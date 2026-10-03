package com.kora.ecommerce.catalog.service;

import java.util.UUID;

import com.kora.ecommerce.catalog.api.CatalogApiException;
import com.kora.ecommerce.catalog.api.customer.CatalogBrowseDtos.ProductBrowsePageResponse;
import com.kora.ecommerce.catalog.api.customer.CatalogBrowseDtos.ProductDetailResponse;
import com.kora.ecommerce.catalog.domain.Product;
import com.kora.ecommerce.catalog.domain.ProductStatus;
import com.kora.ecommerce.catalog.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogBrowseService {

    private static final ProductStatus CUSTOMER_VISIBLE_STATUS = ProductStatus.ACTIVE;

    private final ProductRepository productRepository;

    public CatalogBrowseService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public ProductBrowsePageResponse browseProducts(
            String categorySlug,
            ProductStatus status,
            String query,
            int page,
            int size) {
        ProductStatus requestedStatus = status == null ? CUSTOMER_VISIBLE_STATUS : status;
        if (requestedStatus != CUSTOMER_VISIBLE_STATUS) {
            throw CatalogApiException.badRequest("Customer catalog only supports ACTIVE status.");
        }

        PageRequest pageRequest = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.asc("name"), Sort.Order.asc("sku")));
        Page<Product> products = productRepository.findCustomerVisible(
                CUSTOMER_VISIBLE_STATUS,
                optionalText(categorySlug),
                optionalText(query),
                pageRequest);

        return ProductBrowsePageResponse.from(products);
    }

    @Transactional(readOnly = true)
    public ProductDetailResponse getProduct(UUID productId) {
        Product product = productRepository.findCustomerVisibleDetailsById(productId, CUSTOMER_VISIBLE_STATUS)
                .orElseThrow(() -> CatalogApiException.notFound("Product not found: " + productId));
        return ProductDetailResponse.from(product);
    }

    private static String optionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
