package com.kora.ecommerce.payment.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Duration;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DefaultErrorHandler;

class PaymentKafkaConsumerConfigurationTest {

    @Test
    void routesOrderCreatedFailuresToConfiguredDeadLetterTopicWithOriginalPartition() {
        PaymentOrderCreatedConsumerProperties properties = new PaymentOrderCreatedConsumerProperties();
        properties.getRetry().setDeadLetterTopic("ecommerce.order.events.poison");
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "ecommerce.order.events",
                2,
                42L,
                "order-key",
                "payload");

        TopicPartition destination = PaymentKafkaConsumerConfiguration.orderCreatedDeadLetterDestination(
                properties,
                record);

        assertThat(destination.topic()).isEqualTo("ecommerce.order.events.poison");
        assertThat(destination.partition()).isEqualTo(2);
    }

    @Test
    void retrySettingsAreBounded() {
        PaymentOrderCreatedConsumerProperties properties = new PaymentOrderCreatedConsumerProperties();

        assertThatThrownBy(() -> properties.getRetry().setMaxAttempts(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.getRetry().setMaxAttempts(11))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.getRetry().setBackoff(Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.getRetry().setBackoff(Duration.ofSeconds(61)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildsDefaultErrorHandlerForOrderCreatedConsumer() {
        PaymentOrderCreatedConsumerProperties properties = new PaymentOrderCreatedConsumerProperties();
        properties.getRetry().setMaxAttempts(2);
        properties.getRetry().setBackoff(Duration.ofMillis(50));
        KafkaOperations<Object, Object> kafkaOperations = mock(KafkaOperations.class);

        DefaultErrorHandler errorHandler = new PaymentKafkaConsumerConfiguration()
                .paymentOrderCreatedErrorHandler(kafkaOperations, properties);

        assertThat(errorHandler).isNotNull();
    }
}
