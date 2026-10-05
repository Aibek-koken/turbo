package com.kora.ecommerce.payment.config;

import com.kora.ecommerce.payment.observability.PaymentOperationalMetrics;
import com.kora.ecommerce.payment.order.InvalidOrderCreatedEventException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration(proxyBeanMethods = false)
@EnableKafka
@EnableConfigurationProperties(PaymentOrderCreatedConsumerProperties.class)
public class PaymentKafkaConsumerConfiguration {

    private static final String ORDER_CREATED_EVENT_TYPE = "OrderCreated";

    @Bean
    DefaultErrorHandler paymentOrderCreatedErrorHandler(
            KafkaOperations<Object, Object> kafkaOperations,
            PaymentOrderCreatedConsumerProperties properties,
            PaymentOperationalMetrics metrics) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaOperations,
                (record, exception) -> {
                    metrics.recordKafkaConsumerDeadLetterPublication(record.topic(), ORDER_CREATED_EVENT_TYPE);
                    return orderCreatedDeadLetterDestination(properties, record);
                });
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(
                        properties.getRetry().getBackoff().toMillis(),
                        properties.getRetry().getMaxAttempts() - 1L));
        errorHandler.addNotRetryableExceptions(InvalidOrderCreatedEventException.class);
        errorHandler.setRetryListeners((record, exception, deliveryAttempt) -> {
            if (deliveryAttempt < properties.getRetry().getMaxAttempts()
                    && !hasCause(exception, InvalidOrderCreatedEventException.class)) {
                metrics.recordKafkaConsumerRetry(record.topic(), ORDER_CREATED_EVENT_TYPE);
            }
        });
        return errorHandler;
    }

    static TopicPartition orderCreatedDeadLetterDestination(
            PaymentOrderCreatedConsumerProperties properties,
            ConsumerRecord<?, ?> record) {
        return new TopicPartition(properties.getRetry().getDeadLetterTopic(), record.partition());
    }

    private static boolean hasCause(Throwable exception, Class<? extends Throwable> type) {
        Throwable current = exception;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
