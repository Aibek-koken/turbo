package com.kora.ecommerce.order.application;

import java.math.BigDecimal;
import java.util.UUID;

record OrderCreationDraftItem(
        int itemNumber,
        UUID productId,
        String productSku,
        String productName,
        int quantity,
        BigDecimal unitPriceAmount,
        String currency,
        BigDecimal lineTotalAmount) {
}
