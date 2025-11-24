package com.exploration.circuitbreaker.core;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProtectedService {

    private final FlakyService flakyService;

    @CircuitBreaker(name = "#cbInstanceName", fallbackMethod = "recover")
    public String callFlakyService(String cbInstanceName) {
        return flakyService.getData();
    }

    public String recover(Throwable throwable) {
        return "Default Data (Fallback)";
    }


}
