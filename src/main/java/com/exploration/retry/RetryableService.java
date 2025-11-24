package com.exploration.retry;

import io.github.resilience4j.core.IntervalBiFunction;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RetryableService {

    private final GoofyService goofyService;

    @Retry(name = "#retryInstanceName", fallbackMethod = "recover")
    public String callGoofyService(String retryInstanceName) {
        return goofyService.getData();
    }

    @Retry(name = "#retryInstanceName")
    public String callGoofyServiceWithoutFallback(String retryInstanceName) {
        return goofyService.getData();
    }

    public String recover(Throwable throwable) {
        return "Default Data (Fallback)";
    }

}
