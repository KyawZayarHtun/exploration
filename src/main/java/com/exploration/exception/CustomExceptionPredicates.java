package com.exploration.exception;

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

}
