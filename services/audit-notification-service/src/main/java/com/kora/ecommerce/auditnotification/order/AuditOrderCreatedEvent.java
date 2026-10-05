package com.kora.ecommerce.auditnotification.order;

import java.time.Instant;
import java.util.UUID;

import org.bson.Document;

public record AuditOrderCreatedEvent(
        UUID eventId,
        String eventType,
        int eventVersion,
        UUID aggregateId,
        Instant occurredAt,
        String traceId,
        String correlationId,
        UUID orderId,
        String customerId,
        Document payload) {

    public AuditOrderCreatedEvent {
        payload = new Document(payload);
    }

    @Override
    public Document payload() {
        return new Document(payload);
    }
}
