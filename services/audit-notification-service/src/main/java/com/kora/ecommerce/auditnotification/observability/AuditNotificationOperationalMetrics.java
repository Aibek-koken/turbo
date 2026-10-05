package com.kora.ecommerce.auditnotification.observability;

import java.util.Locale;

import com.kora.ecommerce.auditnotification.notification.NotificationChannel;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class AuditNotificationOperationalMetrics {

    public static final String KAFKA_CONSUMER_EVENTS = "ecommerce.kafka.consumer.events";
    public static final String KAFKA_CONSUMER_RETRIES = "ecommerce.kafka.consumer.retries";
    public static final String KAFKA_CONSUMER_DLT_PUBLICATIONS =
            "ecommerce.kafka.consumer.dead.letter.publications";
    public static final String AUDIT_PERSISTENCE_EVENTS = "ecommerce.audit.persistence.events";
    public static final String NOTIFICATION_DELIVERIES = "ecommerce.notification.deliveries";

    private static final String SERVICE = "audit-notification-service";
    private static final String KAFKA_EVENTS_DESCRIPTION =
            "Kafka consumer processing outcomes for bounded ecommerce event flows.";
    private static final String KAFKA_RETRIES_DESCRIPTION =
            "Kafka consumer retry attempts after listener processing failures.";
    private static final String KAFKA_DLT_DESCRIPTION =
            "Kafka consumer dead-letter publication attempts.";
    private static final String AUDIT_PERSISTENCE_DESCRIPTION =
            "Audit event persistence outcomes.";
    private static final String NOTIFICATION_DELIVERY_DESCRIPTION =
            "Mock notification delivery outcomes.";

    private final MeterRegistry meterRegistry;

    public AuditNotificationOperationalMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordKafkaConsumerEvent(String topic, String eventType, String outcome) {
        kafkaCounter(
                KAFKA_CONSUMER_EVENTS,
                KAFKA_EVENTS_DESCRIPTION,
                topic,
                eventType,
                normalize(outcome))
                .increment();
    }

    public void recordKafkaConsumerRetry(String topic, String eventType) {
        kafkaCounter(
                KAFKA_CONSUMER_RETRIES,
                KAFKA_RETRIES_DESCRIPTION,
                topic,
                eventType,
                "retry")
                .increment();
    }

    public void recordKafkaConsumerDeadLetterPublication(String topic, String eventType) {
        kafkaCounter(
                KAFKA_CONSUMER_DLT_PUBLICATIONS,
                KAFKA_DLT_DESCRIPTION,
                topic,
                eventType,
                "attempted")
                .increment();
    }

    public void recordAuditPersistence(String topic, String eventType, String outcome) {
        kafkaCounter(
                AUDIT_PERSISTENCE_EVENTS,
                AUDIT_PERSISTENCE_DESCRIPTION,
                topic,
                eventType,
                normalize(outcome))
                .increment();
    }

    public void recordNotificationDelivery(NotificationChannel channel, String eventType, String outcome) {
        Counter.builder(NOTIFICATION_DELIVERIES)
                .description(NOTIFICATION_DELIVERY_DESCRIPTION)
                .tag("service", SERVICE)
                .tag("channel", normalize(channel == null ? null : channel.name()))
                .tag("event_type", safeTag(eventType, "unknown"))
                .tag("outcome", normalize(outcome))
                .register(meterRegistry)
                .increment();
    }

    private Counter kafkaCounter(
            String name,
            String description,
            String topic,
            String eventType,
            String outcome) {
        return Counter.builder(name)
                .description(description)
                .tag("service", SERVICE)
                .tag("topic", safeTag(topic, "unknown"))
                .tag("event_type", safeTag(eventType, "unknown"))
                .tag("outcome", outcome)
                .register(meterRegistry);
    }

    private static String normalize(String value) {
        return safeTag(value, "unknown").toLowerCase(Locale.ROOT);
    }

    private static String safeTag(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
