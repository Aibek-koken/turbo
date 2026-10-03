package com.kora.ecommerce.catalog.batch;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.kora.ecommerce.catalog.domain.ProductStatus;

public record SupplierProductImportCommand(
        String sku,
        String productName,
        String productDescription,
        String categorySlug,
        String categoryName,
        String categoryDescription,
        BigDecimal priceAmount,
        String currency,
        ProductStatus status,
        Map<String, String> attributes) {

    public SupplierProductImportCommand {
        attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }
}
