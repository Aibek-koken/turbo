package com.kora.ecommerce.auditnotification.notification;

import java.time.Instant;
import java.util.Objects;

import com.kora.ecommerce.auditnotification.order.AuditOrderCreatedEvent;
import com.kora.ecommerce.auditnotification.payment.AuditPaymentResultEvent;

public record NotificationRouteRequest(
        String eventId,
        String eventType,
        int eventVersion,
        String orderId,
        String customerId,
        String paymentId,
        Instant occurredAt,
        String traceId,
        String correlationId) {

    public NotificationRouteRequest {
        eventId = requireText(eventId, "eventId");
        eventType = requireText(eventType, "eventType");
        if (eventVersion < 1) {
            throw new IllegalArgumentException("eventVersion must be positive");
        }
        orderId = requireText(orderId, "orderId");
        customerId = requireText(customerId, "customerId");
        paymentId = optionalText(paymentId);
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        traceId = requireText(traceId, "traceId");
        correlationId = requireText(correlationId, "correlationId");
    }

    public static NotificationRouteRequest from(AuditOrderCreatedEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        return new NotificationRouteRequest(
                event.eventId().toString(),
                event.eventType(),
                event.eventVersion(),
                event.orderId().toString(),
                event.customerId(),
                null,
                event.occurredAt(),
                event.traceId(),
                event.correlationId());
    }

    public static NotificationRouteRequest from(AuditPaymentResultEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        return new NotificationRouteRequest(
                event.eventId().toString(),
                event.eventType(),
                event.eventVersion(),
                event.orderId().toString(),
                event.customerId(),
                event.paymentId().toString(),
                event.occurredAt(),
                event.traceId(),
                event.correlationId());
    }

    private static String requireText(String value, String fieldName) {
        String normalized = optionalText(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

    private static String optionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
