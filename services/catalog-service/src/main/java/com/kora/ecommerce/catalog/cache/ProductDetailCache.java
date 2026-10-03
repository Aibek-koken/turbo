package com.kora.ecommerce.catalog.cache;

import java.util.Optional;
import java.util.UUID;

import com.kora.ecommerce.catalog.api.customer.CatalogBrowseDtos.ProductDetailResponse;

public interface ProductDetailCache {

    Optional<ProductDetailResponse> get(UUID productId);

    void put(UUID productId, ProductDetailResponse response);

    void evict(UUID productId);
}
