package com.exploration.retry;

import com.exploration.exception.CustomExceptionPredicates;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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

    public ResponseEntity<String> get503ResponseEntity(boolean failAfterMaxAttempts) {

        RetryConfig retryConfig = RetryConfig.<ResponseEntity<?>>custom()
                .maxAttempts(3)
                .failAfterMaxAttempts(failAfterMaxAttempts)
                .retryOnResult(new CustomExceptionPredicates.FiveZeroThreeErrorPredicate())
                .build();

        RetryRegistry retryRegistry = RetryRegistry.of(retryConfig);

        return retryRegistry.retry("fail-max-attempts-test").executeSupplier(goofyService::get503RestException);
    }

}
