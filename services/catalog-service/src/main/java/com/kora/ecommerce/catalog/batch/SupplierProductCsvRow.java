package com.kora.ecommerce.catalog.batch;

public record SupplierProductCsvRow(
        int rowNumber,
        String sku,
        String productName,
        String productDescription,
        String categorySlug,
        String categoryName,
        String categoryDescription,
        String priceAmount,
        String currency,
        String status,
        String attributes) {
}
