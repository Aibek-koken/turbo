package com.kora.ecommerce.auditnotification.config;

import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "audit-notification.kafka.payment-results")
public class AuditPaymentResultConsumerProperties {

    private String topic = "ecommerce.payment.events";
    private String groupId = "audit-notification-service.payment-results.v1";
    private boolean enabled = true;
    private Retry retry = new Retry();

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = requireText(topic, "topic");
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = requireText(groupId, "groupId");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Retry getRetry() {
        return retry;
    }

    public void setRetry(Retry retry) {
        this.retry = Objects.requireNonNull(retry, "retry is required");
    }

    static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    public static class Retry {

        private static final int MAX_CONFIGURED_ATTEMPTS = 10;
        private static final Duration MAX_BACKOFF = Duration.ofSeconds(60);

        private int maxAttempts = 3;
        private Duration backoff = Duration.ofSeconds(1);
        private String deadLetterTopic = "ecommerce.payment.events.audit.DLT";

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            if (maxAttempts < 1 || maxAttempts > MAX_CONFIGURED_ATTEMPTS) {
                throw new IllegalArgumentException("maxAttempts must be between 1 and 10");
            }
            this.maxAttempts = maxAttempts;
        }

        public Duration getBackoff() {
            return backoff;
        }

        public void setBackoff(Duration backoff) {
            Objects.requireNonNull(backoff, "backoff is required");
            if (backoff.isNegative() || backoff.compareTo(MAX_BACKOFF) > 0) {
                throw new IllegalArgumentException("backoff must be between PT0S and PT60S");
            }
            this.backoff = backoff;
        }

        public String getDeadLetterTopic() {
            return deadLetterTopic;
        }

        public void setDeadLetterTopic(String deadLetterTopic) {
            this.deadLetterTopic = requireText(deadLetterTopic, "deadLetterTopic");
        }
    }
}
