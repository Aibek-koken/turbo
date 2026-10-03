package com.kora.ecommerce.catalog.cache;

import java.util.Objects;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kora.ecommerce.catalog.api.customer.CatalogBrowseDtos.ProductDetailResponse;

final class ProductDetailCacheJson {

    private final ObjectMapper objectMapper;

    ProductDetailCacheJson(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    String write(ProductDetailResponse response) {
        Objects.requireNonNull(response, "response must not be null");
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Product detail response cannot be serialized for caching.", ex);
        }
    }

    ProductDetailResponse read(String payload) {
        Objects.requireNonNull(payload, "payload must not be null");
        try {
            return objectMapper.readValue(payload, ProductDetailResponse.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Cached product detail response cannot be read.", ex);
        }
    }
}
