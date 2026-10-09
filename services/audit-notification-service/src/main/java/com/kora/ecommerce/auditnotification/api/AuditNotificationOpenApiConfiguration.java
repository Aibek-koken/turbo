package com.kora.ecommerce.auditnotification.api;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuditNotificationOpenApiConfiguration {

    public static final String BEARER_JWT = "bearer-jwt";

    @Bean
    OpenAPI auditNotificationOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Audit Notification Service API")
                        .version("v1")
                        .description("Customer notification-status APIs and ops audit-event APIs. "
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
                        + "Customer notification paths require CUSTOMER or OPS_ADMIN and are customer-scoped; "
                        + "ops audit paths require OPS_ADMIN.");
    }
}
