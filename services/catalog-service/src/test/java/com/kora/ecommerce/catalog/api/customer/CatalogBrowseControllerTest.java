package com.kora.ecommerce.catalog.api.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.kora.ecommerce.catalog.domain.Category;
import com.kora.ecommerce.catalog.domain.Product;
import com.kora.ecommerce.catalog.domain.ProductAttribute;
import com.kora.ecommerce.catalog.domain.ProductStatus;
import com.kora.ecommerce.catalog.repository.CategoryRepository;
import com.kora.ecommerce.catalog.repository.ProductAttributeRepository;
import com.kora.ecommerce.catalog.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:catalog_browse_api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class CatalogBrowseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductAttributeRepository productAttributeRepository;

    @BeforeEach
    void resetCatalog() {
        productAttributeRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
        categoryRepository.deleteAllInBatch();
    }

    @Test
    void browseProductsRequiresBearerToken() throws Exception {
        mockMvc.perform(get("/api/catalog/products"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void browseProductsReturnsActiveProductsWithPagination() throws Exception {
        Category coffee = saveCategory("Coffee", "coffee", true);
        Category hidden = saveCategory("Archive", "archive", false);
        saveProduct(coffee, "COF-001", "A House Blend", "Daily roast", ProductStatus.ACTIVE);
        saveProduct(coffee, "COF-002", "B Espresso", "Dark roast", ProductStatus.ACTIVE);
        saveProduct(coffee, "COF-003", "C Decaf", "Low caffeine", ProductStatus.ACTIVE);
        saveProduct(coffee, "COF-DRAFT", "D Draft", "Not visible", ProductStatus.DRAFT);
        saveProduct(hidden, "ARC-001", "Archived Beans", "Inactive category", ProductStatus.ACTIVE);

        mockMvc.perform(get("/api/catalog/products")
                        .param("page", "0")
                        .param("size", "2")
                        .header("Authorization", "Bearer customer-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].sku").value("COF-001"))
                .andExpect(jsonPath("$.content[0].category.slug").value("coffee"))
                .andExpect(jsonPath("$.content[1].sku").value("COF-002"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false));
    }

    @Test
    void browseProductsFiltersByCategoryStatusAndTextOrSku() throws Exception {
        Category coffee = saveCategory("Coffee", "coffee", true);
        Category tea = saveCategory("Tea", "tea", true);
        saveProduct(coffee, "COF-HOUSE", "House Blend", "Chocolate notes", ProductStatus.ACTIVE);
        saveProduct(coffee, "COF-DECAF", "Decaf Blend", "Evening coffee", ProductStatus.ACTIVE);
        saveProduct(coffee, "COF-DRAFT", "Draft Blend", "Internal", ProductStatus.DRAFT);
        saveProduct(tea, "TEA-HOUSE", "House Tea", "Green tea", ProductStatus.ACTIVE);

        mockMvc.perform(get("/api/catalog/products")
                        .param("category", "coffee")
                        .param("status", "ACTIVE")
                        .param("q", "house")
                        .header("Authorization", "Bearer customer-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].sku").value("COF-HOUSE"));

        mockMvc.perform(get("/api/catalog/products")
                        .param("category", "coffee")
                        .param("q", "COF-DECAF")
                        .header("Authorization", "Bearer customer-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Decaf Blend"));
    }

    @Test
    void browseProductsRejectsCustomerInvisibleStatus() throws Exception {
        mockMvc.perform(get("/api/catalog/products")
                        .param("status", "DRAFT")
                        .header("Authorization", "Bearer customer-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Customer catalog only supports ACTIVE status."));
    }

    @Test
    void productDetailIncludesCategoryAndActiveAttributes() throws Exception {
        Category coffee = saveCategory("Coffee", "coffee", true);
        Product product = new Product(coffee, "COF-DETAIL", "Detail Blend", new BigDecimal("16.25"), "USD");
        product.setDescription("Full detail response");
        product.setStatus(ProductStatus.ACTIVE);
        product.addAttribute(new ProductAttribute("roast", "medium"));
        product.addAttribute(new ProductAttribute("origin", "colombia"));
        ProductAttribute hiddenAttribute = new ProductAttribute("internal", "supplier-only");
        hiddenAttribute.setActive(false);
        product.addAttribute(hiddenAttribute);
        product = productRepository.saveAndFlush(product);

        mockMvc.perform(get("/api/catalog/products/{productId}", product.getId())
                        .header("Authorization", "Bearer customer-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("COF-DETAIL"))
                .andExpect(jsonPath("$.category.slug").value("coffee"))
                .andExpect(jsonPath("$.attributes.length()").value(2))
                .andExpect(jsonPath("$.attributes[0].attributeKey").value("origin"))
                .andExpect(jsonPath("$.attributes[0].attributeValue").value("colombia"))
                .andExpect(jsonPath("$.attributes[0].active").doesNotExist())
                .andExpect(jsonPath("$.attributes[1].attributeKey").value("roast"));
    }

    @Test
    void productDetailDoesNotExposeDraftProducts() throws Exception {
        Category coffee = saveCategory("Coffee", "coffee", true);
        Product product = saveProduct(coffee, "COF-DRAFT", "Draft Blend", "Internal", ProductStatus.DRAFT);

        mockMvc.perform(get("/api/catalog/products/{productId}", product.getId())
                        .header("Authorization", "Bearer customer-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void customerTokenCannotUseAdminOnlyWritePath() throws Exception {
        mockMvc.perform(post("/api/catalog/admin/categories")
                        .header("Authorization", "Bearer customer-token")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Coffee",
                                  "slug": "coffee"
                                }
                                """))
                .andExpect(status().isForbidden());

        assertThat(categoryRepository.count()).isZero();
    }

    private Category saveCategory(String name, String slug, boolean active) {
        Category category = new Category(name, slug);
        category.setActive(active);
        return categoryRepository.saveAndFlush(category);
    }

    private Product saveProduct(
            Category category,
            String sku,
            String name,
            String description,
            ProductStatus status) {
        Product product = new Product(category, sku, name, new BigDecimal("12.00"), "USD");
        product.setDescription(description);
        product.setStatus(status);
        return productRepository.saveAndFlush(product);
    }

    @TestConfiguration
    static class JwtDecoderConfiguration {

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> switch (token) {
                case "customer-token" -> jwtWithRealmRoles(token, "CUSTOMER");
                case "catalog-admin-token" -> jwtWithRealmRoles(token, "CATALOG_ADMIN");
                default -> throw new JwtException("Test decoder rejects unknown bearer tokens.");
            };
        }

        private static Jwt jwtWithRealmRoles(String token, String... roles) {
            Instant issuedAt = Instant.now();
            return new Jwt(
                    token,
                    issuedAt,
                    issuedAt.plusSeconds(300),
                    Map.of("alg", "none"),
                    Map.of(
                            "sub", "test-user",
                            "iss", "http://localhost:8085/realms/ecommerce",
                            "realm_access", Map.of("roles", List.of(roles))));
        }
    }
}
