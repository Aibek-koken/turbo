package com.kora.ecommerce.order.catalog;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CatalogClientProperties.class)
class CatalogClientConfiguration {

    @Bean
    @ConditionalOnMissingBean(CatalogProductClient.class)
    CatalogProductClient catalogProductClient(
            RestClient.Builder restClientBuilder,
            CatalogClientProperties properties) {
        return new RestClientCatalogProductClient(restClientBuilder, properties);
    }
}
