package com.kora.ecommerce.catalog.api.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.kora.ecommerce.catalog.domain.Category;
import com.kora.ecommerce.catalog.domain.Product;
import com.kora.ecommerce.catalog.domain.ProductAttribute;
import com.kora.ecommerce.catalog.domain.ProductStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class CatalogAdminDtos {

    private CatalogAdminDtos() {
    }

    public record CreateCategoryRequest(
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Size(max = 180) @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") String slug,
            @Size(max = 1000) String description) {
    }

    public record UpdateCategoryRequest(
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Size(max = 180) @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") String slug,
            @Size(max = 1000) String description) {
    }

    public record CreateProductRequest(
            @NotNull UUID categoryId,
            @NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9._-]+$") String sku,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2000) String description,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal priceAmount,
            @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
            ProductStatus status,
            @Size(max = 50) List<@Valid ProductAttributeInput> attributes) {
    }

    public record UpdateProductRequest(
            @NotNull UUID categoryId,
            @NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9._-]+$") String sku,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2000) String description,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal priceAmount,
            @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
            @NotNull ProductStatus status) {
    }

    public record ProductAttributeInput(
            @NotBlank @Size(max = 120) String attributeKey,
            @NotBlank @Size(max = 1000) String attributeValue) {
    }

    public record CreateProductAttributeRequest(
            @NotBlank @Size(max = 120) String attributeKey,
            @NotBlank @Size(max = 1000) String attributeValue) {
    }

    public record UpdateProductAttributeRequest(
            @NotBlank @Size(max = 120) String attributeKey,
            @NotBlank @Size(max = 1000) String attributeValue) {
    }

    public record CategoryResponse(
            UUID id,
            String name,
            String slug,
            String description,
            boolean active,
            Instant createdAt,
            Instant updatedAt) {

        public static CategoryResponse from(Category category) {
            return new CategoryResponse(
                    category.getId(),
                    category.getName(),
                    category.getSlug(),
                    category.getDescription(),
                    category.isActive(),
                    category.getCreatedAt(),
                    category.getUpdatedAt());
        }
    }

    public record ProductResponse(
            UUID id,
            UUID categoryId,
            String categorySlug,
            String sku,
            String name,
            String description,
            BigDecimal priceAmount,
            String currency,
            ProductStatus status,
            List<ProductAttributeResponse> attributes,
            Instant createdAt,
            Instant updatedAt) {

        public static ProductResponse from(Product product) {
            List<ProductAttributeResponse> attributes = product.getAttributes().stream()
                    .sorted(Comparator.comparing(ProductAttribute::getAttributeKey))
                    .map(ProductAttributeResponse::from)
                    .toList();

            return new ProductResponse(
                    product.getId(),
                    product.getCategory().getId(),
                    product.getCategory().getSlug(),
                    product.getSku(),
                    product.getName(),
                    product.getDescription(),
                    product.getPriceAmount(),
                    product.getCurrency(),
                    product.getStatus(),
                    attributes,
                    product.getCreatedAt(),
                    product.getUpdatedAt());
        }
    }

    public record ProductAttributeResponse(
            UUID id,
            UUID productId,
            String attributeKey,
            String attributeValue,
            boolean active,
            Instant createdAt,
            Instant updatedAt) {

        public static ProductAttributeResponse from(ProductAttribute attribute) {
            return new ProductAttributeResponse(
                    attribute.getId(),
                    attribute.getProduct().getId(),
                    attribute.getAttributeKey(),
                    attribute.getAttributeValue(),
                    attribute.isActive(),
                    attribute.getCreatedAt(),
                    attribute.getUpdatedAt());
        }
    }
}
