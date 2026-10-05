package com.kora.ecommerce.payment.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class PaymentOperationalMetricsTest {

    @Test
    void recordsRetryDeadLetterAndTerminalOutcomeCounters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PaymentOperationalMetrics metrics = new PaymentOperationalMetrics(registry);

        metrics.recordKafkaConsumerRetry("ecommerce.order.events", "OrderCreated");
        metrics.recordKafkaConsumerDeadLetterPublication("ecommerce.order.events", "OrderCreated");
        metrics.recordTerminalPaymentOutcome("PaymentFailed", "failed");

        assertThat(counter(
                registry,
                PaymentOperationalMetrics.KAFKA_CONSUMER_RETRIES,
                "topic", "ecommerce.order.events",
                "event_type", "OrderCreated",
                "outcome", "retry")).isEqualTo(1.0);
        assertThat(counter(
                registry,
                PaymentOperationalMetrics.KAFKA_CONSUMER_DLT_PUBLICATIONS,
                "topic", "ecommerce.order.events",
                "event_type", "OrderCreated",
                "outcome", "attempted")).isEqualTo(1.0);
        assertThat(counter(
                registry,
                PaymentOperationalMetrics.PAYMENT_TERMINAL_OUTCOMES,
                "event_type", "PaymentFailed",
                "outcome", "failed")).isEqualTo(1.0);
    }

    private static double counter(
            SimpleMeterRegistry registry,
            String name,
            String... tags) {
        return registry.get(name)
                .tag("service", "payment-service")
                .tags(tags)
                .counter()
                .count();
    }
}
