package com.exploration.circuitbreaker.getStarted;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SpringBootTest
public class ProtectedServiceTest {

    @MockitoBean
    private FlakyService flakyService;

    @Autowired
    private ProtectedService protectedService;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void resetCircuitBreaker() {
        circuitBreakerRegistry.circuitBreaker("backendA").reset();
    }

    @Test
    void testCircuitBreaker_ClosedState_Success() {
        when(flakyService.getData()).thenReturn("Real Data");

        String result = protectedService.callFlakyService();

        Assertions.assertEquals("Real Data", result);

        verify(flakyService, times(1)).getData();
    }

    @Test
    void testCircuitBreaker_Open_and_TriggersFallback() {
        when(flakyService.getData())
                .thenThrow(new RuntimeException("Service Failed!"));

        for (int i = 0; i < 5; i++) {
            String result1= protectedService.callFlakyService();
            assertThat(result1).isEqualTo("Default Data (Fallback)");
        }

        verify(flakyService, times(5)).getData();

        // Act (Phase 2: Test the OPEN state)
        System.out.println("--- Circuit should be OPEN now (Programmatic) ---");
        String resultAfterOpen = protectedService.callFlakyService();

        // Assert (Phase 2)
        assertThat(resultAfterOpen).isEqualTo("Default Data (Fallback)");

        // *** The same key assertion: service was NOT called a 6th time ***
        verify(flakyService, times(5)).getData();
    }

    /*@Test
    void whenFlakyServiceFails_thenFallbackIsCalled() {
        // Given - simulate failure
        doThrow(new RuntimeException("Remote service failed"))
                .when(flakyService)
                .getData();

        // When
        String result = protectedService.callFlakyService();

        // Then
        assertThat(result).isEqualTo("Recovered from remote service");
    }*/

}
