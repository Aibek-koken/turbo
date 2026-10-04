package com.kora.ecommerce.order.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

record OrderCreationDraft(
        UUID orderId,
        String customerId,
        BigDecimal subtotalAmount,
        BigDecimal totalAmount,
        String currency,
        Instant createdAt,
        List<OrderCreationDraftItem> items) {
}
