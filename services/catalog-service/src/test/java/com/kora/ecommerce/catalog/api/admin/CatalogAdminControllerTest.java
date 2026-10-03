package com.kora.ecommerce.catalog.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kora.ecommerce.catalog.domain.Category;
import com.kora.ecommerce.catalog.domain.Product;
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
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:catalog_admin_api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class CatalogAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    void adminCanCreateUpdateAndDeactivateCategory() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/catalog/admin/categories")
                        .header("Authorization", "Bearer catalog-admin-token")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Coffee",
                                  "slug": "coffee",
                                  "description": "Roasted beans"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("coffee"))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();

        UUID categoryId = uuidFrom(created, "id");

        mockMvc.perform(put("/api/catalog/admin/categories/{categoryId}", categoryId)
                        .header("Authorization", "Bearer catalog-admin-token")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Specialty Coffee",
                                  "slug": "specialty-coffee",
                                  "description": "Single origin beans"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Specialty Coffee"))
                .andExpect(jsonPath("$.slug").value("specialty-coffee"));

        mockMvc.perform(patch("/api/catalog/admin/categories/{categoryId}/deactivate", categoryId)
                        .header("Authorization", "Bearer catalog-admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        assertThat(categoryRepository.findById(categoryId).orElseThrow().isActive()).isFalse();
    }

    @Test
    void adminCanCreateUpdateAndDeactivateProduct() throws Exception {
        Category category = categoryRepository.saveAndFlush(new Category("Tea", "tea"));

        MvcResult created = mockMvc.perform(post("/api/catalog/admin/products")
                        .header("Authorization", "Bearer catalog-admin-token")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId": "%s",
                                  "sku": "TEA-001",
                                  "name": "Green Tea",
                                  "description": "Loose leaf",
                                  "priceAmount": 8.25,
                                  "currency": "USD",
                                  "status": "ACTIVE",
                                  "attributes": [
                                    {
                                      "attributeKey": "origin",
                                      "attributeValue": "japan"
                                    }
                                  ]
                                }
                                """.formatted(category.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("TEA-001"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.attributes[0].attributeKey").value("origin"))
                .andReturn();

        UUID productId = uuidFrom(created, "id");

        mockMvc.perform(put("/api/catalog/admin/products/{productId}", productId)
                        .header("Authorization", "Bearer catalog-admin-token")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId": "%s",
                                  "sku": "TEA-001",
                                  "name": "Ceremonial Green Tea",
                                  "description": "First harvest",
                                  "priceAmount": 12.00,
                                  "currency": "USD",
                                  "status": "ACTIVE"
                                }
                                """.formatted(category.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ceremonial Green Tea"))
                .andExpect(jsonPath("$.priceAmount").value(12.00));

        mockMvc.perform(patch("/api/catalog/admin/products/{productId}/deactivate", productId)
                        .header("Authorization", "Bearer catalog-admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        assertThat(productRepository.findById(productId).orElseThrow().getStatus()).isEqualTo(ProductStatus.INACTIVE);
    }

    @Test
    void adminCanCreateUpdateAndDeactivateProductAttribute() throws Exception {
        Product product = saveProduct("MUG-001");

        MvcResult created = mockMvc.perform(post("/api/catalog/admin/products/{productId}/attributes", product.getId())
                        .header("Authorization", "Bearer catalog-admin-token")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "attributeKey": "material",
                                  "attributeValue": "ceramic"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attributeKey").value("material"))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();

        UUID attributeId = uuidFrom(created, "id");

        mockMvc.perform(put("/api/catalog/admin/products/{productId}/attributes/{attributeId}",
                        product.getId(),
                        attributeId)
                        .header("Authorization", "Bearer catalog-admin-token")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "attributeKey": "finish",
                                  "attributeValue": "matte"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attributeKey").value("finish"))
                .andExpect(jsonPath("$.attributeValue").value("matte"));

        mockMvc.perform(patch("/api/catalog/admin/products/{productId}/attributes/{attributeId}/deactivate",
                        product.getId(),
                        attributeId)
                        .header("Authorization", "Bearer catalog-admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        assertThat(productAttributeRepository.findById(attributeId).orElseThrow().isActive()).isFalse();
    }

    @Test
    void adminWriteRejectsMissingBearerToken() throws Exception {
        mockMvc.perform(post("/api/catalog/admin/categories")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Coffee",
                                  "slug": "coffee"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminWriteRejectsCustomerRole() throws Exception {
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
    }

    @Test
    void invalidAdminRequestReturnsValidationErrors() throws Exception {
        mockMvc.perform(post("/api/catalog/admin/products")
                        .header("Authorization", "Bearer catalog-admin-token")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "sku": "",
                                  "name": "",
                                  "priceAmount": -1,
                                  "currency": "usd"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request body"))
                .andExpect(jsonPath("$.errors.categoryId").exists())
                .andExpect(jsonPath("$.errors.sku").exists())
                .andExpect(jsonPath("$.errors.currency").exists());
    }

    @Test
    void domainFailureReturnsConflictProblem() throws Exception {
        categoryRepository.saveAndFlush(new Category("Coffee", "coffee"));

        mockMvc.perform(post("/api/catalog/admin/categories")
                        .header("Authorization", "Bearer catalog-admin-token")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Coffee Again",
                                  "slug": "coffee"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Category slug already exists: coffee"));
    }

    private Product saveProduct(String sku) {
        Category category = categoryRepository.saveAndFlush(new Category("Mugs", "mugs"));
        Product product = new Product(category, sku, "Stoneware Mug", new BigDecimal("12.00"), "USD");
        product.setStatus(ProductStatus.ACTIVE);
        return productRepository.saveAndFlush(product);
    }

    private UUID uuidFrom(MvcResult result, String fieldName) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(root.get(fieldName).asText());
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
