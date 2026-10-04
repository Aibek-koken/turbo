package com.kora.ecommerce.order.catalog;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "order.catalog")
public class CatalogClientProperties {

    private URI baseUrl = URI.create("http://localhost:8081");

    public URI getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(URI baseUrl) {
        this.baseUrl = baseUrl;
    }
}
