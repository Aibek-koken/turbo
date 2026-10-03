package com.kora.ecommerce.catalog.security;

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
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:catalog_security;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class CatalogSecurityConfigurationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void actuatorHealthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void customerProbeRejectsMissingBearerToken() throws Exception {
        mockMvc.perform(get("/api/catalog/rbac/customer"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerProbeAllowsCustomerRealmRole() throws Exception {
        mockMvc.perform(get("/api/catalog/rbac/customer")
                        .header("Authorization", "Bearer customer-token"))
                .andExpect(status().isOk());
    }

    @Test
    void adminProbeRejectsCustomerRealmRole() throws Exception {
        mockMvc.perform(get("/api/catalog/rbac/admin")
                        .header("Authorization", "Bearer customer-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminProbeAllowsCatalogAdminRealmRole() throws Exception {
        mockMvc.perform(get("/api/catalog/rbac/admin")
                        .header("Authorization", "Bearer catalog-admin-token"))
                .andExpect(status().isOk());
    }

    @Test
    void unmappedApiPathIsDeniedEvenWithRealmRole() throws Exception {
        mockMvc.perform(get("/api/catalog/unmapped")
                        .header("Authorization", "Bearer customer-token"))
                .andExpect(status().isForbidden());
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
