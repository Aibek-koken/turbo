package com.kora.ecommerce.order.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.kora.ecommerce.order.persistence.OrderStatus;

public record QueriedOrder(
        UUID orderId,
        String customerId,
        OrderStatus status,
        BigDecimal subtotalAmount,
        BigDecimal totalAmount,
        String currency,
        Instant createdAt,
        Instant updatedAt,
        List<Item> items,
        List<StatusHistory> statusHistory) {

    public record Item(
            int itemNumber,
            UUID productId,
            String productSku,
            String productName,
            int quantity,
            BigDecimal unitPriceAmount,
            String currency,
            BigDecimal lineTotalAmount) {
    }

    public record StatusHistory(
            OrderStatus status,
            Instant changedAt,
            String reason) {
    }
}
