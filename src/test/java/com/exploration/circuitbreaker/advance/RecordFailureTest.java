package com.exploration.circuitbreaker.advance;

import com.exploration.exception.BusinessException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.ResourceAccessException;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("circuit-breaker")
public class RecordFailureTest {

    @Autowired
    private AdvanceProtectedService protectedService;

    @MockitoBean
    private AdvanceFlakyService flakyService;

    @Autowired
    private CircuitBreakerRegistry registry;

    private CircuitBreaker cbRecord;

    @BeforeEach
    void setUp() {
        // Reset all circuit breakers and mocks beforatomTopUpResponsee each test
        registry.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
        reset(flakyService);

        // Get named instances for convenience
        cbRecord = registry.circuitBreaker("record-exception");
    }

    @Test
    @DisplayName("`recordExceptions` define unchecked exceptions should be counted as failure")
    void testRecordExceptions_CountsAsFailure() {

        when(flakyService.callWithCustomErrors())
                .thenThrow(new ArrayIndexOutOfBoundsException("RuntimeException"))
                .thenThrow(new NullPointerException("NullPointerException"));

        for (int i = 0; i < 5; i++) {
            protectedService.callWithSpecificErrors();
        }

        assertThat(cbRecord.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("`recordExceptions` undefine unchecked exceptions should be counted as success")
    void testRecordExceptions_CountsAsSuccess() {

        when(flakyService.callWithCustomErrors())
                .thenThrow(new ResourceAccessException("ResourceAccessException"))
                .thenThrow(new NumberFormatException("NumberFormatException"));

        for (int i = 0; i < 5; i++) {
            protectedService.callWithSpecificErrors();
        }

        assertThat(cbRecord.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

    }

    @ParameterizedTest
    @CsvSource({
            "RETRYABLE, true",
            "Err-01, false"
    })
    @DisplayName("`recordFailurePredicate` (BusinessException with only RETRYABLE code) should count as failure")
    void testRecordFailurePredicate_CountsAsFailure(String errorCode, boolean isCountAsFailure) {
        // Arrange: Throw our custom exception with the "RETRYABLE" code
        when(flakyService.callWithCustomErrors())
                .thenThrow(new BusinessException("Error B-123", errorCode));

        // Act: Call 5 times
        for (int i = 0; i < 5; i++) {
            protectedService.callWithSpecificErrors();
        }

        // Assert: The predicate matched, so the circuit is OPEN
        assertThat(cbRecord.getState()).isEqualTo(isCountAsFailure ? CircuitBreaker.State.OPEN : CircuitBreaker.State.CLOSED);

    }

    @Test
    @DisplayName("`recordResultPredicate` (String='ERROR') should count as failure")
    void testRecordResultPredicate_CountsAsFailure() {
        // Arrange: Return the string "ERROR" (which is not an exception)
        when(flakyService.callWithCustomErrors()).thenReturn("ERROR");

        // Act: Call 5 times
        for (int i = 0; i < 5; i++) {
            String result = protectedService.callWithSpecificErrors();
            // The predicate sees "ERROR", counts it as a failure,
            // but won't be trigger the fallback.
            assertThat(result).isEqualTo("ERROR");
        }

        // Assert: The predicate matched, so the circuit is OPEN
        assertThat(cbRecord.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }


}
