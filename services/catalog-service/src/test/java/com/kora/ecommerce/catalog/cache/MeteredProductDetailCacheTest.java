package com.kora.ecommerce.catalog.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kora.ecommerce.catalog.api.customer.CatalogBrowseDtos.CategorySummaryResponse;
import com.kora.ecommerce.catalog.api.customer.CatalogBrowseDtos.ProductAttributeResponse;
import com.kora.ecommerce.catalog.api.customer.CatalogBrowseDtos.ProductDetailResponse;
import com.kora.ecommerce.catalog.domain.ProductStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class MeteredProductDetailCacheTest {

    private static final UUID PRODUCT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void recordsProductDetailCacheHitsAndMisses() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ProductDetailResponse cached = detailResponse();
        ProductDetailCache cache = new MeteredProductDetailCache(
                new StubProductDetailCache(cached),
                new CatalogCacheMetrics(registry));

        assertThat(cache.get(PRODUCT_ID)).contains(cached);
        assertThat(cache.get(UUID.fromString("22222222-2222-2222-2222-222222222222"))).isEmpty();

        assertThat(registry.get(CatalogCacheMetrics.CACHE_REQUESTS)
                .tag("service", "catalog-service")
                .tag("cache", "product-detail")
                .tag("outcome", "hit")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(registry.get(CatalogCacheMetrics.CACHE_REQUESTS)
                .tag("service", "catalog-service")
                .tag("cache", "product-detail")
                .tag("outcome", "miss")
                .counter()
                .count()).isEqualTo(1.0);
    }

    private static ProductDetailResponse detailResponse() {
        return new ProductDetailResponse(
                PRODUCT_ID,
                "COF-METRIC",
                "Metric Blend",
                "Cache metric sample",
                new BigDecimal("12.0000"),
                "USD",
                ProductStatus.ACTIVE,
                new CategorySummaryResponse(
                        UUID.fromString("33333333-3333-3333-3333-333333333333"),
                        "Coffee",
                        "coffee"),
                List.of(new ProductAttributeResponse("origin", "colombia")));
    }

    private record StubProductDetailCache(ProductDetailResponse cached) implements ProductDetailCache {

        @Override
        public Optional<ProductDetailResponse> get(UUID productId) {
            if (PRODUCT_ID.equals(productId)) {
                return Optional.of(cached);
            }
            return Optional.empty();
        }

        @Override
        public void put(UUID productId, ProductDetailResponse response) {
        }

        @Override
        public void evict(UUID productId) {
        }
    }
}
