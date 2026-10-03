package com.kora.ecommerce.catalog.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kora.ecommerce.catalog.cache.ProductDetailCache;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:catalog_admin_api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "catalog.cache.enabled=false",
        "catalog.supplier-import.chunk-size=2",
        "catalog.supplier-import.import-directory=target/catalog-admin-import-test",
        "management.health.redis.enabled=false"
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

    @MockBean
    private ProductDetailCache productDetailCache;

    private final Path importDirectory = Path.of("target/catalog-admin-import-test").toAbsolutePath().normalize();

    @BeforeEach
    void resetCatalog() throws Exception {
        Files.createDirectories(importDirectory);
        productAttributeRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
        categoryRepository.deleteAllInBatch();
    }

    @Test
    void catalogAdminCanLaunchSupplierImportAndInspectStatus() throws Exception {
        String importPath = "launch-" + UUID.randomUUID() + ".csv";
        writeImportCsv(importPath, """
                sku,product_name,product_description,category_slug,category_name,category_description,price_amount,currency,status,attributes
                API-001,API Product,Imported by API,api-category,API Category,API imports,10.00,usd,ACTIVE,color=black
                """);

        MvcResult launched = mockMvc.perform(post("/api/catalog/admin/supplier-imports")
                        .header("Authorization", "Bearer catalog-admin-token")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "importPath": "%s"
                                }
                                """.formatted(importPath)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.processedCount").value(1))
                .andExpect(jsonPath("$.skippedCount").value(0))
                .andExpect(jsonPath("$.failedCount").value(0))
                .andExpect(jsonPath("$.errorReportLocation").value(importPath + ".errors.csv"))
                .andReturn();

        assertThat(launched.getResponse().getContentAsString())
                .doesNotContain(importDirectory.toString());
        long executionId = longFrom(launched, "executionId");

        mockMvc.perform(get("/api/catalog/admin/supplier-imports/{executionId}", executionId)
                        .header("Authorization", "Bearer catalog-admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionId").value(executionId))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.errorReportLocation").value(importPath + ".errors.csv"));

        assertThat(productRepository.findDetailsBySku("API-001")).isPresent();
    }

    @Test
    void supplierImportLaunchRequiresCatalogAdminRole() throws Exception {
        mockMvc.perform(post("/api/catalog/admin/supplier-imports")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "importPath": "products.csv"
                                }
                                """))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/catalog/admin/supplier-imports")
                        .header("Authorization", "Bearer customer-token")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "importPath": "products.csv"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void supplierImportLaunchRejectsUnsafePathsAndUnsupportedFileTypes() throws Exception {
        for (String importPath : List.of(
                "../secrets.csv",
                "http://supplier.example/products.csv",
                "products.txt")) {
            MvcResult rejected = mockMvc.perform(post("/api/catalog/admin/supplier-imports")
                            .header("Authorization", "Bearer catalog-admin-token")
                            .contentType(APPLICATION_JSON)
                            .content("""
                                    {
                                      "importPath": "%s"
                                    }
                                    """.formatted(importPath)))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertThat(rejected.getResponse().getContentAsString())
                    .doesNotContain(importDirectory.toString());
        }
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
        verify(productDetailCache).evict(productId);
        clearInvocations(productDetailCache);

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

        verify(productDetailCache).evict(productId);
        clearInvocations(productDetailCache);

        mockMvc.perform(patch("/api/catalog/admin/products/{productId}/deactivate", productId)
                        .header("Authorization", "Bearer catalog-admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        verify(productDetailCache).evict(productId);
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
        verify(productDetailCache).evict(product.getId());
        clearInvocations(productDetailCache);

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

        verify(productDetailCache).evict(product.getId());
        clearInvocations(productDetailCache);

        mockMvc.perform(patch("/api/catalog/admin/products/{productId}/attributes/{attributeId}/deactivate",
                        product.getId(),
                        attributeId)
                        .header("Authorization", "Bearer catalog-admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        verify(productDetailCache).evict(product.getId());
        assertThat(productAttributeRepository.findById(attributeId).orElseThrow().isActive()).isFalse();
    }

    @Test
    void categoryUpdateAndDeactivateEvictAffectedProductDetailCacheEntries() throws Exception {
        Category category = categoryRepository.saveAndFlush(new Category("Coffee", "coffee"));
        Product firstProduct = saveProduct(category, "COF-001", ProductStatus.ACTIVE);
        Product secondProduct = saveProduct(category, "COF-002", ProductStatus.DRAFT);
        Category otherCategory = categoryRepository.saveAndFlush(new Category("Tea", "tea"));
        Product otherProduct = saveProduct(otherCategory, "TEA-001", ProductStatus.ACTIVE);
        clearInvocations(productDetailCache);

        mockMvc.perform(put("/api/catalog/admin/categories/{categoryId}", category.getId())
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
                .andExpect(jsonPath("$.slug").value("specialty-coffee"));

        verify(productDetailCache).evict(firstProduct.getId());
        verify(productDetailCache).evict(secondProduct.getId());
        verify(productDetailCache, never()).evict(otherProduct.getId());
        clearInvocations(productDetailCache);

        mockMvc.perform(patch("/api/catalog/admin/categories/{categoryId}/deactivate", category.getId())
                        .header("Authorization", "Bearer catalog-admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        verify(productDetailCache).evict(firstProduct.getId());
        verify(productDetailCache).evict(secondProduct.getId());
        verify(productDetailCache, never()).evict(otherProduct.getId());
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
        return saveProduct(category, sku, ProductStatus.ACTIVE);
    }

    private Product saveProduct(Category category, String sku, ProductStatus status) {
        Product product = new Product(category, sku, "Stoneware Mug", new BigDecimal("12.00"), "USD");
        product.setStatus(status);
        return productRepository.saveAndFlush(product);
    }

    private UUID uuidFrom(MvcResult result, String fieldName) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(root.get(fieldName).asText());
    }

    private long longFrom(MvcResult result, String fieldName) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.get(fieldName).asLong();
    }

    private ResultActions launchSupplierImport(String importPath) throws Exception {
        return mockMvc.perform(post("/api/catalog/admin/supplier-imports")
                .header("Authorization", "Bearer catalog-admin-token")
                .contentType(APPLICATION_JSON)
                .content("""
                        {
                          "importPath": "%s"
                        }
                        """.formatted(importPath)));
    }

    private void writeImportCsv(String importPath, String contents) throws Exception {
        Files.writeString(importDirectory.resolve(importPath), contents);
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
