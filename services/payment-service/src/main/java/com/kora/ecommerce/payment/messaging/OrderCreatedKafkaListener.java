package com.kora.ecommerce.payment.messaging;

import com.kora.ecommerce.payment.application.OrderCreatedPaymentResult;
import com.kora.ecommerce.payment.application.OrderCreatedPaymentService;
import com.kora.ecommerce.payment.order.InvalidOrderCreatedEventException;
import com.kora.ecommerce.payment.order.OrderCreatedEvent;
import com.kora.ecommerce.payment.order.OrderCreatedEventMetadata;
import com.kora.ecommerce.payment.order.OrderCreatedEventParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class OrderCreatedKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedKafkaListener.class);

    private final OrderCreatedEventParser parser;
    private final OrderCreatedPaymentService paymentService;

    public OrderCreatedKafkaListener(
            OrderCreatedEventParser parser,
            OrderCreatedPaymentService paymentService) {
        this.parser = parser;
        this.paymentService = paymentService;
    }

    @KafkaListener(
            id = "payment-service-order-created",
            topics = "${payment.kafka.order-created.topic}",
            groupId = "${payment.kafka.order-created.group-id}",
            autoStartup = "${payment.kafka.order-created.enabled:true}")
    public void onMessage(
            @Payload(required = false) String payload,
            @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String key,
            @Header(name = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
            @Header(name = KafkaHeaders.RECEIVED_PARTITION, required = false) Integer partition,
            @Header(name = KafkaHeaders.OFFSET, required = false) Long offset) {
        try {
            OrderCreatedEvent event = parser.parse(payload);
            OrderCreatedPaymentResult result = paymentService.processOrderCreated(event);
            logResult(result, topic, partition, offset, key);
            if (result.outcome() == OrderCreatedPaymentResult.Outcome.PROVIDER_RETRYABLE_FAILURE) {
                throw new RetryableOrderCreatedEventException(result);
            }
        } catch (InvalidOrderCreatedEventException exception) {
            OrderCreatedEventMetadata metadata = exception.metadata();
            log.warn(
                    "rejected_order_created_event reason={} eventId={} eventType={} eventVersion={} aggregateId={} topic={} partition={} offset={} key={}",
                    exception.reason(),
                    metadata.eventId(),
                    metadata.eventType(),
                    metadata.eventVersion(),
                    metadata.aggregateId(),
                    topic,
                    partition,
                    offset,
                    key);
            throw exception;
        }
    }

    private void logResult(
            OrderCreatedPaymentResult result,
            String topic,
            Integer partition,
            Long offset,
            String key) {
        switch (result.outcome()) {
            case PROVIDER_APPROVED -> log.info(
                    "payment_provider_approved_order_created eventId={} orderId={} paymentId={} attemptId={} topic={} partition={} offset={} key={}",
                    result.eventId(),
                    result.orderId(),
                    result.paymentId(),
                    result.attemptId(),
                    topic,
                    partition,
                    offset,
                    key);
            case PROVIDER_DECLINED -> log.info(
                    "payment_provider_declined_order_created eventId={} orderId={} paymentId={} attemptId={} topic={} partition={} offset={} key={}",
                    result.eventId(),
                    result.orderId(),
                    result.paymentId(),
                    result.attemptId(),
                    topic,
                    partition,
                    offset,
                    key);
            case PROVIDER_RETRYABLE_FAILURE -> log.warn(
                    "payment_provider_retryable_failure eventId={} orderId={} paymentId={} attemptId={} attemptOutcome={} topic={} partition={} offset={} key={}",
                    result.eventId(),
                    result.orderId(),
                    result.paymentId(),
                    result.attemptId(),
                    result.attemptOutcome(),
                    topic,
                    partition,
                    offset,
                    key);
            case PROVIDER_MALFORMED_RESPONSE -> log.warn(
                    "payment_provider_malformed_response eventId={} orderId={} paymentId={} attemptId={} topic={} partition={} offset={} key={}",
                    result.eventId(),
                    result.orderId(),
                    result.paymentId(),
                    result.attemptId(),
                    topic,
                    partition,
                    offset,
                    key);
            case DUPLICATE_EVENT -> log.info(
                    "skipped_duplicate_order_created_event eventId={} orderId={} topic={} partition={} offset={} key={}",
                    result.eventId(),
                    result.orderId(),
                    topic,
                    partition,
                    offset,
                    key);
            case PAYMENT_ALREADY_EXISTS -> log.info(
                    "skipped_order_created_payment_already_exists eventId={} orderId={} paymentId={} topic={} partition={} offset={} key={}",
                    result.eventId(),
                    result.orderId(),
                    result.paymentId(),
                    topic,
                    partition,
                    offset,
                    key);
        }
    }
}
