package com.kora.ecommerce.auditnotification.notification;

public record EmailDeliveryDispatchResult(
        int pendingDeliveryCount,
        int attemptedCount,
        int sentCount,
        int failedCount,
        int skippedCount,
        int maxAttemptsExhaustedCount) {

    public EmailDeliveryDispatchResult {
        requireNonNegative(pendingDeliveryCount, "pendingDeliveryCount");
        requireNonNegative(attemptedCount, "attemptedCount");
        requireNonNegative(sentCount, "sentCount");
        requireNonNegative(failedCount, "failedCount");
        requireNonNegative(skippedCount, "skippedCount");
        requireNonNegative(maxAttemptsExhaustedCount, "maxAttemptsExhaustedCount");
    }

    static EmailDeliveryDispatchResult empty() {
        return new EmailDeliveryDispatchResult(0, 0, 0, 0, 0, 0);
    }

    private static void requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
    }
}
