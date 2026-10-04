package com.kora.ecommerce.order.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.kora.ecommerce.order.application.QueriedOrder;

public record OrderResponse(
        UUID orderId,
        String customerId,
        String status,
        BigDecimal subtotalAmount,
        BigDecimal totalAmount,
        String currency,
        Instant createdAt,
        Instant updatedAt,
        List<ItemResponse> items,
        List<StatusHistoryResponse> statusHistory) {

    static OrderResponse from(QueriedOrder order) {
        return new OrderResponse(
                order.orderId(),
                order.customerId(),
                order.status().name(),
                order.subtotalAmount(),
                order.totalAmount(),
                order.currency(),
                order.createdAt(),
                order.updatedAt(),
                order.items().stream()
                        .map(item -> new ItemResponse(
                                item.itemNumber(),
                                item.productId(),
                                item.productSku(),
                                item.productName(),
                                item.quantity(),
                                item.unitPriceAmount(),
                                item.currency(),
                                item.lineTotalAmount()))
                        .toList(),
                order.statusHistory().stream()
                        .map(history -> new StatusHistoryResponse(
                                history.status().name(),
                                history.changedAt(),
                                history.reason()))
                        .toList());
    }

    public record ItemResponse(
            int itemNumber,
            UUID productId,
            String productSku,
            String productName,
            int quantity,
            BigDecimal unitPriceAmount,
            String currency,
            BigDecimal lineTotalAmount) {
    }

    public record StatusHistoryResponse(
            String status,
            Instant changedAt,
            String reason) {
    }
}
