package com.exploration.circuitbreaker.advance;

import com.exploration.circuitbreaker.advance.exception.ItemNotFoundException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("circuit-breaker")
public class IgnoreFailureTest {

    @Autowired
    private AdvanceProtectedService protectedService;

    @MockitoBean
    private AdvanceFlakyService flakyService;

    @Autowired
    private CircuitBreakerRegistry registry;

    private CircuitBreaker cbIgnore;

    @BeforeEach
    void setUp() {
        // Reset all circuit breakers and mock class
        registry.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
        reset(flakyService);

        // Get named instances for convenience
        cbIgnore = registry.circuitBreaker("ignore-exception");
    }

    @Test
    @DisplayName("`ignoreExceptions` (ItemNotFoundException) should NOT count as failure")
    void testIgnoreExceptions_KeepsCircuitClosed() {
        // Arrange: Throw the exception that IS in our ignoreExceptions list
        when(flakyService.callWithIgnorableErrors())
                .thenThrow(new ItemNotFoundException("ID 404"));

        // Act: Call 5 times
        for (int i = 0; i < 5; i++) {
            protectedService.callWithIgnoredErrors();
        }

        // Assert: The circuit is still CLOSED because all failures were ignored.
        assertThat(cbIgnore.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("Other exceptions (RuntimeException) should STILL be counted as failures")
    void testOtherException_OpensIgnoredCircuit() {
        // Arrange: Throw an exception that is NOT in our ignoreExceptions list
        when(flakyService.callWithIgnorableErrors())
                .thenThrow(new NullPointerException("NPE")); // This will be recorded!

        // Act: Call 5 times
        for (int i = 0; i < 5; i++) {
            protectedService.callWithIgnoredErrors();
        }

        // Assert: The circuit is OPEN because the NPE was not ignored.
        assertThat(cbIgnore.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

}
