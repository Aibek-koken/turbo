package com.kora.ecommerce.catalog.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.kora.ecommerce.catalog.api.customer.CatalogBrowseDtos.CategorySummaryResponse;
import com.kora.ecommerce.catalog.api.customer.CatalogBrowseDtos.ProductAttributeResponse;
import com.kora.ecommerce.catalog.api.customer.CatalogBrowseDtos.ProductDetailResponse;
import com.kora.ecommerce.catalog.domain.ProductStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

class ProductDetailCacheJsonTest {

    private final ProductDetailCacheJson cacheJson = new ProductDetailCacheJson(
            Jackson2ObjectMapperBuilder.json().build());

    @Test
    void roundTripsProductDetailResponseWithoutTypeMetadata() {
        ProductDetailResponse response = new ProductDetailResponse(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "COF-DETAIL",
                "Detail Blend",
                "Full detail response",
                new BigDecimal("16.25"),
                "USD",
                ProductStatus.ACTIVE,
                new CategorySummaryResponse(
                        UUID.fromString("22222222-2222-2222-2222-222222222222"),
                        "Coffee",
                        "coffee"),
                List.of(
                        new ProductAttributeResponse("origin", "colombia"),
                        new ProductAttributeResponse("roast", "medium")));

        String payload = cacheJson.write(response);
        ProductDetailResponse roundTripped = cacheJson.read(payload);

        assertThat(payload)
                .contains("\"sku\":\"COF-DETAIL\"")
                .contains("\"attributes\"")
                .doesNotContain("@class", "com.kora.ecommerce.catalog");
        assertThat(roundTripped).isEqualTo(response);
    }
}
