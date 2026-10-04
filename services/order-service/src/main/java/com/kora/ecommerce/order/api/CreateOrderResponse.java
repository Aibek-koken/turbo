package com.kora.ecommerce.order.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.kora.ecommerce.order.application.CreatedOrder;

public record CreateOrderResponse(
        UUID orderId,
        String customerId,
        String status,
        BigDecimal subtotalAmount,
        BigDecimal totalAmount,
        String currency,
        Instant createdAt,
        List<CreateOrderItemResponse> items) {

    static CreateOrderResponse from(CreatedOrder order) {
        return new CreateOrderResponse(
                order.orderId(),
                order.customerId(),
                order.status().name(),
                order.subtotalAmount(),
                order.totalAmount(),
                order.currency(),
                order.createdAt(),
                order.items().stream()
                        .map(item -> new CreateOrderItemResponse(
                                item.itemNumber(),
                                item.productId(),
                                item.productSku(),
                                item.productName(),
                                item.quantity(),
                                item.unitPriceAmount(),
                                item.currency(),
                                item.lineTotalAmount()))
                        .toList());
    }

    public record CreateOrderItemResponse(
            int itemNumber,
            UUID productId,
            String productSku,
            String productName,
            int quantity,
            BigDecimal unitPriceAmount,
            String currency,
            BigDecimal lineTotalAmount) {
    }
}
