package com.kora.ecommerce.order.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@SpringBootTest
@ActiveProfiles("test")
@Import(OrderQueryControllerTest.TestSecurityConfiguration.class)
class OrderQueryControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-03T18:00:00Z");
    private static final Instant OLDER_CREATED_AT = Instant.parse("2026-10-03T12:00:00Z");
    private static final Instant NEWER_CREATED_AT = Instant.parse("2026-10-03T13:00:00Z");

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
    }

    @Test
    void customerListsOnlyOwnedOrdersWithPaginationAndChronologicalHistory() throws Exception {
        UUID olderOwnedOrderId = persistOrder(
                "customer-123",
                OLDER_CREATED_AT,
                "10.0000",
                List.of(history(OrderStatus.CREATED, OLDER_CREATED_AT, "order created")));
        UUID newerOwnedOrderId = persistOrder(
                "customer-123",
                NEWER_CREATED_AT,
                "20.0000",
                List.of(
                        history(OrderStatus.CREATED, NEWER_CREATED_AT, "order created"),
                        history(OrderStatus.PAYMENT_PENDING, NEWER_CREATED_AT.plusSeconds(30), "send to payment")));
        UUID otherCustomerOrderId = persistOrder(
                "customer-999",
                NEWER_CREATED_AT.plusSeconds(60),
                "30.0000",
                List.of(history(OrderStatus.CREATED, NEWER_CREATED_AT.plusSeconds(60), "order created")));

        mockMvc.perform(get("/api/orders/customer/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer customer-token")
                        .queryParam("page", "0")
                        .queryParam("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.orders.length()").value(1))
                .andExpect(jsonPath("$.orders[0].orderId").value(newerOwnedOrderId.toString()))
                .andExpect(jsonPath("$.orders[0].customerId").value("customer-123"))
                .andExpect(jsonPath("$.orders[0].status").value("PAYMENT_PENDING"))
                .andExpect(jsonPath("$.orders[0].totalAmount").value(20.0000))
                .andExpect(jsonPath("$.orders[0].currency").value("USD"))
                .andExpect(jsonPath("$.orders[0].items[0].productSku").isNotEmpty())
                .andExpect(jsonPath("$.orders[0].items[0].lineTotalAmount").value(20.0000))
                .andExpect(jsonPath("$.orders[0].statusHistory[0].status").value("CREATED"))
                .andExpect(jsonPath("$.orders[0].statusHistory[1].status").value("PAYMENT_PENDING"))
                .andExpect(jsonPath("$.orders[0].statusHistory[0].changedAt").value(NEWER_CREATED_AT.toString()))
                .andExpect(jsonPath("$.orders[0].statusHistory[1].changedAt")
                        .value(NEWER_CREATED_AT.plusSeconds(30).toString()));

        assertThat(olderOwnedOrderId).isNotEqualTo(otherCustomerOrderId);
    }

    @Test
    void customerFetchesOwnedOrderDetailWithSnapshotsTotalsAndHistory() throws Exception {
        UUID orderId = persistOrder(
                "customer-123",
                OLDER_CREATED_AT,
                "15.0000",
                List.of(history(OrderStatus.CREATED, OLDER_CREATED_AT, "order created")));

        mockMvc.perform(get("/api/orders/customer/orders/{orderId}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer customer-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.customerId").value("customer-123"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.subtotalAmount").value(15.0000))
                .andExpect(jsonPath("$.totalAmount").value(15.0000))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.createdAt").value(OLDER_CREATED_AT.toString()))
                .andExpect(jsonPath("$.updatedAt").value(OLDER_CREATED_AT.toString()))
                .andExpect(jsonPath("$.items[0].itemNumber").value(1))
                .andExpect(jsonPath("$.items[0].productSku").isNotEmpty())
                .andExpect(jsonPath("$.items[0].productName").isNotEmpty())
                .andExpect(jsonPath("$.items[0].quantity").value(1))
                .andExpect(jsonPath("$.items[0].unitPriceAmount").value(15.0000))
                .andExpect(jsonPath("$.items[0].lineTotalAmount").value(15.0000))
                .andExpect(jsonPath("$.statusHistory[0].status").value("CREATED"))
                .andExpect(jsonPath("$.statusHistory[0].changedAt").value(OLDER_CREATED_AT.toString()))
                .andExpect(jsonPath("$.statusHistory[0].reason").value("order created"));
    }

    @Test
    void customerNonOwnedDetailUsesStableNotFoundProblemWithoutLeakingCustomerData() throws Exception {
        UUID otherCustomerOrderId = persistOrder(
                "customer-999",
                OLDER_CREATED_AT,
                "15.0000",
                List.of(history(OrderStatus.CREATED, OLDER_CREATED_AT, "order created")));

        mockMvc.perform(get("/api/orders/customer/orders/{orderId}", otherCustomerOrderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer customer-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Order lookup failed"))
                .andExpect(jsonPath("$.failure").value("ORDER_NOT_FOUND"))
                .andExpect(jsonPath("$.orderId").value(otherCustomerOrderId.toString()))
                .andExpect(jsonPath("$.customerId").doesNotExist());
    }

    @Test
    void opsAdminCanLookupAnyOrderByIdForSupport() throws Exception {
        UUID orderId = persistOrder(
                "customer-999",
                OLDER_CREATED_AT,
                "15.0000",
                List.of(history(OrderStatus.CREATED, OLDER_CREATED_AT, "order created")));

        mockMvc.perform(get("/api/orders/ops/orders/{orderId}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer ops-admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.customerId").value("customer-999"))
                .andExpect(jsonPath("$.items[0].lineTotalAmount").value(15.0000))
                .andExpect(jsonPath("$.statusHistory[0].status").value("CREATED"));
    }

    @Test
    void customerCannotUseOpsLookup() throws Exception {
        UUID orderId = persistOrder(
                "customer-123",
                OLDER_CREATED_AT,
                "15.0000",
                List.of(history(OrderStatus.CREATED, OLDER_CREATED_AT, "order created")));

        mockMvc.perform(get("/api/orders/ops/orders/{orderId}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer customer-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsOversizedCustomerPageWithProblemDetails() throws Exception {
        mockMvc.perform(get("/api/orders/customer/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer customer-token")
                        .queryParam("size", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Order lookup failed"))
                .andExpect(jsonPath("$.failure").value("PAGE_SIZE_TOO_LARGE"))
                .andExpect(jsonPath("$.maxAllowed").value(50));
    }

    private UUID persistOrder(
            String customerId,
            Instant createdAt,
            String totalAmount,
            List<HistorySeed> historySeeds) {
        UUID orderId = UUID.randomUUID();
        OrderEntity order = OrderEntity.create(
                orderId,
                customerId,
                new BigDecimal(totalAmount),
                new BigDecimal(totalAmount),
                "USD",
                createdAt);
        String suffix = orderId.toString().substring(0, 8);
        order.addItem(OrderItemEntity.snapshot(
                UUID.randomUUID(),
                1,
                UUID.randomUUID(),
                "SKU-" + suffix,
                "Snapshot Product " + suffix,
                1,
                new BigDecimal(totalAmount),
                "USD",
                new BigDecimal(totalAmount)));
        for (HistorySeed history : historySeeds) {
            if (history.status() == OrderStatus.CREATED) {
                order.appendStatusHistory(history.status(), history.changedAt(), history.reason());
            } else {
                order.transitionTo(history.status(), history.changedAt(), history.reason());
            }
        }
        return orderRepository.saveAndFlush(order).getId();
    }

    private static HistorySeed history(OrderStatus status, Instant changedAt, String reason) {
        return new HistorySeed(status, changedAt, reason);
    }

    private record HistorySeed(OrderStatus status, Instant changedAt, String reason) {
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
