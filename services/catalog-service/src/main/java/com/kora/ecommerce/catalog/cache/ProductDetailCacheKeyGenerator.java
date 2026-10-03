package com.kora.ecommerce.catalog.cache;

import java.util.Objects;
import java.util.UUID;

public class ProductDetailCacheKeyGenerator {

    private static final String VERSION = "v1";

    private final CatalogCacheProperties properties;

    public ProductDetailCacheKeyGenerator(CatalogCacheProperties properties) {
        this.properties = properties;
    }

    public String productDetailKey(UUID productId) {
        return prefix() + ":" + VERSION + ":" + Objects.requireNonNull(productId, "productId must not be null");
    }

    public String productDetailLockKey(UUID productId) {
        return prefix() + ":lock:" + VERSION + ":" + Objects.requireNonNull(productId, "productId must not be null");
    }

    private String prefix() {
        String prefix = properties.getKeyPrefix();
        while (prefix.endsWith(":")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        return prefix;
    }
}
