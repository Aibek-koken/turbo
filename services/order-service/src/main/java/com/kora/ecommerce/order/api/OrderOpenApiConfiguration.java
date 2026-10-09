package com.kora.ecommerce.order.api;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrderOpenApiConfiguration {

    public static final String BEARER_JWT = "bearer-jwt";

    @Bean
    OpenAPI orderOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Order Service API")
                        .version("v1")
                        .description("Customer order placement/query APIs and ops order support APIs. "
                                + "All business operations require a local Keycloak bearer JWT."))
                .components(new Components().addSecuritySchemes(BEARER_JWT, bearerJwtScheme()))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_JWT));
    }

    private static SecurityScheme bearerJwtScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("JWT issued by the local ecommerce Keycloak realm. "
                        + "Customer order paths require CUSTOMER; ops paths require OPS_ADMIN.");
    }
}
