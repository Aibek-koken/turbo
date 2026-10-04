package com.kora.ecommerce.order.config;

import com.kora.ecommerce.order.application.payment.PaymentResultEventException;
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
@EnableConfigurationProperties(OrderPaymentResultConsumerProperties.class)
class OrderKafkaConfiguration {

    @Bean
    DefaultErrorHandler orderPaymentResultErrorHandler(
            KafkaOperations<Object, Object> kafkaOperations,
            OrderPaymentResultConsumerProperties properties) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaOperations,
                (record, exception) -> paymentResultDeadLetterDestination(properties, record));
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(
                        properties.getRetry().getBackoff().toMillis(),
                        properties.getRetry().getMaxAttempts() - 1L));
        errorHandler.addNotRetryableExceptions(PaymentResultEventException.class);
        return errorHandler;
    }

    static TopicPartition paymentResultDeadLetterDestination(
            OrderPaymentResultConsumerProperties properties,
            ConsumerRecord<?, ?> record) {
        return new TopicPartition(properties.getRetry().getDeadLetterTopic(), record.partition());
    }
}
