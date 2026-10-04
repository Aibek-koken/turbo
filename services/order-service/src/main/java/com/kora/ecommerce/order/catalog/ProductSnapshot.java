package com.kora.ecommerce.order.catalog;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record ProductSnapshot(
        UUID productId,
        String sku,
        String name,
        BigDecimal unitPriceAmount,
        String currency) {

    public ProductSnapshot {
        Objects.requireNonNull(productId, "productId is required");
        Objects.requireNonNull(sku, "sku is required");
        Objects.requireNonNull(name, "name is required");
        Objects.requireNonNull(unitPriceAmount, "unitPriceAmount is required");
        Objects.requireNonNull(currency, "currency is required");
    }
}
