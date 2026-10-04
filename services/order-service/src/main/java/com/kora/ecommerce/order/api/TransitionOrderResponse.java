package com.kora.ecommerce.order.api;

import java.time.Instant;
import java.util.UUID;

import com.kora.ecommerce.order.application.TransitionedOrder;

public record TransitionOrderResponse(
        UUID orderId,
        String previousStatus,
        String currentStatus,
        Instant changedAt,
        String reason,
        long version) {

    static TransitionOrderResponse from(TransitionedOrder order) {
        return new TransitionOrderResponse(
                order.orderId(),
                order.previousStatus().name(),
                order.currentStatus().name(),
                order.changedAt(),
                order.reason(),
                order.version());
    }
}
