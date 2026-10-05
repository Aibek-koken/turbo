package com.kora.ecommerce.auditnotification.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "audit-notification.notifications.email")
public class EmailNotificationProperties {

    private static final int MAX_CONFIGURED_ATTEMPTS = 10;
    private static final int MAX_CONFIGURED_BATCH_SIZE = 100;

    private boolean enabled = true;
    private int maxAttempts = 3;
    private int batchSize = 50;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        if (maxAttempts < 1 || maxAttempts > MAX_CONFIGURED_ATTEMPTS) {
            throw new IllegalArgumentException("maxAttempts must be between 1 and 10");
        }
        this.maxAttempts = maxAttempts;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        if (batchSize < 1 || batchSize > MAX_CONFIGURED_BATCH_SIZE) {
            throw new IllegalArgumentException("batchSize must be between 1 and 100");
        }
        this.batchSize = batchSize;
    }
}
