package com.kora.ecommerce.catalog.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import com.kora.ecommerce.catalog.api.customer.CatalogBrowseDtos.CategorySummaryResponse;
import com.kora.ecommerce.catalog.api.customer.CatalogBrowseDtos.ProductAttributeResponse;
import com.kora.ecommerce.catalog.api.customer.CatalogBrowseDtos.ProductDetailResponse;
import com.kora.ecommerce.catalog.domain.ProductStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

@ExtendWith(MockitoExtension.class)
class RedisProductDetailCacheTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    void putWritesProductDetailWithConfiguredTtl() {
        UUID productId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        CatalogCacheProperties properties = new CatalogCacheProperties();
        properties.setKeyPrefix("catalog:test-product-detail");
        properties.setProductDetailTtl(Duration.ofSeconds(45));
        RedisProductDetailCache cache = new RedisProductDetailCache(
                redisTemplate,
                new ProductDetailCacheKeyGenerator(properties),
                new ProductDetailCacheJson(Jackson2ObjectMapperBuilder.json().build()),
                properties);
        ProductDetailResponse response = new ProductDetailResponse(
                productId,
                "COF-TTL",
                "TTL Blend",
                "Cached with an explicit TTL",
                new BigDecimal("18.50"),
                "USD",
                ProductStatus.ACTIVE,
                new CategorySummaryResponse(
                        UUID.fromString("22222222-2222-2222-2222-222222222222"),
                        "Coffee",
                        "coffee"),
                List.of(new ProductAttributeResponse("origin", "colombia")));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        cache.put(productId, response);

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(
                eq("catalog:test-product-detail:v1:" + productId),
                payload.capture(),
                eq(Duration.ofSeconds(45)));
        assertThat(payload.getValue())
                .contains("\"sku\":\"COF-TTL\"")
                .contains("\"status\":\"ACTIVE\"");
    }
}
