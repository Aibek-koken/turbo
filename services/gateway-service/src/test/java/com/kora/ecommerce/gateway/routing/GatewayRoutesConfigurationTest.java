package com.kora.ecommerce.gateway.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

class GatewayRoutesConfigurationTest {

    @Test
    void routesCoverDownstreamServicesWithLocalDefaults() {
        Properties properties = gatewayApplicationProperties();

        assertRoute(
                properties,
                0,
                "catalog-service",
                "${ECOMMERCE_CATALOG_SERVICE_URI:http://localhost:8081}",
                "Path=/api/catalog/**");
        assertRoute(
                properties,
                1,
                "order-service",
                "${ECOMMERCE_ORDER_SERVICE_URI:http://localhost:8082}",
                "Path=/api/orders/**");
        assertRoute(
                properties,
                2,
                "payment-service",
                "${ECOMMERCE_PAYMENT_SERVICE_URI:http://localhost:8083}",
                "Path=/api/payments/**");
        assertRoute(
                properties,
                3,
                "audit-notification-service",
                "${ECOMMERCE_AUDIT_NOTIFICATION_SERVICE_URI:http://localhost:8084}",
                "Path=/api/audit-notifications/**");
    }

    private static Properties gatewayApplicationProperties() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        Properties properties = yaml.getObject();
        assertThat(properties).isNotNull();
        return properties;
    }

    private static void assertRoute(
            Properties properties,
            int index,
            String id,
            String uri,
            String pathPredicate) {
        String prefix = "spring.cloud.gateway.routes[" + index + "]";
        assertThat(properties.getProperty(prefix + ".id")).isEqualTo(id);
        assertThat(properties.getProperty(prefix + ".uri")).isEqualTo(uri);
        assertThat(properties.getProperty(prefix + ".predicates[0]")).isEqualTo(pathPredicate);
    }
}
