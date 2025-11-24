package com.exploration.circuitbreaker.advance;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdvanceProtectedService {

    private final AdvanceFlakyService advanceFlakyService;

    @CircuitBreaker(name = "slow-call", fallbackMethod = "recover")
    public String callSlowService() {
        return advanceFlakyService.callSlow();
    }

    @CircuitBreaker(name = "record-exception", fallbackMethod = "recover")
    public String callWithSpecificErrors() {
        return advanceFlakyService.callWithCustomErrors();
    }

    @CircuitBreaker(name = "ignore-exception", fallbackMethod = "recover")
    public String callWithIgnoredErrors() {
        return advanceFlakyService.callWithIgnorableErrors();
    }

    public String recover(Throwable throwable) {
        log.warn("Fallback triggered: {}", throwable.getMessage());
        return "Default Data (Fallback)";
    }


}
