package com.kora.ecommerce.payment.config;

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

    @Bean
    DefaultErrorHandler paymentOrderCreatedErrorHandler(
            KafkaOperations<Object, Object> kafkaOperations,
            PaymentOrderCreatedConsumerProperties properties) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaOperations,
                (record, exception) -> orderCreatedDeadLetterDestination(properties, record));
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(
                        properties.getRetry().getBackoff().toMillis(),
                        properties.getRetry().getMaxAttempts() - 1L));
        errorHandler.addNotRetryableExceptions(InvalidOrderCreatedEventException.class);
        return errorHandler;
    }

    static TopicPartition orderCreatedDeadLetterDestination(
            PaymentOrderCreatedConsumerProperties properties,
            ConsumerRecord<?, ?> record) {
        return new TopicPartition(properties.getRetry().getDeadLetterTopic(), record.partition());
    }
}
