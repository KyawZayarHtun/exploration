package com.exploration.retry;

import io.github.resilience4j.retry.MaxRetriesExceededException;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Slf4j
@SpringBootTest
@ActiveProfiles("retry")
public class CoreFeatureTest {

    @MockitoBean
    private GoofyService goofyService;

    @Autowired
    private RetryableService retryableService;

    @Autowired
    private RetryRegistry retryRegistry;

    private Retry retry;

    @BeforeEach
    public void setUp() {
        reset(goofyService);
        retry = retryRegistry.retry("core-test");
    }

    @Test
    @DisplayName("Retry for 3 times when throw an error")
    void Retry__ThreeTime() {
        when(goofyService.getData())
                .thenThrow(new RuntimeException());

        String result = retryableService.callGoofyService("default");

        assertEquals("Default Data (Fallback)", result);

        verify(goofyService, times(3)).getData();
    }

    @Test
    @DisplayName("Retry with 500ms interval")
    void Retry__Interval() {

        when(goofyService.getData())
                .thenThrow(new RuntimeException());

        long start = System.currentTimeMillis();

        String result = retryableService.callGoofyService("default");

        long end = System.currentTimeMillis();

        long interval = end - start;

        assertTrue(interval >= 1000);

        assertEquals("Default Data (Fallback)", result);

        verify(goofyService, times(3)).getData();

    }

    @Test
    @DisplayName("Retry with 500ms interval and exponential-backoff-multiplier = 2.0 with exponential-max-wait-duration: 6s")
    void Retry__Interval__Exponential_Backoff_Multiplier() {

        when(goofyService.getData())
                .thenAnswer(invocationOnMock -> {
                    log.info("Calling mock");
                    throw new RuntimeException();
                });

        long start = System.currentTimeMillis();

        // 1. 2s
        // 2. 4s
        // 3. 8s but only 6s (max wait is 6s)
        // 4. 12s but only 6s
        String result = retryableService.callGoofyService("exponential-test");

        long end = System.currentTimeMillis();

        long interval = end - start;

        log.info("interval = {}", interval);

        assertTrue(interval >= 18000);

        assertEquals("Default Data (Fallback)", result);

        verify(goofyService, times(5)).getData();

    }

    @RepeatedTest(5)
    @DisplayName("Retry jitter with randomize wait factor")
    void Retry__Interval__Randomize_Wait_factor() {

        when(goofyService.getData())
                .thenAnswer(invocationOnMock -> {
                    log.info("Calling mock");
                    throw new RuntimeException();
                });

        long start = System.currentTimeMillis();

        String result = retryableService.callGoofyService("basic-randomized-wait-test");

        long duration = System.currentTimeMillis() - start;

        log.info("duration = {}", duration);

        /*
         * 500ms <=> 1500mx
         * since max-attempts is 3, retry twice.
         * so 1000 <=> 3000
         * */

        assertTrue(duration >= 1000, "Should be >= 1000");
        assertTrue(duration <= 3000, "Should be <= 3000");


        assertEquals("Default Data (Fallback)", result);

        verify(goofyService, times(3)).getData();

    }

    // this is not a test  between 1 -> 2 interval and 2 -> 3 interval for both
    @RepeatedTest(5)
    @DisplayName("Retry jitter with randomize wait factor and check interval between 1st retry and 2nd retry")
    void Retry__Between_Interval__Randomize_Wait_factor() {

        Retry retry = retryRegistry.retry("basic-randomized-wait-test");
        List<Long> retryTimestamps = Collections.synchronizedList(new ArrayList<>());

        retry.getEventPublisher().onRetry(event -> {
            // Record timestamp when Retry event fires (at retry attempt invocation)
            retryTimestamps.add(System.currentTimeMillis());
        });


        when(goofyService.getData()).thenAnswer(invocationOnMock -> {
            log.info("Inject Mock");
            throw new RuntimeException();
        });

        assertThrows(RuntimeException.class, () -> retryableService.callGoofyServiceWithoutFallback("basic-randomized-wait-test"));

        // We expect two retry events (because maxAttempts = 3 => 2 retries)
        // But events are recorded when a retry attempt is made. For 2 retries, timestamps.size() == 2.
        assertEquals(2, retryTimestamps.size(), "Expected exactly 2 retry events");

        // Compute intervals between retry events.
        // interval[0] is time between retry event 1 and retry event 2 -> this equals the wait between retry attempts.
        long intervalBetweenRetries = retryTimestamps.get(1) - retryTimestamps.get(0);

        // Each wait should be in range [500ms, 1500ms].
        long expectedMin = 500L;
        long expectedMax = 1500L;

        // Add a small tolerance to avoid flakes due to scheduling/clock granularity.
        long tolLower = 80L;
        long tolUpper = 250L;

        assertTrue(intervalBetweenRetries + tolLower >= expectedMin,
                "Interval too short: " + intervalBetweenRetries);
        assertTrue(intervalBetweenRetries - tolUpper <= expectedMax,
                "Interval too long: " + intervalBetweenRetries);

        verify(goofyService, times(3)).getData();

    }

    @RepeatedTest(5)
    @DisplayName("Retry jitter with randomize wait factor and exponential delay manual check between log")
    void Retry__Between_Interval__Randomize_Wait_factor_with_Exponential() {

        when(goofyService.getData()).thenAnswer(invocationOnMock -> {
            log.info("Injecting Mock");
            throw new RuntimeException();
        });

        // 1. 2s
        // 2. 4s
        // 3. 8s but only 6s (max wait is 6s)
        // 4. 12s but only 6s
        assertThrows(RuntimeException.class, () -> retryableService.callGoofyServiceWithoutFallback("advance-randomized-wait-test"));

        verify(goofyService, times(5)).getData();

    }

    @ParameterizedTest
    @CsvSource({
            "true",
            "false"
    })
    @DisplayName("Fail After Max Attempt")
    void Fail_After_Max_Attempt(boolean failAfterMaxAttempt) {

        when(goofyService.get503RestException()).thenReturn(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("{\"status\":503,\"title\":\"Service Unavailable\",\"type\":\"about:blank\",\"detail\":\"Service Unavailable\",\"message\":\"Service Unavailable\"}"));

        if (failAfterMaxAttempt) {
            assertThrows(MaxRetriesExceededException.class, () -> retryableService.get503ResponseEntity(true));
        } else {
            ResponseEntity<String> retryableService503ResponseEntity = retryableService.get503ResponseEntity(false);
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, retryableService503ResponseEntity.getStatusCode());
        }
    }


}
