package com.kora.ecommerce.auditnotification.notification;

public class PushNotificationDeliveryException extends RuntimeException {

    private final String safeFailureDetail;

    public PushNotificationDeliveryException(String safeFailureDetail) {
        super(requireText(safeFailureDetail));
        this.safeFailureDetail = requireText(safeFailureDetail);
    }

    public String safeFailureDetail() {
        return safeFailureDetail;
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("safeFailureDetail must not be blank");
        }
        return value.trim();
    }
}
