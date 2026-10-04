package com.kora.ecommerce.order.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.kora.ecommerce.order.catalog.CatalogProductClient;
import com.kora.ecommerce.order.catalog.ProductSnapshot;
import com.kora.ecommerce.order.catalog.ProductSnapshotResolutionException;
import com.kora.ecommerce.order.persistence.OrderItemRepository;
import com.kora.ecommerce.order.persistence.OrderRepository;
import com.kora.ecommerce.order.persistence.OrderStatusHistoryRepository;
import com.kora.ecommerce.order.persistence.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@SpringBootTest
@ActiveProfiles("test")
@Import(CustomerOrderControllerTest.TestSecurityConfiguration.class)
class CustomerOrderControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-03T14:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @MockBean
    private CatalogProductClient catalogProductClient;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAllInBatch();
        orderStatusHistoryRepository.deleteAllInBatch();
        orderItemRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        reset(catalogProductClient);
    }

    @Test
    void createsOrderFromJwtSubjectAndCatalogSnapshotsIgnoringClientOwnedFields() throws Exception {
        UUID productId = UUID.randomUUID();
        when(catalogProductClient.resolveProductSnapshot(productId))
                .thenReturn(snapshot(productId, "SKU-001", "Catalog Jacket", "12.5000", "USD"));

        mockMvc.perform(post("/api/orders/customer/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer customer-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": "body-customer",
                                  "items": [
                                    {
                                      "productId": "%s",
                                      "quantity": 2,
                                      "productName": "Client Jacket",
                                      "unitPriceAmount": 0.0100,
                                      "currency": "EUR"
                                    }
                                  ]
                                }
                                """.formatted(productId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").isNotEmpty())
                .andExpect(jsonPath("$.customerId").value("jwt-customer-123"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.subtotalAmount").value(25.0000))
                .andExpect(jsonPath("$.totalAmount").value(25.0000))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.items[0].productId").value(productId.toString()))
                .andExpect(jsonPath("$.items[0].productSku").value("SKU-001"))
                .andExpect(jsonPath("$.items[0].productName").value("Catalog Jacket"))
                .andExpect(jsonPath("$.items[0].unitPriceAmount").value(12.5000))
                .andExpect(jsonPath("$.items[0].lineTotalAmount").value(25.0000));

        assertThat(orderRepository.findAll()).singleElement().satisfies(order -> {
            assertThat(order.getCustomerId()).isEqualTo("jwt-customer-123");
            assertThat(order.getTotalAmount()).isEqualByComparingTo("25.0000");
            assertThat(order.getCurrency()).isEqualTo("USD");
            assertThat(orderItemRepository.findByOrder_IdOrderByItemNumberAsc(order.getId()))
                    .singleElement()
                    .satisfies(item -> {
                        assertThat(item.getProductId()).isEqualTo(productId);
                        assertThat(item.getProductSku()).isEqualTo("SKU-001");
                        assertThat(item.getProductName()).isEqualTo("Catalog Jacket");
                        assertThat(item.getUnitPriceAmount()).isEqualByComparingTo("12.5000");
                        assertThat(item.getLineTotalAmount()).isEqualByComparingTo("25.0000");
                    });
            assertThat(orderStatusHistoryRepository.findByOrder_IdOrderByChangedAtAsc(order.getId()))
                    .singleElement()
                    .satisfies(history -> assertThat(history.getStatus().name()).isEqualTo("CREATED"));
        });
    }

    @Test
    void rejectsOrderCreateWithoutCustomerRole() throws Exception {
        mockMvc.perform(post("/api/orders/customer/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer ops-admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items": [{"productId": "%s", "quantity": 1}]}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isForbidden());

        verifyNoInteractions(catalogProductClient);
        assertThat(orderRepository.findAll()).isEmpty();
    }

    @Test
    void rejectsEmptyOrdersWithProblemDetails() throws Exception {
        mockMvc.perform(post("/api/orders/customer/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer customer-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items": []}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Order request is invalid"))
                .andExpect(jsonPath("$.failure").value("EMPTY_ORDER"));

        verifyNoInteractions(catalogProductClient);
        assertThat(orderRepository.findAll()).isEmpty();
    }

    @Test
    void rejectsDuplicateProductsWithProblemDetails() throws Exception {
        UUID productId = UUID.randomUUID();

        mockMvc.perform(post("/api/orders/customer/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer customer-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    {"productId": "%s", "quantity": 1},
                                    {"productId": "%s", "quantity": 2}
                                  ]
                                }
                                """.formatted(productId, productId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.failure").value("DUPLICATE_PRODUCT"))
                .andExpect(jsonPath("$.productId").value(productId.toString()));

        verifyNoInteractions(catalogProductClient);
        assertThat(orderRepository.findAll()).isEmpty();
    }

    @Test
    void rejectsInvalidQuantityWithProblemDetails() throws Exception {
        UUID productId = UUID.randomUUID();

        mockMvc.perform(post("/api/orders/customer/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer customer-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items": [{"productId": "%s", "quantity": 0}]}
                                """.formatted(productId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.failure").value("INVALID_QUANTITY"))
                .andExpect(jsonPath("$.productId").value(productId.toString()))
                .andExpect(jsonPath("$.maxAllowed").value(100));

        verifyNoInteractions(catalogProductClient);
        assertThat(orderRepository.findAll()).isEmpty();
    }

    @Test
    void rejectsMixedCurrenciesWithProblemDetails() throws Exception {
        UUID firstProductId = UUID.randomUUID();
        UUID secondProductId = UUID.randomUUID();
        when(catalogProductClient.resolveProductSnapshot(firstProductId))
                .thenReturn(snapshot(firstProductId, "SKU-001", "Catalog Jacket", "12.5000", "USD"));
        when(catalogProductClient.resolveProductSnapshot(secondProductId))
                .thenReturn(snapshot(secondProductId, "SKU-002", "Catalog Hat", "9.0000", "EUR"));

        mockMvc.perform(post("/api/orders/customer/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer customer-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    {"productId": "%s", "quantity": 1},
                                    {"productId": "%s", "quantity": 1}
                                  ]
                                }
                                """.formatted(firstProductId, secondProductId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.failure").value("MIXED_CURRENCIES"))
                .andExpect(jsonPath("$.productId").value(secondProductId.toString()));

        assertThat(orderRepository.findAll()).isEmpty();
    }

    @Test
    void mapsMissingCatalogProductToProblemDetails() throws Exception {
        UUID productId = UUID.randomUUID();
        when(catalogProductClient.resolveProductSnapshot(productId))
                .thenThrow(ProductSnapshotResolutionException.missing(productId));

        mockMvc.perform(post("/api/orders/customer/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer customer-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items": [{"productId": "%s", "quantity": 1}]}
                                """.formatted(productId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Product snapshot resolution failed"))
                .andExpect(jsonPath("$.failure").value("MISSING"))
                .andExpect(jsonPath("$.productId").value(productId.toString()));

        assertThat(orderRepository.findAll()).isEmpty();
    }

    @Test
    void mapsInactiveCatalogProductToProblemDetails() throws Exception {
        UUID productId = UUID.randomUUID();
        when(catalogProductClient.resolveProductSnapshot(productId))
                .thenThrow(ProductSnapshotResolutionException.inactive(productId));

        mockMvc.perform(post("/api/orders/customer/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer customer-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items": [{"productId": "%s", "quantity": 1}]}
                                """.formatted(productId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.failure").value("INACTIVE"))
                .andExpect(jsonPath("$.productId").value(productId.toString()));

        assertThat(orderRepository.findAll()).isEmpty();
    }

    private static ProductSnapshot snapshot(
            UUID productId,
            String sku,
            String name,
            String unitPriceAmount,
            String currency) {
        return new ProductSnapshot(
                productId,
                sku,
                name,
                new BigDecimal(unitPriceAmount),
                currency);
    }

    @TestConfiguration
    static class TestSecurityConfiguration {

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> switch (token) {
                case "customer-token" -> jwtWithRealmRoles(token, "jwt-customer-123", "CUSTOMER");
                case "ops-admin-token" -> jwtWithRealmRoles(token, "ops-admin-123", "OPS_ADMIN");
                default -> throw new JwtException("Test decoder rejects unknown bearer tokens.");
            };
        }

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        private static Jwt jwtWithRealmRoles(String token, String subject, String... roles) {
            return new Jwt(
                    token,
                    NOW,
                    NOW.plusSeconds(300),
                    Map.of("alg", "none"),
                    Map.of(
                            "sub", subject,
                            "iss", "http://localhost:8085/realms/ecommerce",
                            "realm_access", Map.of("roles", List.of(roles))));
        }
    }
}
