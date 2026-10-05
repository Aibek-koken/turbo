package com.kora.ecommerce.auditnotification.notification;

public record PushDeliveryDispatchResult(
        int pendingDeliveryCount,
        int attemptedCount,
        int sentCount,
        int failedCount,
        int skippedCount,
        int maxAttemptsExhaustedCount) {

    public PushDeliveryDispatchResult {
        requireNonNegative(pendingDeliveryCount, "pendingDeliveryCount");
        requireNonNegative(attemptedCount, "attemptedCount");
        requireNonNegative(sentCount, "sentCount");
        requireNonNegative(failedCount, "failedCount");
        requireNonNegative(skippedCount, "skippedCount");
        requireNonNegative(maxAttemptsExhaustedCount, "maxAttemptsExhaustedCount");
    }

    static PushDeliveryDispatchResult empty() {
        return new PushDeliveryDispatchResult(0, 0, 0, 0, 0, 0);
    }

    private static void requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
    }
}
