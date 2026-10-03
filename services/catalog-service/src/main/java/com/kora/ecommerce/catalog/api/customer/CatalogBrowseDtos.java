package com.kora.ecommerce.catalog.api.customer;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.kora.ecommerce.catalog.domain.Category;
import com.kora.ecommerce.catalog.domain.Product;
import com.kora.ecommerce.catalog.domain.ProductAttribute;
import com.kora.ecommerce.catalog.domain.ProductStatus;
import org.springframework.data.domain.Page;

public final class CatalogBrowseDtos {

    private CatalogBrowseDtos() {
    }

    public record ProductBrowsePageResponse(
            List<ProductSummaryResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean first,
            boolean last) {

        public static ProductBrowsePageResponse from(Page<Product> products) {
            return new ProductBrowsePageResponse(
                    products.getContent().stream().map(ProductSummaryResponse::from).toList(),
                    products.getNumber(),
                    products.getSize(),
                    products.getTotalElements(),
                    products.getTotalPages(),
                    products.isFirst(),
                    products.isLast());
        }
    }

    public record ProductSummaryResponse(
            UUID id,
            String sku,
            String name,
            String description,
            BigDecimal priceAmount,
            String currency,
            ProductStatus status,
            CategorySummaryResponse category) {

        public static ProductSummaryResponse from(Product product) {
            return new ProductSummaryResponse(
                    product.getId(),
                    product.getSku(),
                    product.getName(),
                    product.getDescription(),
                    product.getPriceAmount(),
                    product.getCurrency(),
                    product.getStatus(),
                    CategorySummaryResponse.from(product.getCategory()));
        }
    }

    public record ProductDetailResponse(
            UUID id,
            String sku,
            String name,
            String description,
            BigDecimal priceAmount,
            String currency,
            ProductStatus status,
            CategorySummaryResponse category,
            List<ProductAttributeResponse> attributes) {

        public static ProductDetailResponse from(Product product) {
            List<ProductAttributeResponse> attributes = product.getAttributes().stream()
                    .filter(ProductAttribute::isActive)
                    .sorted(Comparator.comparing(ProductAttribute::getAttributeKey))
                    .map(ProductAttributeResponse::from)
                    .toList();

            return new ProductDetailResponse(
                    product.getId(),
                    product.getSku(),
                    product.getName(),
                    product.getDescription(),
                    product.getPriceAmount(),
                    product.getCurrency(),
                    product.getStatus(),
                    CategorySummaryResponse.from(product.getCategory()),
                    attributes);
        }
    }

    public record CategorySummaryResponse(UUID id, String name, String slug) {

        public static CategorySummaryResponse from(Category category) {
            return new CategorySummaryResponse(category.getId(), category.getName(), category.getSlug());
        }
    }

    public record ProductAttributeResponse(String attributeKey, String attributeValue) {

        public static ProductAttributeResponse from(ProductAttribute attribute) {
            return new ProductAttributeResponse(attribute.getAttributeKey(), attribute.getAttributeValue());
        }
    }
}
