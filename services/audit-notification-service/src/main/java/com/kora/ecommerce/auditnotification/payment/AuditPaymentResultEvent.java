package com.kora.ecommerce.auditnotification.payment;

import java.time.Instant;
import java.util.UUID;

import org.bson.Document;

public record AuditPaymentResultEvent(
        UUID eventId,
        String eventType,
        int eventVersion,
        UUID aggregateId,
        Instant occurredAt,
        String traceId,
        String correlationId,
        UUID orderId,
        UUID paymentId,
        String customerId,
        String paymentStatus,
        Document payload) {

    public AuditPaymentResultEvent {
        payload = new Document(payload);
    }

    @Override
    public Document payload() {
        return new Document(payload);
    }
}
