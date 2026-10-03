package com.kora.ecommerce.catalog.cache;

import java.util.Optional;
import java.util.UUID;

import com.kora.ecommerce.catalog.api.customer.CatalogBrowseDtos.ProductDetailResponse;

enum NoopProductDetailCache implements ProductDetailCache {

    INSTANCE;

    @Override
    public Optional<ProductDetailResponse> get(UUID productId) {
        return Optional.empty();
    }

    @Override
    public void put(UUID productId, ProductDetailResponse response) {
    }

    @Override
    public void evict(UUID productId) {
    }
}
