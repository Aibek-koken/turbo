package com.kora.ecommerce.order.application.payment;

import com.kora.ecommerce.order.observability.OrderOperationalMetrics;
import io.micrometer.observation.ObservationRegistry;
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
    private static final String PAYMENT_RESULT_EVENT_TYPE = "PaymentResult";

    private final PaymentResultEventParser paymentResultEventParser;
    private final PaymentResultOrderUpdater paymentResultOrderUpdater;
    private final KafkaEventProcessingObservation processingObservation;
    private final OrderOperationalMetrics metrics;

    PaymentResultKafkaListener(
            PaymentResultEventParser paymentResultEventParser,
            PaymentResultOrderUpdater paymentResultOrderUpdater,
            ObservationRegistry observationRegistry,
            OrderOperationalMetrics metrics) {
        this.paymentResultEventParser = paymentResultEventParser;
        this.paymentResultOrderUpdater = paymentResultOrderUpdater;
        this.processingObservation = new KafkaEventProcessingObservation(observationRegistry);
        this.metrics = metrics;
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
            processingObservation.observePaymentResult(event, () -> {
                try {
                    PaymentResultHandlingResult result = paymentResultOrderUpdater.handle(event);
                    metrics.recordKafkaConsumerEvent(topic, event.eventType(), result.status().name());
                    logResult(result, topic, offset, key);
                } catch (RuntimeException exception) {
                    metrics.recordKafkaConsumerEvent(topic, event.eventType(), "failure");
                    throw exception;
                }
                return null;
            });
        } catch (PaymentResultEventException exception) {
            metrics.recordKafkaConsumerEvent(topic, PAYMENT_RESULT_EVENT_TYPE, "rejected");
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
