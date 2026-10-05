package com.kora.ecommerce.auditnotification.observability;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "audit-notification.observability.dlt-depth")
public class KafkaDeadLetterTopicDepthProperties {

    private static final int MAX_CONFIGURED_TOPICS = 10;
    private static final Duration MAX_REFRESH_INTERVAL = Duration.ofMinutes(5);
    private static final Duration MAX_TIMEOUT = Duration.ofSeconds(10);

    private boolean enabled = true;
    private Duration refreshInterval = Duration.ofSeconds(30);
    private Duration timeout = Duration.ofSeconds(2);
    private List<Topic> topics = List.of(
            new Topic("payment-service", "ecommerce.order.events.DLT"),
            new Topic("order-service", "ecommerce.payment.events.DLT"),
            new Topic("audit-notification-service", "ecommerce.order.events.audit.DLT"),
            new Topic("audit-notification-service", "ecommerce.payment.events.audit.DLT"));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getRefreshInterval() {
        return refreshInterval;
    }

    public void setRefreshInterval(Duration refreshInterval) {
        Objects.requireNonNull(refreshInterval, "refreshInterval is required");
        if (refreshInterval.isNegative() || refreshInterval.compareTo(MAX_REFRESH_INTERVAL) > 0) {
            throw new IllegalArgumentException("refreshInterval must be between PT0S and PT5M");
        }
        this.refreshInterval = refreshInterval;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout is required");
        if (timeout.isZero() || timeout.isNegative() || timeout.compareTo(MAX_TIMEOUT) > 0) {
            throw new IllegalArgumentException("timeout must be between PT0.001S and PT10S");
        }
        this.timeout = timeout;
    }

    public List<Topic> getTopics() {
        return topics;
    }

    public void setTopics(List<Topic> topics) {
        Objects.requireNonNull(topics, "topics are required");
        if (topics.isEmpty() || topics.size() > MAX_CONFIGURED_TOPICS) {
            throw new IllegalArgumentException("topics must contain between 1 and 10 fixed entries");
        }
        Set<String> names = new LinkedHashSet<>();
        List<Topic> validatedTopics = topics.stream()
                .map(Topic::validatedCopy)
                .toList();
        for (Topic topic : validatedTopics) {
            if (!names.add(topic.getTopic())) {
                throw new IllegalArgumentException("DLT topic names must be unique");
            }
        }
        this.topics = validatedTopics;
    }

    public static class Topic {

        private String service = "audit-notification-service";
        private String topic = "ecommerce.audit.events.DLT";

        public Topic() {
        }

        Topic(String service, String topic) {
            setService(service);
            setTopic(topic);
        }

        public String getService() {
            return service;
        }

        public void setService(String service) {
            this.service = requireText(service, "service");
        }

        public String getTopic() {
            return topic;
        }

        public void setTopic(String topic) {
            this.topic = requireText(topic, "topic");
        }

        static Topic validatedCopy(Topic topic) {
            Objects.requireNonNull(topic, "topic entry is required");
            return new Topic(topic.getService(), topic.getTopic());
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}
