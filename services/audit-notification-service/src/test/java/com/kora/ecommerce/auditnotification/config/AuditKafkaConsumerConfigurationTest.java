package com.kora.ecommerce.auditnotification.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Duration;

import com.kora.ecommerce.auditnotification.observability.AuditNotificationOperationalMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DefaultErrorHandler;

class AuditKafkaConsumerConfigurationTest {

    @Test
    void routesOrderCreatedFailuresToConfiguredDeadLetterTopicWithOriginalPartition() {
        AuditOrderCreatedConsumerProperties properties = new AuditOrderCreatedConsumerProperties();
        properties.getRetry().setDeadLetterTopic("ecommerce.order.events.audit.poison");
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "ecommerce.order.events",
                2,
                42L,
                "order-key",
                "payload");

        TopicPartition destination = AuditKafkaConsumerConfiguration.orderCreatedDeadLetterDestination(
                properties,
                record);

        assertThat(destination.topic()).isEqualTo("ecommerce.order.events.audit.poison");
        assertThat(destination.partition()).isEqualTo(2);
    }

    @Test
    void routesPaymentResultFailuresToConfiguredDeadLetterTopicWithOriginalPartition() {
        AuditPaymentResultConsumerProperties properties = new AuditPaymentResultConsumerProperties();
        properties.getRetry().setDeadLetterTopic("ecommerce.payment.events.audit.poison");
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "ecommerce.payment.events",
                3,
                43L,
                "payment-key",
                "payload");

        TopicPartition destination = AuditKafkaConsumerConfiguration.paymentResultDeadLetterDestination(
                properties,
                record);

        assertThat(destination.topic()).isEqualTo("ecommerce.payment.events.audit.poison");
        assertThat(destination.partition()).isEqualTo(3);
    }

    @Test
    void retrySettingsAreBounded() {
        AuditOrderCreatedConsumerProperties properties = new AuditOrderCreatedConsumerProperties();
        AuditPaymentResultConsumerProperties paymentProperties = new AuditPaymentResultConsumerProperties();

        assertThatThrownBy(() -> properties.getRetry().setMaxAttempts(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.getRetry().setMaxAttempts(11))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.getRetry().setBackoff(Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.getRetry().setBackoff(Duration.ofSeconds(61)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> paymentProperties.getRetry().setMaxAttempts(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> paymentProperties.getRetry().setBackoff(Duration.ofSeconds(61)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildsDefaultErrorHandlerForOrderCreatedConsumer() {
        AuditOrderCreatedConsumerProperties properties = new AuditOrderCreatedConsumerProperties();
        properties.getRetry().setMaxAttempts(2);
        properties.getRetry().setBackoff(Duration.ofMillis(50));
        KafkaOperations<Object, Object> kafkaOperations = mock(KafkaOperations.class);

        DefaultErrorHandler errorHandler = new AuditKafkaConsumerConfiguration()
                .auditOrderCreatedErrorHandler(
                        kafkaOperations,
                        properties,
                        new AuditNotificationOperationalMetrics(new SimpleMeterRegistry()));

        assertThat(errorHandler).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildsDefaultErrorHandlerForPaymentResultConsumer() {
        AuditPaymentResultConsumerProperties properties = new AuditPaymentResultConsumerProperties();
        properties.getRetry().setMaxAttempts(2);
        properties.getRetry().setBackoff(Duration.ofMillis(50));
        KafkaOperations<Object, Object> kafkaOperations = mock(KafkaOperations.class);

        DefaultErrorHandler errorHandler = new AuditKafkaConsumerConfiguration()
                .auditPaymentResultErrorHandler(
                        kafkaOperations,
                        properties,
                        new AuditNotificationOperationalMetrics(new SimpleMeterRegistry()));

        assertThat(errorHandler).isNotNull();
    }
}
