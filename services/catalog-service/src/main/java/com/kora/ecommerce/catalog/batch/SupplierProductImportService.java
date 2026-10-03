package com.kora.ecommerce.catalog.batch;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import com.kora.ecommerce.catalog.domain.Category;
import com.kora.ecommerce.catalog.domain.Product;
import com.kora.ecommerce.catalog.domain.ProductAttribute;
import com.kora.ecommerce.catalog.repository.CategoryRepository;
import com.kora.ecommerce.catalog.repository.ProductRepository;
import com.kora.ecommerce.catalog.service.ProductDetailCacheInvalidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SupplierProductImportService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductDetailCacheInvalidator productDetailCacheInvalidator;

    SupplierProductImportService(
            CategoryRepository categoryRepository,
            ProductRepository productRepository,
            ProductDetailCacheInvalidator productDetailCacheInvalidator) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.productDetailCacheInvalidator = productDetailCacheInvalidator;
    }

    @Transactional
    public void importRows(Collection<? extends SupplierProductImportCommand> rows) {
        Map<String, Category> categoriesBySlug = new LinkedHashMap<>();
        Map<String, Product> productsBySku = new LinkedHashMap<>();
        for (SupplierProductImportCommand row : rows) {
            importRow(row, categoriesBySlug, productsBySku);
        }
    }

    private void importRow(
            SupplierProductImportCommand row,
            Map<String, Category> categoriesBySlug,
            Map<String, Product> productsBySku) {
        Category category = categoriesBySlug.computeIfAbsent(row.categorySlug(), slug ->
                categoryRepository.findBySlug(slug)
                        .orElseGet(() -> new Category(row.categoryName(), slug)));
        category.setName(row.categoryName());
        category.setDescription(row.categoryDescription());
        category.setActive(true);
        Category savedCategory = categoryRepository.save(category);
        categoriesBySlug.put(row.categorySlug(), savedCategory);

        Product product = productsBySku.computeIfAbsent(row.sku(), sku ->
                productRepository.findDetailsBySku(sku)
                        .orElseGet(() -> new Product(
                                savedCategory,
                                sku,
                                row.productName(),
                                row.priceAmount(),
                                row.currency())));
        product.setCategory(savedCategory);
        product.setSku(row.sku());
        product.setName(row.productName());
        product.setDescription(row.productDescription());
        product.setPriceAmount(row.priceAmount());
        product.setCurrency(row.currency());
        product.setStatus(row.status());
        upsertAttributes(product, row.attributes());

        Product savedProduct = productRepository.save(product);
        productsBySku.put(row.sku(), savedProduct);
        productDetailCacheInvalidator.evictProductAfterCommit(savedProduct.getId());
    }

    private static void upsertAttributes(Product product, Map<String, String> attributes) {
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            ProductAttribute attribute = product.getAttributes().stream()
                    .filter(existing -> existing.getAttributeKey().equals(entry.getKey()))
                    .findFirst()
                    .orElseGet(() -> {
                        ProductAttribute created = new ProductAttribute(entry.getKey(), entry.getValue());
                        product.addAttribute(created);
                        return created;
                    });
            attribute.setAttributeValue(entry.getValue());
            attribute.setActive(true);
        }
    }
}
