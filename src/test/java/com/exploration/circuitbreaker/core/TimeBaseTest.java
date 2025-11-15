package com.exploration.circuitbreaker.core;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("cb-time-base")
public class TimeBaseTest {

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
        circuitBreaker = circuitBreakerRegistry.circuitBreaker("backendA");
        circuitBreaker.reset();
    }

    @Test
    void testCircuitBreaker_Closed_to_Closed() {
        // Arrange:
        // Our config (application-test.yml) needs 3 calls with a 50% failure rate
        // in a 2-second window. We'll make 3 calls, 2 of which fail.
        when(flakyService.getData())
                .thenThrow(new RuntimeException("Service Failed!")) // Call 1: Fails
                .thenThrow(new RuntimeException("Service Failed!")) // Call 3: Fails
                .thenReturn("Real Data");                        // Call 2: Succeeds

        // Assert initial state is CLOSED
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        // Act: Call the service 3 times. All calls will be made (and fallbacks triggered)
        // because the test will run within the 2-second sliding window.
        System.out.println("Calling service (Call 1)...");
        protectedService.callFlakyService(); // Fails, fallback
        System.out.println("Calling service (Call 2)...");
        protectedService.callFlakyService(); // Fails, fallback

        // We will wait to pass sliding window size
        try {
            Thread.sleep(Duration.ofSeconds(3));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        System.out.println("Calling service (Call 3)...");
        protectedService.callFlakyService(); // Succeeds

        // Assert:
        // After 3 calls (minimumNumberOfCalls), the failure rate is 1/3 because we wait 3 sec for reset.
        // This is < our 50% threshold, so the circuit MUST transition to CLOSED.
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        // Act 2: Call one more time
        String result = protectedService.callFlakyService();

        // Assert 2:
        // The circuit is CLOSED, so the real data is returned.
        assertThat(result).isEqualTo("Real Data");

        // Verify flakyService was called 4 times.
        verify(flakyService, times(4)).getData();
    }

    @Test
    void testCircuitBreaker_Closed_to_Open() {
        // Arrange:
        // Our config (application-test.yml) needs 3 calls with a 50% failure rate
        // in a 2-second window. We'll make 3 calls, 2 of which fail.
        when(flakyService.getData())
                .thenThrow(new RuntimeException("Service Failed!")) // Call 1: Fails
                .thenReturn("Real Data")                           // Call 2: Succeeds
                .thenThrow(new RuntimeException("Service Failed!")); // Call 3: Fails

        // Assert initial state is CLOSED
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        // Act: Call the service 3 times. All calls will be made (and fallbacks triggered)
        // because the test will run within the 2-second sliding window.
        System.out.println("Calling service (Call 1)...");
        protectedService.callFlakyService(); // Fails, fallback
        System.out.println("Calling service (Call 2)...");
        protectedService.callFlakyService(); // Succeeds
        System.out.println("Calling service (Call 3)...");
        protectedService.callFlakyService(); // Fails, fallback

        // Assert:
        // After 3 calls (minimumNumberOfCalls), the failure rate is 2/3 (66.6%).
        // This is > our 50% threshold, so the circuit MUST transition to OPEN.
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // Act 2: Call one more time
        String result = protectedService.callFlakyService();

        // Assert 2:
        // The circuit is OPEN, so the fallback is returned *without* calling the service.
        assertThat(result).isEqualTo("Default Data (Fallback)");

        // Verify flakyService was only called 3 times. The 4th call was blocked.
        verify(flakyService, times(3)).getData();
    }

    @Test
    void testCircuitBreaker_HalfOpen_to_Closed() {
        // Arrange:
        // Instead of waiting 10s (waitDurationInOpenState), we manually force
        // 1. Force the circuit to OPEN
        circuitBreaker.transitionToOpenState();
        // 2. Force the circuit into the HALF_OPEN state. This is the power of the Registry.
        circuitBreaker.transitionToHalfOpenState();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

        // Arrange 2:
        // Our config allows 2 calls in HALF_OPEN. We will make both succeed.
        when(flakyService.getData()).thenReturn("Real Data");

        // Act:
        // Call the service twice (our permittedNumberOfCallsInHalfOpenState)
        String result1 = protectedService.callFlakyService();
        String result2 = protectedService.callFlakyService();

        // Assert:
        // Both calls succeeded, so the circuit should transition back to CLOSED.
        assertThat(result1).isEqualTo("Real Data");
        assertThat(result2).isEqualTo("Real Data");
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        // Verify the service was called twice.
        verify(flakyService, times(2)).getData();
    }

    @Test
    void testCircuitBreaker_HalfOpen_to_Open() {
        // Arrange:
        // 1. Force the circuit to OPEN
        circuitBreaker.transitionToOpenState();
        // 2. Force the circuit into the HALF_OPEN state.
        circuitBreaker.transitionToHalfOpenState();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

        // Arrange 2:
        // Configure the service to FAIL on its first call in the HALF_OPEN state.
        when(flakyService.getData())
                .thenThrow(new RuntimeException("Service Failed!"))
                .thenReturn("Real Data");


        // Act:
        // Make the first permitted call. It fails.
        // The CB records the failure but is still HALF_OPEN, waiting for call 2.
        String result1 = protectedService.callFlakyService();
        assertThat(result1).isEqualTo("Default Data (Fallback)");
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

        // Act 2:
        // Make the second permitted call. It also fails.
        String result2 = protectedService.callFlakyService();
        assertThat(result2).isEqualTo("Real Data");


        // Assert:
        // Now that all 2 permitted calls have been made, and at least one
        // (in this case, both) has failed, the circuit snaps back to OPEN.
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // Verify the service was called twice (the number of permitted calls).
        verify(flakyService, times(2)).getData();
    }

}
