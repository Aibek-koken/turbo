package com.kora.ecommerce.order.observability;

import java.util.Locale;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class OrderOperationalMetrics {

    public static final String KAFKA_CONSUMER_EVENTS = "ecommerce.kafka.consumer.events";
    public static final String KAFKA_CONSUMER_RETRIES = "ecommerce.kafka.consumer.retries";
    public static final String KAFKA_CONSUMER_DLT_PUBLICATIONS =
            "ecommerce.kafka.consumer.dead.letter.publications";

    private static final String SERVICE = "order-service";
    private static final String KAFKA_EVENTS_DESCRIPTION =
            "Kafka consumer processing outcomes for bounded ecommerce event flows.";
    private static final String KAFKA_RETRIES_DESCRIPTION =
            "Kafka consumer retry attempts after listener processing failures.";
    private static final String KAFKA_DLT_DESCRIPTION =
            "Kafka consumer dead-letter publication attempts.";

    private final MeterRegistry meterRegistry;

    public OrderOperationalMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordKafkaConsumerEvent(String topic, String eventType, String outcome) {
        counter(
                KAFKA_CONSUMER_EVENTS,
                KAFKA_EVENTS_DESCRIPTION,
                topic,
                eventType,
                normalize(outcome))
                .increment();
    }

    public void recordKafkaConsumerRetry(String topic, String eventType) {
        counter(
                KAFKA_CONSUMER_RETRIES,
                KAFKA_RETRIES_DESCRIPTION,
                topic,
                eventType,
                "retry")
                .increment();
    }

    public void recordKafkaConsumerDeadLetterPublication(String topic, String eventType) {
        counter(
                KAFKA_CONSUMER_DLT_PUBLICATIONS,
                KAFKA_DLT_DESCRIPTION,
                topic,
                eventType,
                "attempted")
                .increment();
    }

    private Counter counter(
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
