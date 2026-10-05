package com.kora.ecommerce.order.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Duration;

import com.kora.ecommerce.order.observability.OrderOperationalMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DefaultErrorHandler;

class OrderKafkaConfigurationTest {

    @Test
    void routesPaymentResultFailuresToConfiguredDeadLetterTopicWithOriginalPartition() {
        OrderPaymentResultConsumerProperties properties = new OrderPaymentResultConsumerProperties();
        properties.getRetry().setDeadLetterTopic("ecommerce.payment.events.poison");
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "ecommerce.payment.events",
                1,
                24L,
                "order-key",
                "payload");

        TopicPartition destination = OrderKafkaConfiguration.paymentResultDeadLetterDestination(
                properties,
                record);

        assertThat(destination.topic()).isEqualTo("ecommerce.payment.events.poison");
        assertThat(destination.partition()).isEqualTo(1);
    }

    @Test
    void retrySettingsAreBounded() {
        OrderPaymentResultConsumerProperties properties = new OrderPaymentResultConsumerProperties();

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
    void buildsDefaultErrorHandlerForPaymentResultConsumer() {
        OrderPaymentResultConsumerProperties properties = new OrderPaymentResultConsumerProperties();
        properties.getRetry().setMaxAttempts(2);
        properties.getRetry().setBackoff(Duration.ofMillis(50));
        KafkaOperations<Object, Object> kafkaOperations = mock(KafkaOperations.class);

        DefaultErrorHandler errorHandler = new OrderKafkaConfiguration()
                .orderPaymentResultErrorHandler(
                        kafkaOperations,
                        properties,
                        new OrderOperationalMetrics(new SimpleMeterRegistry()));

        assertThat(errorHandler).isNotNull();
    }
}
