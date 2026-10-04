package com.kora.ecommerce.order.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@SpringBootTest
@ActiveProfiles("test")
class OrderSecurityConfigurationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void actuatorHealthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void customerProbeRejectsMissingBearerToken() throws Exception {
        mockMvc.perform(get("/api/orders/customer/rbac"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerProbeAllowsCustomerRealmRole() throws Exception {
        mockMvc.perform(get("/api/orders/customer/rbac")
                        .header("Authorization", "Bearer customer-token"))
                .andExpect(status().isOk());
    }

    @Test
    void opsProbeRejectsCustomerRealmRole() throws Exception {
        mockMvc.perform(get("/api/orders/ops/rbac")
                        .header("Authorization", "Bearer customer-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void opsProbeAllowsOpsRealmRole() throws Exception {
        mockMvc.perform(get("/api/orders/ops/rbac")
                        .header("Authorization", "Bearer ops-admin-token"))
                .andExpect(status().isOk());
    }

    @Test
    void unmappedApiPathIsDeniedEvenWithRealmRole() throws Exception {
        mockMvc.perform(get("/api/orders/unmapped")
                        .header("Authorization", "Bearer customer-token"))
                .andExpect(status().isForbidden());
    }

    @TestConfiguration
    static class JwtDecoderConfiguration {

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> switch (token) {
                case "customer-token" -> jwtWithRealmRoles(token, "CUSTOMER");
                case "ops-admin-token" -> jwtWithRealmRoles(token, "OPS_ADMIN");
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
