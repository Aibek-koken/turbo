package com.kora.ecommerce.catalog.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import com.kora.ecommerce.catalog.domain.Category;
import com.kora.ecommerce.catalog.domain.Product;
import com.kora.ecommerce.catalog.domain.ProductAttribute;
import com.kora.ecommerce.catalog.domain.ProductStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EntityScan(basePackages = "com.kora.ecommerce.catalog.domain")
@EnableJpaRepositories(basePackages = "com.kora.ecommerce.catalog.repository")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:catalog_repository;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.locations=classpath:db/migration",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class CatalogRepositoryTest {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductAttributeRepository productAttributeRepository;

    @Test
    void storesProductWithCategoryAndAttributes() {
        Category category = categoryRepository.save(new Category("Coffee", "coffee"));

        Product product = new Product(category, "COF-001", "House Blend", new BigDecimal("14.50"), "USD");
        product.setStatus(ProductStatus.ACTIVE);
        product.addAttribute(new ProductAttribute("roast", "medium"));
        product.addAttribute(new ProductAttribute("origin", "colombia"));

        Product savedProduct = productRepository.saveAndFlush(product);

        Product details = productRepository.findDetailsBySku("COF-001").orElseThrow();

        assertThat(details.getId()).isEqualTo(savedProduct.getId());
        assertThat(details.getCategory().getSlug()).isEqualTo("coffee");
        assertThat(details.getAttributes())
                .extracting(ProductAttribute::getAttributeKey)
                .containsExactlyInAnyOrder("origin", "roast");
        assertThat(productAttributeRepository.findByProductIdOrderByAttributeKey(savedProduct.getId()))
                .extracting(ProductAttribute::getAttributeValue)
                .containsExactly("colombia", "medium");
    }

    @Test
    void supportsActiveBrowseByCategorySlug() {
        Category category = categoryRepository.save(new Category("Tea", "tea"));
        Product activeProduct = new Product(category, "TEA-001", "Green Tea", new BigDecimal("8.25"), "USD");
        activeProduct.setStatus(ProductStatus.ACTIVE);
        Product draftProduct = new Product(category, "TEA-002", "Oolong", new BigDecimal("9.25"), "USD");

        productRepository.save(activeProduct);
        productRepository.save(draftProduct);
        productRepository.flush();

        assertThat(productRepository.findByStatusAndCategorySlug(ProductStatus.ACTIVE, "tea", PageRequest.of(0, 10)))
                .extracting(Product::getSku)
                .containsExactly("TEA-001");
    }

    @Test
    void rejectsDuplicateSku() {
        Category category = categoryRepository.save(new Category("Mugs", "mugs"));
        productRepository.saveAndFlush(new Product(category, "MUG-001", "Stoneware Mug", new BigDecimal("12.00"), "USD"));

        Product duplicateSku = new Product(category, "MUG-001", "Travel Mug", new BigDecimal("18.00"), "USD");

        assertThatThrownBy(() -> productRepository.saveAndFlush(duplicateSku))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
