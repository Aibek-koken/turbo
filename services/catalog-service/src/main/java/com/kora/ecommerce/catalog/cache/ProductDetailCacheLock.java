package com.kora.ecommerce.catalog.cache;

import java.util.UUID;
import java.util.function.Supplier;

public interface ProductDetailCacheLock {

    <T> T withProductDetailLock(UUID productId, Supplier<T> operation);
}
