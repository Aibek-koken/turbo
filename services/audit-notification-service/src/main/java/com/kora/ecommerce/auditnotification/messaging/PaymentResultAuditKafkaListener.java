package com.kora.ecommerce.auditnotification.messaging;

import com.kora.ecommerce.auditnotification.notification.EmailNotificationDispatcher;
import com.kora.ecommerce.auditnotification.notification.NotificationRoutingService;
import com.kora.ecommerce.auditnotification.notification.PushNotificationDispatcher;
import com.kora.ecommerce.auditnotification.observability.AuditNotificationOperationalMetrics;
import com.kora.ecommerce.auditnotification.payment.AuditPaymentResultEvent;
import com.kora.ecommerce.auditnotification.payment.AuditPaymentResultEventParser;
import com.kora.ecommerce.auditnotification.payment.InvalidPaymentResultEventException;
import com.kora.ecommerce.auditnotification.payment.PaymentResultEventMetadata;
import com.kora.ecommerce.auditnotification.persistence.AuditEventPersistenceResult;
import com.kora.ecommerce.auditnotification.persistence.AuditEventPersistenceService;
import com.kora.ecommerce.auditnotification.persistence.AuditEventSource;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "audit-notification.mongodb.repositories",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class PaymentResultAuditKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentResultAuditKafkaListener.class);
    private static final String PAYMENT_RESULT_EVENT_TYPE = "PaymentResult";

    private final AuditPaymentResultEventParser parser;
    private final AuditEventPersistenceService persistenceService;
    private final NotificationRoutingService notificationRoutingService;
    private final EmailNotificationDispatcher emailNotificationDispatcher;
    private final PushNotificationDispatcher pushNotificationDispatcher;
    private final KafkaEventProcessingObservation processingObservation;
    private final AuditNotificationOperationalMetrics metrics;

    public PaymentResultAuditKafkaListener(
            AuditPaymentResultEventParser parser,
            AuditEventPersistenceService persistenceService,
            NotificationRoutingService notificationRoutingService,
            EmailNotificationDispatcher emailNotificationDispatcher,
            PushNotificationDispatcher pushNotificationDispatcher,
            ObservationRegistry observationRegistry,
            AuditNotificationOperationalMetrics metrics) {
        this.parser = parser;
        this.persistenceService = persistenceService;
        this.notificationRoutingService = notificationRoutingService;
        this.emailNotificationDispatcher = emailNotificationDispatcher;
        this.pushNotificationDispatcher = pushNotificationDispatcher;
        this.processingObservation = new KafkaEventProcessingObservation(observationRegistry);
        this.metrics = metrics;
    }

    @KafkaListener(
            id = "audit-notification-payment-results",
            topics = "${audit-notification.kafka.payment-results.topic}",
            groupId = "${audit-notification.kafka.payment-results.group-id}",
            autoStartup = "${audit-notification.kafka.payment-results.enabled:true}",
            containerFactory = "auditPaymentResultKafkaListenerContainerFactory")
    public void onMessage(
            @Payload(required = false) String payload,
            @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String key,
            @Header(name = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
            @Header(name = KafkaHeaders.RECEIVED_PARTITION, required = false) Integer partition,
            @Header(name = KafkaHeaders.OFFSET, required = false) Long offset) {
        try {
            AuditPaymentResultEvent event = parser.parse(payload);
            processingObservation.observePaymentResult(event, () -> {
                try {
                    AuditEventPersistenceResult result = persistenceService.persistPaymentResult(
                            event,
                            new AuditEventSource(topic, partition, offset));
                    notificationRoutingService.routePaymentResult(event);
                    emailNotificationDispatcher.dispatchPendingEmailDeliveries();
                    pushNotificationDispatcher.dispatchPendingPushDeliveries();
                    metrics.recordKafkaConsumerEvent(topic, event.eventType(), result.outcome().name());
                    logResult(result, event, topic, partition, offset, key);
                } catch (RuntimeException exception) {
                    metrics.recordKafkaConsumerEvent(topic, event.eventType(), "failure");
                    throw exception;
                }
                return null;
            });
        } catch (InvalidPaymentResultEventException exception) {
            PaymentResultEventMetadata metadata = exception.metadata();
            metrics.recordKafkaConsumerEvent(topic, PAYMENT_RESULT_EVENT_TYPE, "rejected");
            log.warn(
                    "rejected_audit_payment_result_event reason={} eventId={} eventType={} eventVersion={} aggregateId={} topic={} partition={} offset={} key={}",
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
            AuditEventPersistenceResult result,
            AuditPaymentResultEvent event,
            String topic,
            Integer partition,
            Long offset,
            String key) {
        switch (result.outcome()) {
            case PERSISTED -> log.info(
                    "persisted_audit_payment_result_event eventId={} eventType={} orderId={} paymentId={} topic={} partition={} offset={} key={}",
                    result.eventId(),
                    event.eventType(),
                    result.orderId(),
                    event.paymentId(),
                    topic,
                    partition,
                    offset,
                    key);
            case DUPLICATE -> log.info(
                    "skipped_duplicate_audit_payment_result_event eventId={} eventType={} orderId={} paymentId={} topic={} partition={} offset={} key={}",
                    result.eventId(),
                    event.eventType(),
                    result.orderId(),
                    event.paymentId(),
                    topic,
                    partition,
                    offset,
                    key);
        }
    }
}
