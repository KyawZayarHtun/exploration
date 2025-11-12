package com.exploration.circuitbreaker.getStarted;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProtectedService {

    private final FlakyService flakyService;
    private final CircuitBreaker circuitBreaker;

    @Autowired
    public ProtectedService(FlakyService flakyService, CircuitBreakerFactory<?, ?> circuitBreakerFactory) {
        this.flakyService = flakyService;
        this.circuitBreaker = circuitBreakerFactory.create("backendA");
    }

    public String callFlakyService() {
        return circuitBreaker.run(
                flakyService::getData,
                this::recover
        );
    }

    public String recover(Throwable throwable) {
        return "Default Data (Fallback)";
    }


}
