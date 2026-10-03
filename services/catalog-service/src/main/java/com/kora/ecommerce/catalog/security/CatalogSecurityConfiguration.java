package com.kora.ecommerce.catalog.security;

import static com.kora.ecommerce.security.SecurityRoles.CATALOG_ADMIN;
import static com.kora.ecommerce.security.SecurityRoles.CUSTOMER;
import static com.kora.ecommerce.security.SecurityRoles.OPS_ADMIN;

import com.kora.ecommerce.security.EcommerceJwtAuthenticationConverters;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class CatalogSecurityConfiguration {

    @Bean
    SecurityFilterChain catalogSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/info",
                                "/actuator/prometheus")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/catalog/rbac/customer")
                        .hasAnyRole(CUSTOMER, CATALOG_ADMIN, OPS_ADMIN)
                        .requestMatchers(HttpMethod.GET, "/api/catalog/rbac/admin")
                        .hasRole(CATALOG_ADMIN)
                        .requestMatchers(HttpMethod.GET,
                                "/api/catalog/products",
                                "/api/catalog/products/**",
                                "/api/catalog/categories",
                                "/api/catalog/categories/**")
                        .hasAnyRole(CUSTOMER, CATALOG_ADMIN, OPS_ADMIN)
                        .requestMatchers("/api/catalog/admin/**")
                        .hasRole(CATALOG_ADMIN)
                        .anyRequest()
                        .denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(
                                EcommerceJwtAuthenticationConverters.keycloakRealmRoles())))
                .build();
    }
}
