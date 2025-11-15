package com.exploration.circuitbreaker.core;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProtectedService {

    private final FlakyService flakyService;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;

    public String callFlakyService(String cbInstanceName) {
        return circuitBreakerFactory.create(cbInstanceName).run(
                flakyService::getData,
                this::recover
        );
    }

    public String recover(Throwable throwable) {
        return "Default Data (Fallback)";
    }


}
