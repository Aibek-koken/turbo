package com.kora.ecommerce.order.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@SpringBootTest
@ActiveProfiles("test")
class OrderOpenApiDocumentationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void openApiJsonDocumentsExternalOrderApisOnly() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Order Service API"))
                .andExpect(jsonPath("$.components.securitySchemes['bearer-jwt'].scheme").value("bearer"))
                .andExpect(jsonPath("$.security[0]['bearer-jwt']").exists())
                .andExpect(jsonPath("$.paths['/api/orders/customer/orders'].get.operationId")
                        .value("listCustomerOrders"))
                .andExpect(jsonPath("$.paths['/api/orders/customer/orders'].post.operationId")
                        .value("createCustomerOrder"))
                .andExpect(jsonPath("$.paths['/api/orders/ops/orders/{orderId}/transitions'].post.operationId")
                        .value("transitionOrderForOperations"))
                .andExpect(jsonPath("$.paths['/api/orders/customer/rbac']").doesNotExist())
                .andExpect(jsonPath("$.paths['/actuator/health']").doesNotExist());
    }

    @TestConfiguration
    static class JwtDecoderConfiguration {

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                throw new JwtException("OpenAPI documentation test does not decode bearer tokens.");
            };
        }
    }
}
