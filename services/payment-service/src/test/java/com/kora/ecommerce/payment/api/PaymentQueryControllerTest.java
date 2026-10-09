package com.kora.ecommerce.payment.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.kora.ecommerce.payment.persistence.Payment;
import com.kora.ecommerce.payment.persistence.PaymentAttempt;
import com.kora.ecommerce.payment.persistence.PaymentAttemptRepository;
import com.kora.ecommerce.payment.persistence.PaymentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@SpringBootTest
@ActiveProfiles("test")
class PaymentQueryControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-08T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentAttemptRepository paymentAttemptRepository;

    @BeforeEach
    @AfterEach
    void cleanup() {
        paymentAttemptRepository.deleteAllInBatch();
        paymentRepository.deleteAllInBatch();
    }

    @Test
    void customerCanLookupOnlyOwnPaymentByOrder() throws Exception {
        UUID orderId = UUID.randomUUID();
        Payment payment = persistedSucceededPayment(orderId, "customer-123");

        mockMvc.perform(get("/api/payments/customer/payments/by-order/{orderId}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer customer-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(payment.getId().toString()))
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.customerId").value("customer-123"))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.attempts[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.attempts[0].providerReference").value("mock-provider-ref"));

        mockMvc.perform(get("/api/payments/customer/payments/by-order/{orderId}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer other-customer-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Payment lookup failed"))
                .andExpect(jsonPath("$.failure").value("PAYMENT_NOT_FOUND"))
                .andExpect(jsonPath("$.customerId").doesNotExist());
    }

    @Test
    void customerCannotUseOpsPaymentLookup() throws Exception {
        Payment payment = persistedSucceededPayment(UUID.randomUUID(), "customer-123");

        mockMvc.perform(get("/api/payments/ops/payments/{paymentId}", payment.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer customer-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void opsCanLookupPaymentByIdAndOrder() throws Exception {
        UUID orderId = UUID.randomUUID();
        Payment payment = persistedSucceededPayment(orderId, "customer-999");

        mockMvc.perform(get("/api/payments/ops/payments/{paymentId}", payment.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer ops-admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(payment.getId().toString()))
                .andExpect(jsonPath("$.orderId").value(orderId.toString()));

        mockMvc.perform(get("/api/payments/ops/payments/by-order/{orderId}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer ops-admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(payment.getId().toString()));
    }

    @Test
    void openApiJsonDocumentsExternalPaymentApisOnly() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Payment Service API"))
                .andExpect(jsonPath("$.components.securitySchemes['bearer-jwt'].scheme").value("bearer"))
                .andExpect(jsonPath("$.security[0]['bearer-jwt']").exists())
                .andExpect(jsonPath("$.paths['/api/payments/customer/payments/by-order/{orderId}'].get.operationId")
                        .value("getCustomerPaymentByOrder"))
                .andExpect(jsonPath("$.paths['/api/payments/ops/payments/{paymentId}'].get.operationId")
                        .value("getPaymentForOperations"))
                .andExpect(jsonPath("$.paths['/api/payments/customer/rbac']").doesNotExist())
                .andExpect(jsonPath("$.paths['/actuator/health']").doesNotExist());
    }

    private Payment persistedSucceededPayment(UUID orderId, String customerId) {
        UUID paymentId = UUID.randomUUID();
        Payment payment = Payment.pending(
                paymentId,
                orderId,
                customerId,
                new BigDecimal("25.0000"),
                "USD",
                UUID.randomUUID(),
                NOW);
        payment.markSucceeded("mock-provider-ref", NOW.plusSeconds(3));
        payment = paymentRepository.saveAndFlush(payment);

        PaymentAttempt attempt = PaymentAttempt.pending(
                UUID.randomUUID(),
                paymentId,
                1,
                new BigDecimal("25.0000"),
                "USD",
                UUID.randomUUID(),
                NOW.plusSeconds(1));
        attempt.markSucceeded("mock-provider-ref", NOW.plusSeconds(3));
        paymentAttemptRepository.saveAndFlush(attempt);

        assertThat(paymentAttemptRepository.countByPaymentId(paymentId)).isEqualTo(1);
        return payment;
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
