package com.kora.ecommerce.catalog.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

final class CatalogCacheMetrics {

    static final String CACHE_REQUESTS = "ecommerce.cache.requests";

    private static final String SERVICE = "catalog-service";
    private static final String PRODUCT_DETAIL_CACHE = "product-detail";
    private static final String DESCRIPTION = "Catalog cache lookup outcomes.";

    private final MeterRegistry meterRegistry;

    CatalogCacheMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    void recordProductDetailHit() {
        recordProductDetailLookup("hit");
    }

    void recordProductDetailMiss() {
        recordProductDetailLookup("miss");
    }

    private void recordProductDetailLookup(String outcome) {
        Counter.builder(CACHE_REQUESTS)
                .description(DESCRIPTION)
                .tag("service", SERVICE)
                .tag("cache", PRODUCT_DETAIL_CACHE)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
    }
}
