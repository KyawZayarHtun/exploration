package com.exploration.exception;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

import java.net.http.HttpResponse;
import java.util.function.Predicate;

/**
 * Predicates must be public static inner classes or their own public classes
 * so Resilience4J can instantiate them.
 */
public class CustomExceptionPredicates {

    /**
     * This predicate tells the CB to record a failure ONLY IF
     * the exception is a BusinessException with the code "RETRYABLE".
     */
    public static class RecordableFailurePredicate implements Predicate<Throwable> {
        @Override
        public boolean test(Throwable throwable) {
            if (throwable instanceof BusinessException) {
                return "RETRYABLE".equals(((BusinessException) throwable).getErrorCode());
            }
            return false;
        }
    }

    /**
     * This predicate tells the CB to treat a successful result as a failure
     * IF the returned String is "ERROR".
     */
    public static class RecordableResultPredicate implements Predicate<Object> {
        @Override
        public boolean test(Object result) {
            if (result instanceof String) {
                return "ERROR".equals(result);
            }
            return false;
        }
    }

    /*
    * predicate that defines what a "bad" result is
    */
    public static class FiveZeroThreeErrorPredicate implements Predicate<ResponseEntity<?>> {
        @Override
        public boolean test(ResponseEntity<?> entity) {
            return entity.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE;
        }
    }

}
