package com.kora.ecommerce.order.api;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.kora.ecommerce.order.persistence.OrderEntity;
import com.kora.ecommerce.order.persistence.OrderItemEntity;
import com.kora.ecommerce.order.persistence.OrderItemRepository;
import com.kora.ecommerce.order.persistence.OrderRepository;
import com.kora.ecommerce.order.persistence.OrderStatus;
import com.kora.ecommerce.order.persistence.OrderStatusHistoryEntity;
import com.kora.ecommerce.order.persistence.OrderStatusHistoryRepository;
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
@Import(OpsOrderTransitionControllerTest.TestSecurityConfiguration.class)
class OpsOrderTransitionControllerTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-03T12:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-03T16:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @MockBean
    private CatalogProductClient catalogProductClient;

    @BeforeEach
    void setUp() {
        orderStatusHistoryRepository.deleteAllInBatch();
        orderItemRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
    }

    @Test
    void opsAdminCanTransitionOrderAndAppendHistory() throws Exception {
        UUID orderId = persistCreatedOrder();

        mockMvc.perform(post("/api/orders/ops/orders/{orderId}/transitions", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer ops-admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetStatus": "PAYMENT_PENDING",
                                  "reason": " send to payment "
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.previousStatus").value("CREATED"))
                .andExpect(jsonPath("$.currentStatus").value("PAYMENT_PENDING"))
                .andExpect(jsonPath("$.changedAt").value(NOW.toString()))
                .andExpect(jsonPath("$.reason").value("send to payment"));

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(statusHistory(orderId))
                .extracting(OrderStatusHistoryEntity::getStatus)
                .containsExactly(OrderStatus.CREATED, OrderStatus.PAYMENT_PENDING);
        assertThat(statusHistory(orderId).get(1).getChangedAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsCustomerRoleWithoutChangingOrder() throws Exception {
        UUID orderId = persistCreatedOrder();

        mockMvc.perform(post("/api/orders/ops/orders/{orderId}/transitions", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer customer-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetStatus": "PAYMENT_PENDING"}
                                """))
                .andExpect(status().isForbidden());

        assertOrderUnchanged(orderId);
    }

    @Test
    void rejectsMissingBearerTokenWithoutChangingOrder() throws Exception {
        UUID orderId = persistCreatedOrder();

        mockMvc.perform(post("/api/orders/ops/orders/{orderId}/transitions", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetStatus": "PAYMENT_PENDING"}
                                """))
                .andExpect(status().isUnauthorized());

        assertOrderUnchanged(orderId);
    }

    @Test
    void rejectsNoOpTransitionWithProblemDetailsWithoutChangingOrder() throws Exception {
        UUID orderId = persistCreatedOrder();

        mockMvc.perform(post("/api/orders/ops/orders/{orderId}/transitions", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer ops-admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetStatus": "CREATED"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Order transition is invalid"))
                .andExpect(jsonPath("$.failure").value("NO_OP_TRANSITION"))
                .andExpect(jsonPath("$.currentStatus").value("CREATED"))
                .andExpect(jsonPath("$.targetStatus").value("CREATED"));

        assertOrderUnchanged(orderId);
    }

    @Test
    void rejectsInvalidTransitionWithProblemDetailsWithoutChangingOrder() throws Exception {
        UUID orderId = persistCreatedOrder();

        mockMvc.perform(post("/api/orders/ops/orders/{orderId}/transitions", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer ops-admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetStatus": "PAID", "reason": "skip payment"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.failure").value("INVALID_TRANSITION"))
                .andExpect(jsonPath("$.currentStatus").value("CREATED"))
                .andExpect(jsonPath("$.targetStatus").value("PAID"));

        assertOrderUnchanged(orderId);
    }

    @Test
    void rejectsUnknownTargetStatusWithProblemDetailsWithoutChangingOrder() throws Exception {
        UUID orderId = persistCreatedOrder();

        mockMvc.perform(post("/api/orders/ops/orders/{orderId}/transitions", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer ops-admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetStatus": "REFUNDED"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.failure").value("UNKNOWN_TARGET_STATUS"))
                .andExpect(jsonPath("$.requestedStatus").value("REFUNDED"));

        assertOrderUnchanged(orderId);
    }

    private UUID persistCreatedOrder() {
        UUID orderId = UUID.randomUUID();
        OrderEntity order = OrderEntity.create(
                orderId,
                "customer-123",
                new BigDecimal("25.0000"),
                new BigDecimal("25.0000"),
                "USD",
                CREATED_AT);
        order.addItem(OrderItemEntity.snapshot(
                UUID.randomUUID(),
                1,
                UUID.randomUUID(),
                "SKU-001",
                "Snapshot Product",
                1,
                new BigDecimal("25.0000"),
                "USD",
                new BigDecimal("25.0000")));
        order.appendStatusHistory(OrderStatus.CREATED, CREATED_AT, "order created");
        return orderRepository.saveAndFlush(order).getId();
    }

    private void assertOrderUnchanged(UUID orderId) {
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(statusHistory(orderId))
                .extracting(OrderStatusHistoryEntity::getStatus)
                .containsExactly(OrderStatus.CREATED);
    }

    private List<OrderStatusHistoryEntity> statusHistory(UUID orderId) {
        return orderStatusHistoryRepository.findByOrder_IdOrderByChangedAtAsc(orderId);
    }

    @TestConfiguration
    static class TestSecurityConfiguration {

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> switch (token) {
                case "customer-token" -> jwtWithRealmRoles(token, "customer-123", "CUSTOMER");
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
