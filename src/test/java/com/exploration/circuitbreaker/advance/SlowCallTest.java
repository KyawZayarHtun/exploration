package com.exploration.circuitbreaker.advance;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@Slf4j
@SpringBootTest
@ActiveProfiles("circuit-breaker")
public class SlowCallTest {

    @Autowired
    private AdvanceProtectedService protectedService;

    @MockitoBean
    private AdvanceFlakyService flakyService;

    @Autowired
    private CircuitBreakerRegistry registry;

    private CircuitBreaker cbSlow;

    @BeforeEach
    void setUp() {
        // Reset all circuit breakers and mocks before each test
        registry.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
        reset(flakyService);

        // Get named instances for convenience
        cbSlow = registry.circuitBreaker("slow-call");
    }

    // --- Scenario 1: Slow Call Thresholds ---

    @Test
    @DisplayName("Slow Calls (over 100ms) should count as failures and open the circuit")
    void testSlowCalls_OpenCircuit() {
        // Arrange: Mock a call that takes 150ms (threshold is 100ms)
        when(flakyService.callSlow()).thenAnswer(invocation -> {
            Thread.sleep(Duration.ofMillis(150));
            return "slow-data";
        });

        // Act: Call 4 times (our minimumNumberOfCalls)
        for (int i = 0; i < 5; i++) {
            protectedService.callSlowService();
        }

        // Assert: All 4 calls were slow (100% slow rate > 50% threshold)
        // The circuit should be OPEN
        assertThat(cbSlow.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("Fast Calls (under 100ms) should NOT count as failures")
    void testFastCalls_KeepCircuitClosed() {
        // Arrange: Mock a call that takes 50ms (threshold is 100s)
        when(flakyService.callSlow()).thenAnswer(invocation -> {
            Thread.sleep(Duration.ofMillis(50));
            return "fast-data";
        });

        // Act: Call 4 times
        for (int i = 0; i < 4; i++) {
            protectedService.callSlowService();
        }

        // Assert: 0% slow call rate. The circuit is still CLOSED.
        assertThat(cbSlow.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }



}
