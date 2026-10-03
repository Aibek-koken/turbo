package com.kora.ecommerce.catalog.service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.kora.ecommerce.catalog.api.admin.CatalogAdminDtos.CategoryResponse;
import com.kora.ecommerce.catalog.api.admin.CatalogAdminDtos.CreateCategoryRequest;
import com.kora.ecommerce.catalog.api.admin.CatalogAdminDtos.CreateProductAttributeRequest;
import com.kora.ecommerce.catalog.api.admin.CatalogAdminDtos.CreateProductRequest;
import com.kora.ecommerce.catalog.api.admin.CatalogAdminDtos.ProductAttributeInput;
import com.kora.ecommerce.catalog.api.admin.CatalogAdminDtos.ProductAttributeResponse;
import com.kora.ecommerce.catalog.api.admin.CatalogAdminDtos.ProductResponse;
import com.kora.ecommerce.catalog.api.admin.CatalogAdminDtos.UpdateCategoryRequest;
import com.kora.ecommerce.catalog.api.admin.CatalogAdminDtos.UpdateProductAttributeRequest;
import com.kora.ecommerce.catalog.api.admin.CatalogAdminDtos.UpdateProductRequest;
import com.kora.ecommerce.catalog.api.admin.CatalogAdminException;
import com.kora.ecommerce.catalog.domain.Category;
import com.kora.ecommerce.catalog.domain.Product;
import com.kora.ecommerce.catalog.domain.ProductAttribute;
import com.kora.ecommerce.catalog.domain.ProductStatus;
import com.kora.ecommerce.catalog.repository.CategoryRepository;
import com.kora.ecommerce.catalog.repository.ProductAttributeRepository;
import com.kora.ecommerce.catalog.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogAdminService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductAttributeRepository productAttributeRepository;

    public CatalogAdminService(
            CategoryRepository categoryRepository,
            ProductRepository productRepository,
            ProductAttributeRepository productAttributeRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.productAttributeRepository = productAttributeRepository;
    }

    @Transactional
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        String slug = requiredText(request.slug());
        ensureCategorySlugAvailable(slug, null);

        Category category = new Category(requiredText(request.name()), slug);
        category.setDescription(optionalText(request.description()));

        return CategoryResponse.from(categoryRepository.saveAndFlush(category));
    }

    @Transactional
    public CategoryResponse updateCategory(UUID categoryId, UpdateCategoryRequest request) {
        Category category = findCategory(categoryId);
        String slug = requiredText(request.slug());
        ensureCategorySlugAvailable(slug, category.getId());

        category.setName(requiredText(request.name()));
        category.setSlug(slug);
        category.setDescription(optionalText(request.description()));

        return CategoryResponse.from(categoryRepository.saveAndFlush(category));
    }

    @Transactional
    public CategoryResponse deactivateCategory(UUID categoryId) {
        Category category = findCategory(categoryId);
        category.setActive(false);
        return CategoryResponse.from(categoryRepository.saveAndFlush(category));
    }

    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        Category category = findActiveCategory(request.categoryId());
        String sku = requiredText(request.sku());
        ensureProductSkuAvailable(sku, null);

        Product product = new Product(
                category,
                sku,
                requiredText(request.name()),
                request.priceAmount(),
                requiredText(request.currency()));
        product.setDescription(optionalText(request.description()));
        product.setStatus(Optional.ofNullable(request.status()).orElse(ProductStatus.DRAFT));
        addInitialAttributes(product, request.attributes());

        return ProductResponse.from(productRepository.saveAndFlush(product));
    }

    @Transactional
    public ProductResponse updateProduct(UUID productId, UpdateProductRequest request) {
        Product product = findProductDetails(productId);
        Category category = findActiveCategory(request.categoryId());
        String sku = requiredText(request.sku());
        ensureProductSkuAvailable(sku, product.getId());

        product.setCategory(category);
        product.setSku(sku);
        product.setName(requiredText(request.name()));
        product.setDescription(optionalText(request.description()));
        product.setPriceAmount(request.priceAmount());
        product.setCurrency(requiredText(request.currency()));
        product.setStatus(request.status());

        return ProductResponse.from(productRepository.saveAndFlush(product));
    }

    @Transactional
    public ProductResponse deactivateProduct(UUID productId) {
        Product product = findProductDetails(productId);
        product.setStatus(ProductStatus.INACTIVE);
        return ProductResponse.from(productRepository.saveAndFlush(product));
    }

    @Transactional
    public ProductAttributeResponse createAttribute(UUID productId, CreateProductAttributeRequest request) {
        Product product = findProductDetails(productId);
        String attributeKey = requiredText(request.attributeKey());
        String attributeValue = requiredText(request.attributeValue());

        Optional<ProductAttribute> existing = productAttributeRepository.findByProductIdAndAttributeKey(
                productId,
                attributeKey);
        if (existing.filter(ProductAttribute::isActive).isPresent()) {
            throw CatalogAdminException.conflict("Product attribute already exists for key: " + attributeKey);
        }

        ProductAttribute attribute = existing.orElseGet(() -> {
            ProductAttribute created = new ProductAttribute(attributeKey, attributeValue);
            product.addAttribute(created);
            return created;
        });
        attribute.setAttributeValue(attributeValue);
        attribute.setActive(true);

        return ProductAttributeResponse.from(productAttributeRepository.saveAndFlush(attribute));
    }

    @Transactional
    public ProductAttributeResponse updateAttribute(
            UUID productId,
            UUID attributeId,
            UpdateProductAttributeRequest request) {
        ProductAttribute attribute = findAttribute(productId, attributeId);
        String attributeKey = requiredText(request.attributeKey());
        ensureAttributeKeyAvailable(productId, attributeKey, attribute.getId());

        attribute.setAttributeKey(attributeKey);
        attribute.setAttributeValue(requiredText(request.attributeValue()));

        return ProductAttributeResponse.from(productAttributeRepository.saveAndFlush(attribute));
    }

    @Transactional
    public ProductAttributeResponse deactivateAttribute(UUID productId, UUID attributeId) {
        ProductAttribute attribute = findAttribute(productId, attributeId);
        attribute.setActive(false);
        return ProductAttributeResponse.from(productAttributeRepository.saveAndFlush(attribute));
    }

    private void addInitialAttributes(Product product, List<ProductAttributeInput> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return;
        }

        Set<String> attributeKeys = new HashSet<>();
        for (ProductAttributeInput attribute : attributes) {
            String attributeKey = requiredText(attribute.attributeKey());
            if (!attributeKeys.add(attributeKey)) {
                throw CatalogAdminException.badRequest("Duplicate attribute key in request: " + attributeKey);
            }
            product.addAttribute(new ProductAttribute(attributeKey, requiredText(attribute.attributeValue())));
        }
    }

    private Category findCategory(UUID categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> CatalogAdminException.notFound("Category not found: " + categoryId));
    }

    private Category findActiveCategory(UUID categoryId) {
        Category category = findCategory(categoryId);
        if (!category.isActive()) {
            throw CatalogAdminException.conflict("Category is inactive: " + categoryId);
        }
        return category;
    }

    private Product findProductDetails(UUID productId) {
        return productRepository.findDetailsById(productId)
                .orElseThrow(() -> CatalogAdminException.notFound("Product not found: " + productId));
    }

    private ProductAttribute findAttribute(UUID productId, UUID attributeId) {
        return productAttributeRepository.findByIdAndProductId(attributeId, productId)
                .orElseThrow(() -> CatalogAdminException.notFound(
                        "Product attribute not found for product: " + productId));
    }

    private void ensureCategorySlugAvailable(String slug, UUID currentCategoryId) {
        categoryRepository.findBySlug(slug)
                .filter(existing -> !existing.getId().equals(currentCategoryId))
                .ifPresent(existing -> {
                    throw CatalogAdminException.conflict("Category slug already exists: " + slug);
                });
    }

    private void ensureProductSkuAvailable(String sku, UUID currentProductId) {
        productRepository.findBySku(sku)
                .filter(existing -> !existing.getId().equals(currentProductId))
                .ifPresent(existing -> {
                    throw CatalogAdminException.conflict("Product SKU already exists: " + sku);
                });
    }

    private void ensureAttributeKeyAvailable(UUID productId, String attributeKey, UUID currentAttributeId) {
        productAttributeRepository.findByProductIdAndAttributeKey(productId, attributeKey)
                .filter(existing -> !existing.getId().equals(currentAttributeId))
                .ifPresent(existing -> {
                    throw CatalogAdminException.conflict("Product attribute already exists for key: " + attributeKey);
                });
    }

    private static String requiredText(String value) {
        return value.trim();
    }

    private static String optionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
