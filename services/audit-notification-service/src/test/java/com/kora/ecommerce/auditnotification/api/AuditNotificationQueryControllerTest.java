package com.kora.ecommerce.auditnotification.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.kora.ecommerce.auditnotification.notification.NotificationChannel;
import com.kora.ecommerce.auditnotification.notification.NotificationDeliveryDocument;
import com.kora.ecommerce.auditnotification.notification.NotificationDeliveryRepository;
import com.kora.ecommerce.auditnotification.notification.NotificationDeliveryStatus;
import com.kora.ecommerce.auditnotification.persistence.AuditEventDocument;
import com.kora.ecommerce.auditnotification.persistence.AuditEventRepository;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "management.health.mongo.enabled=false",
        "audit-notification.mongodb.repositories.enabled=false",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration"
})
class AuditNotificationQueryControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-08T11:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuditEventRepository auditEventRepository;

    @MockBean
    private NotificationDeliveryRepository notificationDeliveryRepository;

    @Test
    void customerNotificationLookupIsScopedToJwtSubject() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(notificationDeliveryRepository.findByOrderIdAndCustomerId(orderId.toString(), "customer-123"))
                .thenReturn(List.of(notificationDelivery(orderId, "customer-123")));

        mockMvc.perform(get("/api/audit-notifications/customer/orders/{orderId}/notifications", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer customer-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventId").value("event-1"))
                .andExpect(jsonPath("$[0].orderId").value(orderId.toString()))
                .andExpect(jsonPath("$[0].customerId").value("customer-123"))
                .andExpect(jsonPath("$[0].channel").value("EMAIL"))
                .andExpect(jsonPath("$[0].status").value("SENT"));

        mockMvc.perform(get("/api/audit-notifications/customer/orders/{orderId}/notifications", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer other-customer-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void customerCannotUseOpsAuditLookup() throws Exception {
        mockMvc.perform(get("/api/audit-notifications/ops/audit-events/{eventId}", "event-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer customer-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void opsCanLookupAuditEventAndPagedOrderEvents() throws Exception {
        UUID orderId = UUID.randomUUID();
        AuditEventDocument document = auditEvent(orderId, "event-1");
        when(auditEventRepository.findByEventId("event-1")).thenReturn(Optional.of(document));
        when(auditEventRepository.findByOrderId(eq(orderId.toString()), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(document), Pageable.ofSize(1), 1));

        mockMvc.perform(get("/api/audit-notifications/ops/audit-events/{eventId}", "event-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer ops-admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("event-1"))
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.payload.totalAmount").value("25.0000"))
                .andExpect(jsonPath("$.sourceTopic").doesNotExist());

        mockMvc.perform(get("/api/audit-notifications/ops/orders/{orderId}/audit-events", orderId)
                        .queryParam("page", "0")
                        .queryParam("size", "1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer ops-admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.auditEvents[0].eventId").value("event-1"));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        org.mockito.Mockito.verify(auditEventRepository).findByOrderId(eq(orderId.toString()), pageableCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(1);
    }

    @Test
    void opsAuditEventPaginationRejectsOversizedPage() throws Exception {
        mockMvc.perform(get("/api/audit-notifications/ops/orders/{orderId}/audit-events", UUID.randomUUID())
                        .queryParam("size", "101")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer ops-admin-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Audit notification lookup failed"))
                .andExpect(jsonPath("$.failure").value("INVALID_PAGE_SIZE"))
                .andExpect(jsonPath("$.maxAllowed").value(100));
    }

    @Test
    void openApiJsonDocumentsExternalAuditNotificationApisOnly() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Audit Notification Service API"))
                .andExpect(jsonPath("$.components.securitySchemes['bearer-jwt'].scheme").value("bearer"))
                .andExpect(jsonPath("$.security[0]['bearer-jwt']").exists())
                .andExpect(jsonPath("$.paths['/api/audit-notifications/customer/orders/{orderId}/notifications']"
                        + ".get.operationId").value("listCustomerOrderNotifications"))
                .andExpect(jsonPath("$.paths['/api/audit-notifications/ops/audit-events/{eventId}']"
                        + ".get.operationId").value("getAuditEventForOperations"))
                .andExpect(jsonPath("$.paths['/api/audit-notifications/customer/rbac']").doesNotExist())
                .andExpect(jsonPath("$.paths['/actuator/health']").doesNotExist());
    }

    private static NotificationDeliveryDocument notificationDelivery(UUID orderId, String customerId) {
        return new NotificationDeliveryDocument(
                "event-1",
                NotificationChannel.EMAIL,
                "PaymentSucceeded",
                1,
                orderId.toString(),
                customerId,
                UUID.randomUUID().toString(),
                NotificationDeliveryStatus.SENT,
                1,
                NOW.plusSeconds(2),
                null,
                NOW,
                NOW.plusSeconds(1),
                NOW.plusSeconds(2),
                "trace-1",
                "correlation-1");
    }

    private static AuditEventDocument auditEvent(UUID orderId, String eventId) {
        return new AuditEventDocument(
                eventId,
                "PaymentSucceeded",
                1,
                orderId.toString(),
                orderId.toString(),
                "customer-123",
                UUID.randomUUID().toString(),
                NOW,
                NOW.plusSeconds(1),
                "trace-1",
                "correlation-1",
                "ecommerce.payment.events",
                0,
                42,
                new Document("totalAmount", "25.0000"));
    }

    @TestConfiguration
    static class JwtDecoderConfiguration {

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> switch (token) {
                case "customer-token" -> jwtWithRealmRoles(token, "customer-123", "CUSTOMER");
                case "other-customer-token" -> jwtWithRealmRoles(token, "customer-456", "CUSTOMER");
                case "ops-admin-token" -> jwtWithRealmRoles(token, "ops-admin-123", "OPS_ADMIN");
                default -> throw new JwtException("Test decoder rejects unknown bearer tokens.");
            };
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
