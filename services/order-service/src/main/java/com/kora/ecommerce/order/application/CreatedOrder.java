package com.kora.ecommerce.order.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.kora.ecommerce.order.persistence.OrderStatus;

public record CreatedOrder(
        UUID orderId,
        String customerId,
        OrderStatus status,
        BigDecimal subtotalAmount,
        BigDecimal totalAmount,
        String currency,
        Instant createdAt,
        List<CreatedOrderItem> items) {
}
