package com.kora.ecommerce.auditnotification.config;

import com.kora.ecommerce.auditnotification.order.InvalidOrderCreatedEventException;
import com.kora.ecommerce.auditnotification.observability.AuditNotificationOperationalMetrics;
import com.kora.ecommerce.auditnotification.payment.InvalidPaymentResultEventException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration(proxyBeanMethods = false)
@EnableKafka
@EnableConfigurationProperties({
        AuditOrderCreatedConsumerProperties.class,
        AuditPaymentResultConsumerProperties.class
})
public class AuditKafkaConsumerConfiguration {

    private static final String ORDER_CREATED_EVENT_TYPE = "OrderCreated";
    private static final String PAYMENT_RESULT_EVENT_TYPE = "PaymentResult";

    @Bean(name = "auditOrderCreatedKafkaListenerContainerFactory")
    ConcurrentKafkaListenerContainerFactory<Object, Object> auditOrderCreatedKafkaListenerContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<Object, Object> consumerFactory,
            KafkaOperations<Object, Object> kafkaOperations,
            AuditOrderCreatedConsumerProperties properties,
            AuditNotificationOperationalMetrics metrics) {
        return listenerContainerFactory(
                configurer,
                consumerFactory,
                auditOrderCreatedErrorHandler(kafkaOperations, properties, metrics));
    }

    @Bean(name = "auditPaymentResultKafkaListenerContainerFactory")
    ConcurrentKafkaListenerContainerFactory<Object, Object> auditPaymentResultKafkaListenerContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<Object, Object> consumerFactory,
            KafkaOperations<Object, Object> kafkaOperations,
            AuditPaymentResultConsumerProperties properties,
            AuditNotificationOperationalMetrics metrics) {
        return listenerContainerFactory(
                configurer,
                consumerFactory,
                auditPaymentResultErrorHandler(kafkaOperations, properties, metrics));
    }

    DefaultErrorHandler auditOrderCreatedErrorHandler(
            KafkaOperations<Object, Object> kafkaOperations,
            AuditOrderCreatedConsumerProperties properties,
            AuditNotificationOperationalMetrics metrics) {
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

    DefaultErrorHandler auditPaymentResultErrorHandler(
            KafkaOperations<Object, Object> kafkaOperations,
            AuditPaymentResultConsumerProperties properties,
            AuditNotificationOperationalMetrics metrics) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaOperations,
                (record, exception) -> {
                    metrics.recordKafkaConsumerDeadLetterPublication(record.topic(), PAYMENT_RESULT_EVENT_TYPE);
                    return paymentResultDeadLetterDestination(properties, record);
                });
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(
                        properties.getRetry().getBackoff().toMillis(),
                        properties.getRetry().getMaxAttempts() - 1L));
        errorHandler.addNotRetryableExceptions(InvalidPaymentResultEventException.class);
        errorHandler.setRetryListeners((record, exception, deliveryAttempt) -> {
            if (deliveryAttempt < properties.getRetry().getMaxAttempts()
                    && !hasCause(exception, InvalidPaymentResultEventException.class)) {
                metrics.recordKafkaConsumerRetry(record.topic(), PAYMENT_RESULT_EVENT_TYPE);
            }
        });
        return errorHandler;
    }

    static TopicPartition orderCreatedDeadLetterDestination(
            AuditOrderCreatedConsumerProperties properties,
            ConsumerRecord<?, ?> record) {
        return new TopicPartition(properties.getRetry().getDeadLetterTopic(), record.partition());
    }

    static TopicPartition paymentResultDeadLetterDestination(
            AuditPaymentResultConsumerProperties properties,
            ConsumerRecord<?, ?> record) {
        return new TopicPartition(properties.getRetry().getDeadLetterTopic(), record.partition());
    }

    private ConcurrentKafkaListenerContainerFactory<Object, Object> listenerContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<Object, Object> consumerFactory,
            DefaultErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        configurer.configure(factory, consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        factory.getContainerProperties().setObservationEnabled(true);
        return factory;
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
