package com.kora.ecommerce.catalog.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class ProductDetailCacheKeyGeneratorTest {

    @Test
    void generatesStableNamespacedProductDetailAndLockKeys() {
        CatalogCacheProperties properties = new CatalogCacheProperties();
        properties.setKeyPrefix("catalog:product-detail:");
        ProductDetailCacheKeyGenerator keyGenerator = new ProductDetailCacheKeyGenerator(properties);
        UUID productId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        assertThat(keyGenerator.productDetailKey(productId))
                .isEqualTo("catalog:product-detail:v1:11111111-1111-1111-1111-111111111111");
        assertThat(keyGenerator.productDetailLockKey(productId))
                .isEqualTo("catalog:product-detail:lock:v1:11111111-1111-1111-1111-111111111111");
    }
}
