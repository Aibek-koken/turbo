package com.kora.ecommerce.catalog.cache;

import java.util.Optional;
import java.util.UUID;

import com.kora.ecommerce.catalog.api.customer.CatalogBrowseDtos.ProductDetailResponse;

final class MeteredProductDetailCache implements ProductDetailCache {

    private final ProductDetailCache delegate;
    private final CatalogCacheMetrics metrics;

    MeteredProductDetailCache(ProductDetailCache delegate, CatalogCacheMetrics metrics) {
        this.delegate = delegate;
        this.metrics = metrics;
    }

    @Override
    public Optional<ProductDetailResponse> get(UUID productId) {
        Optional<ProductDetailResponse> result = delegate.get(productId);
        if (result.isPresent()) {
            metrics.recordProductDetailHit();
        } else {
            metrics.recordProductDetailMiss();
        }
        return result;
    }

    @Override
    public void put(UUID productId, ProductDetailResponse response) {
        delegate.put(productId, response);
    }

    @Override
    public void evict(UUID productId) {
        delegate.evict(productId);
    }
}
