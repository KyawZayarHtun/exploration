package com.exploration.circuitbreaker.advance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdvanceProtectedService {

    private final AdvanceFlakyService advanceFlakyService;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;

    public String callSlowService() {
        return circuitBreakerFactory.create("slow-call").run(
                advanceFlakyService::callSlow,
                this::recover
        );
    }

    public String callWithSpecificErrors() {
        return circuitBreakerFactory.create("backendRecord").run(
                advanceFlakyService::callWithCustomErrors,
                this::recover
        );
    }

    public String callWithIgnoredErrors() {
        return circuitBreakerFactory.create("backendIgnore").run(
                advanceFlakyService::callWithIgnorableErrors,
                this::recover
        );    }

    public String recover(Throwable throwable) {
        log.warn("Fallback triggered: {}", throwable.getMessage());
        return "Default Data (Fallback)";
    }


}
