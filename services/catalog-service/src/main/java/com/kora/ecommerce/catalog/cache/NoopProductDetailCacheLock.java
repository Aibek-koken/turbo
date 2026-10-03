package com.kora.ecommerce.catalog.cache;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

enum NoopProductDetailCacheLock implements ProductDetailCacheLock {

    INSTANCE;

    @Override
    public <T> T withProductDetailLock(UUID productId, Supplier<T> operation) {
        Objects.requireNonNull(productId, "productId must not be null");
        return Objects.requireNonNull(operation, "operation must not be null").get();
    }
}
