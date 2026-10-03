package com.kora.ecommerce.catalog.cache;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.kora.ecommerce.catalog.api.customer.CatalogBrowseDtos.ProductDetailResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

final class RedisProductDetailCache implements ProductDetailCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisProductDetailCache.class);

    private final StringRedisTemplate redisTemplate;
    private final ProductDetailCacheKeyGenerator keyGenerator;
    private final ProductDetailCacheJson cacheJson;
    private final CatalogCacheProperties properties;

    RedisProductDetailCache(
            StringRedisTemplate redisTemplate,
            ProductDetailCacheKeyGenerator keyGenerator,
            ProductDetailCacheJson cacheJson,
            CatalogCacheProperties properties) {
        this.redisTemplate = redisTemplate;
        this.keyGenerator = keyGenerator;
        this.cacheJson = cacheJson;
        this.properties = properties;
    }

    @Override
    public Optional<ProductDetailResponse> get(UUID productId) {
        String key = keyGenerator.productDetailKey(productId);
        String payload = redisTemplate.opsForValue().get(key);
        if (payload == null || payload.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(cacheJson.read(payload));
        } catch (IllegalArgumentException ex) {
            redisTemplate.delete(key);
            LOGGER.warn("Discarded unreadable product-detail cache entry for key {}", key);
            return Optional.empty();
        }
    }

    @Override
    public void put(UUID productId, ProductDetailResponse response) {
        Objects.requireNonNull(response, "response must not be null");
        redisTemplate.opsForValue().set(
                keyGenerator.productDetailKey(productId),
                cacheJson.write(response),
                properties.getProductDetailTtl());
    }

    @Override
    public void evict(UUID productId) {
        redisTemplate.delete(keyGenerator.productDetailKey(productId));
    }
}
