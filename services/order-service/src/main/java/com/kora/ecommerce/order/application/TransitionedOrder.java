package com.kora.ecommerce.order.application;

import java.time.Instant;
import java.util.UUID;

import com.kora.ecommerce.order.persistence.OrderStatus;

public record TransitionedOrder(
        UUID orderId,
        OrderStatus previousStatus,
        OrderStatus currentStatus,
        Instant changedAt,
        String reason,
        long version) {
}
