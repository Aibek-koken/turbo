package com.kora.ecommerce.payment.provider;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment.provider.mock")
public class MockPaymentProviderProperties {

    private URI baseUrl = URI.create("http://localhost:8089");
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(3);
    private String authorizePath = "/api/mock-payments/authorize";

    public URI getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(URI baseUrl) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl is required");
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = requirePositiveDuration(connectTimeout, "connectTimeout");
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = requirePositiveDuration(readTimeout, "readTimeout");
    }

    public String getAuthorizePath() {
        return authorizePath;
    }

    public void setAuthorizePath(String authorizePath) {
        String normalized = requireText(authorizePath, "authorizePath");
        this.authorizePath = normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    private static Duration requirePositiveDuration(Duration value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " is required");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        if (value.toMillis() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(fieldName + " must fit in milliseconds");
        }
        return value;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}
