package com.exploration.circuitbreaker.core;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("circuit-breaker")
public class CountBaseTest {

    @MockitoBean
    private FlakyService flakyService;

    @Autowired
    private ProtectedService protectedService;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void resetCircuitBreaker() {
        reset(flakyService);
        circuitBreaker = circuitBreakerRegistry.circuitBreaker("count-base");
        circuitBreaker.reset();
    }

    @Test
    void testCircuitBreaker_ClosedState_Success() {
        when(flakyService.getData()).thenReturn("Real Data");

        String result = protectedService.callFlakyService("count-base");

        Assertions.assertEquals("Real Data", result);

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        verify(flakyService, times(1)).getData();
    }

    @Test
    void testCircuitBreaker_Closed_to_Open() {
        // arrange
        when(flakyService.getData())
                .thenThrow(new RuntimeException("Service Failed!"));

        // act
        for (int i = 0; i < 5; i++) {
            String result1= protectedService.callFlakyService("count-base");
            assertThat(result1).isEqualTo("Default Data (Fallback)");
        }

        // assert
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // prove
        verify(flakyService, times(5)).getData();

        // Act (Phase 2: Test the OPEN state)
        System.out.println("--- Circuit should be OPEN now (Programmatic) ---");
        String resultAfterOpen = protectedService.callFlakyService("count-base");

        // Assert (Phase 2)
        assertThat(resultAfterOpen).isEqualTo("Default Data (Fallback)");

        // *** The same key assertion: service was NOT called a 6th time ***
        verify(flakyService, times(5)).getData();
    }

    @Test
    void testCircuitBreaker_HalfOpen_to_Closed() {
        // --- Arrange ---
        // 1. Force the circuit to OPEN
        circuitBreaker.transitionToOpenState();

        // 2. Force it to HALF-OPEN (bypassing the 1s wait)
        circuitBreaker.transitionToHalfOpenState();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

        // 3. Tell the mock to SUCCEED on this one call
        when(flakyService.getData()).thenReturn("Success!");

        // --- Act ---
        // 4. Make the one permitted "test call"
        String result = protectedService.callFlakyService("count-base");

        // --- Assert ---
        // 5. The call should have succeeded
        assertThat(result).isEqualTo("Success!");

        // 6. The circuit should now be CLOSED! (This is the magic)
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        // 7. Prove it: Call again, it should still succeed
        String nextResult = protectedService.callFlakyService("count-base");
        assertThat(nextResult).isEqualTo("Success!");

        // 8. The mock was called twice (once in HALF-OPEN, once in CLOSED)
        verify(flakyService, times(2)).getData();
    }

    // --- NEW TEST 2: HALF-OPEN back to OPEN ---

    @Test
    void testCircuitBreaker_HalfOpen_to_Open() {
        // --- Arrange ---
        // 1. Force the circuit to OPEN, then HALF-OPEN
        circuitBreaker.transitionToOpenState();
        circuitBreaker.transitionToHalfOpenState();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

        // 2. Tell the mock to FAIL on the test call
        when(flakyService.getData()).thenThrow(new RuntimeException("Failed again!"));

        // --- Act ---
        // 3. Make the one permitted "test call"
        String result = protectedService.callFlakyService("count-base");

        // --- Assert ---
        // 4. The call should fail and return the fallback
        assertThat(result).isEqualTo("Default Data (Fallback)");

        // 5. The circuit should immediately go *back* to OPEN
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // 6. Prove it: Call again, it should be short-circuited
        String nextResult = protectedService.callFlakyService("count-base");
        assertThat(nextResult).isEqualTo("Default Data (Fallback)");

        // 7. The mock was only called ONCE (the failed half-open attempt)
        // The second call was short-circuited by the now-OPEN circuit.
        verify(flakyService, times(1)).getData();
    }

}
