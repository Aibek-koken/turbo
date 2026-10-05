package com.kora.ecommerce.order.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class OrderOperationalMetricsTest {

    @Test
    void recordsRetryAndDeadLetterCounters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OrderOperationalMetrics metrics = new OrderOperationalMetrics(registry);

        metrics.recordKafkaConsumerRetry("ecommerce.payment.events", "PaymentResult");
        metrics.recordKafkaConsumerDeadLetterPublication("ecommerce.payment.events", "PaymentResult");

        assertThat(counter(
                registry,
                OrderOperationalMetrics.KAFKA_CONSUMER_RETRIES,
                "topic", "ecommerce.payment.events",
                "event_type", "PaymentResult",
                "outcome", "retry")).isEqualTo(1.0);
        assertThat(counter(
                registry,
                OrderOperationalMetrics.KAFKA_CONSUMER_DLT_PUBLICATIONS,
                "topic", "ecommerce.payment.events",
                "event_type", "PaymentResult",
                "outcome", "attempted")).isEqualTo(1.0);
    }

    private static double counter(
            SimpleMeterRegistry registry,
            String name,
            String... tags) {
        return registry.get(name)
                .tag("service", "order-service")
                .tags(tags)
                .counter()
                .count();
    }
}
