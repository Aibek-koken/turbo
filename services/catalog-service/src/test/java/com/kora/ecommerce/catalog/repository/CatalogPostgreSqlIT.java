package com.kora.ecommerce.catalog.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import com.kora.ecommerce.catalog.domain.Category;
import com.kora.ecommerce.catalog.domain.Product;
import com.kora.ecommerce.catalog.domain.ProductAttribute;
import com.kora.ecommerce.catalog.domain.ProductStatus;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class CatalogPostgreSqlIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("catalog_it")
            .withUsername("catalog_it")
            .withPassword("catalog_it");

    @DynamicPropertySource
    static void postgresqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.batch.jdbc.initialize-schema", () -> "never");
    }

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductAttributeRepository productAttributeRepository;

    @Test
    void flywayBuildsCatalogOwnedSchemaOnPostgreSql() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("3");

        assertThat(tableNames())
                .contains(
                        "categories",
                        "products",
                        "product_attributes",
                        "batch_job_instance",
                        "batch_job_execution",
                        "batch_step_execution");
    }

    @Test
    void persistsProductDetailsAndCustomerVisibleRepositoryFlow() {
        Category coffee = categoryRepository.save(new Category("Coffee", "coffee-postgres"));
        Category hidden = categoryRepository.save(new Category("Hidden Coffee", "hidden-coffee-postgres"));
        hidden.setActive(false);

        Product activeProduct = new Product(
                coffee,
                "PG-CAT-001",
                "PostgreSQL Espresso",
                new BigDecimal("18.50"),
                "USD");
        activeProduct.setStatus(ProductStatus.ACTIVE);
        activeProduct.addAttribute(new ProductAttribute("origin", "colombia"));
        activeProduct.addAttribute(new ProductAttribute("roast", "dark"));

        Product hiddenProduct = new Product(
                hidden,
                "PG-CAT-002",
                "PostgreSQL Hidden Espresso",
                new BigDecimal("20.00"),
                "USD");
        hiddenProduct.setStatus(ProductStatus.ACTIVE);

        Product savedProduct = productRepository.save(activeProduct);
        productRepository.saveAndFlush(hiddenProduct);

        assertThat(productRepository.findCustomerVisible(
                ProductStatus.ACTIVE,
                null,
                "espresso",
                PageRequest.of(0, 10)))
                .extracting(Product::getSku)
                .containsExactly("PG-CAT-001");

        Product details = productRepository.findDetailsBySku("PG-CAT-001").orElseThrow();

        assertThat(details.getId()).isEqualTo(savedProduct.getId());
        assertThat(details.getCategory().getSlug()).isEqualTo("coffee-postgres");
        assertThat(details.getAttributes())
                .extracting(ProductAttribute::getAttributeKey)
                .containsExactly("origin", "roast");
        assertThat(productAttributeRepository.findByProductIdOrderByAttributeKey(savedProduct.getId()))
                .extracting(ProductAttribute::getAttributeValue)
                .containsExactly("colombia", "dark");
    }

    @Test
    void postgresConstraintsRejectDuplicateCatalogKeys() {
        Category category = categoryRepository.save(new Category("Mugs", "mugs-postgres"));
        productRepository.saveAndFlush(new Product(
                category,
                "PG-CAT-DUP",
                "Stoneware Mug",
                new BigDecimal("12.00"),
                "USD"));

        Product duplicateSku = new Product(
                category,
                "PG-CAT-DUP",
                "Travel Mug",
                new BigDecimal("18.00"),
                "USD");

        assertThatThrownBy(() -> productRepository.saveAndFlush(duplicateSku))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Set<String> tableNames() {
        return new HashSet<>(jdbcTemplate.queryForList(
                """
                        select table_name
                        from information_schema.tables
                        where table_schema = 'public'
                        """,
                String.class));
    }
}
