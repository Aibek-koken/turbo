package com.kora.ecommerce.gateway.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class GatewayRoutesConfigurationTest {

    @Autowired
    private RouteDefinitionLocator routeDefinitionLocator;

    @Test
    void routesCoverDownstreamServicesWithLocalDefaults() {
        Map<String, RouteDefinition> routes = routeDefinitionLocator.getRouteDefinitions()
                .collectMap(RouteDefinition::getId)
                .block(Duration.ofSeconds(5));

        assertThat(routes).isNotNull();
        assertThat(routes).containsOnlyKeys(
                "catalog-service",
                "order-service",
                "payment-service",
                "audit-notification-service");

        assertRoute(routes.get("catalog-service"), "http://localhost:8081", "/api/catalog/**");
        assertRoute(routes.get("order-service"), "http://localhost:8082", "/api/orders/**");
        assertRoute(routes.get("payment-service"), "http://localhost:8083", "/api/payments/**");
        assertRoute(routes.get("audit-notification-service"), "http://localhost:8084", "/api/audit-notifications/**");
    }

    private static void assertRoute(RouteDefinition route, String uri, String pathPattern) {
        assertThat(route).isNotNull();
        assertThat(route.getUri()).hasToString(uri);
        assertThat(route.getPredicates())
                .anySatisfy(predicate -> {
                    assertThat(predicate.getName()).isEqualTo("Path");
                    assertThat(predicate.getArgs()).containsValue(pathPattern);
                });
    }
}
