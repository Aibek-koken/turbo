package com.kora.ecommerce.order.application.payment;

import java.time.Instant;
import java.util.UUID;

import com.kora.ecommerce.order.persistence.OrderStatus;

public record PaymentSucceededEnvelope(
        UUID eventId,
        int eventVersion,
        UUID aggregateId,
        Instant occurredAt,
        String traceId,
        String correlationId,
        PaymentResultData data) implements PaymentResultEnvelope {

    public static final String EVENT_TYPE = "PaymentSucceeded";

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    @Override
    public OrderStatus targetStatus() {
        return OrderStatus.PAID;
    }
}
