package com.kora.ecommerce.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.web.reactive.server.WebTestClient;

import reactor.core.publisher.Mono;

@AutoConfigureWebTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewaySecurityConfigurationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void actuatorHealthIsPublic() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void apiRoutesRejectMissingBearerToken() {
        webTestClient.get()
                .uri("/api/catalog/products")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void apiRoutesRejectInvalidBearerToken() {
        webTestClient.get()
                .uri("/api/catalog/products")
                .headers(headers -> headers.setBearerAuth("invalid-token"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @TestConfiguration
    static class RejectingJwtDecoderConfiguration {

        @Bean
        ReactiveJwtDecoder reactiveJwtDecoder() {
            return token -> Mono.error(new JwtException("Test decoder rejects all bearer tokens."));
        }
    }
}
