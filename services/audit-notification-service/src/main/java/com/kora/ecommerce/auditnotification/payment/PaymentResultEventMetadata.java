package com.kora.ecommerce.auditnotification.payment;

public record PaymentResultEventMetadata(
        String eventId,
        String eventType,
        Integer eventVersion,
        String aggregateId) {

    public static PaymentResultEventMetadata empty() {
        return new PaymentResultEventMetadata(null, null, null, null);
    }
}
