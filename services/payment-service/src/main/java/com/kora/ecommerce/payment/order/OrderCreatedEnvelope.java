package com.kora.ecommerce.payment.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
record OrderCreatedEnvelope(
        UUID eventId,
        String eventType,
        Integer eventVersion,
        UUID aggregateId,
        Instant occurredAt,
        String traceId,
        String correlationId,
        OrderCreatedData data) {

    static final String SUPPORTED_EVENT_TYPE = "OrderCreated";
    static final int SUPPORTED_EVENT_VERSION = 1;
    static final String SUPPORTED_ORDER_STATUS = "CREATED";

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OrderCreatedData(
            UUID orderId,
            String customerId,
            String status,
            BigDecimal subtotalAmount,
            BigDecimal totalAmount,
            String currency,
            Instant createdAt,
            List<OrderCreatedItemData> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OrderCreatedItemData(
            Integer itemNumber,
            UUID productId,
            String productSku,
            String productName,
            Integer quantity,
            BigDecimal unitPriceAmount,
            String currency,
            BigDecimal lineTotalAmount) {
    }
}
