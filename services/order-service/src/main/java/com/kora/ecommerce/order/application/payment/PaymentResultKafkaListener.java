package com.kora.ecommerce.order.application.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class PaymentResultKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentResultKafkaListener.class);

    private final PaymentResultEventParser paymentResultEventParser;
    private final PaymentResultOrderUpdater paymentResultOrderUpdater;

    PaymentResultKafkaListener(
            PaymentResultEventParser paymentResultEventParser,
            PaymentResultOrderUpdater paymentResultOrderUpdater) {
        this.paymentResultEventParser = paymentResultEventParser;
        this.paymentResultOrderUpdater = paymentResultOrderUpdater;
    }

    @KafkaListener(
            id = "order-payment-result-listener",
            topics = "${order.payment-results.topic:ecommerce.payment.events}",
            groupId = "${spring.kafka.consumer.group-id:order-service.payment-results.v1}",
            autoStartup = "${order.payment-results.consumer-enabled:true}")
    public void handle(
            @Payload String payload,
            @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String key,
            @Header(name = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
            @Header(name = KafkaHeaders.OFFSET, required = false) Long offset) {
        try {
            PaymentResultEnvelope event = paymentResultEventParser.parse(payload);
            PaymentResultHandlingResult result = paymentResultOrderUpdater.handle(event);
            logResult(result, topic, offset, key);
        } catch (PaymentResultEventException exception) {
            log.warn(
                    "Rejected payment result event: failure={} detail=\"{}\" topic={} offset={} key={}",
                    exception.failure(),
                    exception.getMessage(),
                    topic,
                    offset,
                    key);
            throw exception;
        }
    }

    private static void logResult(PaymentResultHandlingResult result, String topic, Long offset, String key) {
        switch (result.status()) {
            case PROCESSED, DUPLICATE -> log.info(
                    "Handled payment result event: status={} eventId={} orderId={} paymentId={} topic={} offset={} key={}",
                    result.status(),
                    result.eventId(),
                    result.orderId(),
                    result.paymentId(),
                    topic,
                    offset,
                    key);
            case MISSING_ORDER, ALREADY_TERMINAL, INVALID_TRANSITION -> log.warn(
                    "Skipped payment result event: status={} eventId={} orderId={} paymentId={} previousStatus={} currentStatus={} topic={} offset={} key={}",
                    result.status(),
                    result.eventId(),
                    result.orderId(),
                    result.paymentId(),
                    result.previousStatus(),
                    result.currentStatus(),
                    topic,
                    offset,
                    key);
        }
    }
}
