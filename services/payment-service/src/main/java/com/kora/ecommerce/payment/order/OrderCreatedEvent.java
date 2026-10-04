package com.kora.ecommerce.payment.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderCreatedEvent(
        UUID eventId,
        int eventVersion,
        UUID aggregateId,
        Instant occurredAt,
        String traceId,
        String correlationId,
        UUID orderId,
        String customerId,
        BigDecimal totalAmount,
        String currency) {
}
