package com.kora.ecommerce.auditnotification.payment;

public class InvalidPaymentResultEventException extends RuntimeException {

    private final String reason;
    private final PaymentResultEventMetadata metadata;

    public InvalidPaymentResultEventException(String reason, PaymentResultEventMetadata metadata) {
        super("Invalid payment result audit event: " + reason);
        this.reason = reason;
        this.metadata = metadata == null ? PaymentResultEventMetadata.empty() : metadata;
    }

    public InvalidPaymentResultEventException(
            String reason,
            PaymentResultEventMetadata metadata,
            Throwable cause) {
        super("Invalid payment result audit event: " + reason, cause);
        this.reason = reason;
        this.metadata = metadata == null ? PaymentResultEventMetadata.empty() : metadata;
    }

    public String reason() {
        return reason;
    }

    public PaymentResultEventMetadata metadata() {
        return metadata;
    }
}
