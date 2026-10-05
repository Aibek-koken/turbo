package com.kora.ecommerce.auditnotification.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.kora.ecommerce.auditnotification.notification.NotificationChannel;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class AuditNotificationOperationalMetricsTest {

    @Test
    void recordsRetryDeadLetterAuditAndNotificationCounters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AuditNotificationOperationalMetrics metrics = new AuditNotificationOperationalMetrics(registry);

        metrics.recordKafkaConsumerRetry("ecommerce.order.events", "OrderCreated");
        metrics.recordKafkaConsumerDeadLetterPublication("ecommerce.order.events", "OrderCreated");
        metrics.recordAuditPersistence("ecommerce.order.events", "OrderCreated", "persisted");
        metrics.recordNotificationDelivery(NotificationChannel.EMAIL, "OrderCreated", "sent");

        assertThat(counter(
                registry,
                AuditNotificationOperationalMetrics.KAFKA_CONSUMER_RETRIES,
                "topic", "ecommerce.order.events",
                "event_type", "OrderCreated",
                "outcome", "retry")).isEqualTo(1.0);
        assertThat(counter(
                registry,
                AuditNotificationOperationalMetrics.KAFKA_CONSUMER_DLT_PUBLICATIONS,
                "topic", "ecommerce.order.events",
                "event_type", "OrderCreated",
                "outcome", "attempted")).isEqualTo(1.0);
        assertThat(counter(
                registry,
                AuditNotificationOperationalMetrics.AUDIT_PERSISTENCE_EVENTS,
                "topic", "ecommerce.order.events",
                "event_type", "OrderCreated",
                "outcome", "persisted")).isEqualTo(1.0);
        assertThat(registry.get(AuditNotificationOperationalMetrics.NOTIFICATION_DELIVERIES)
                .tag("service", "audit-notification-service")
                .tag("channel", "email")
                .tag("event_type", "OrderCreated")
                .tag("outcome", "sent")
                .counter()
                .count()).isEqualTo(1.0);
    }

    private static double counter(
            SimpleMeterRegistry registry,
            String name,
            String... tags) {
        return registry.get(name)
                .tag("service", "audit-notification-service")
                .tags(tags)
                .counter()
                .count();
    }
}
