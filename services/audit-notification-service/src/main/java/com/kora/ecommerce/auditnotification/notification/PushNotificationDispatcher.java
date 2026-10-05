package com.kora.ecommerce.auditnotification.notification;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.kora.ecommerce.auditnotification.observability.AuditNotificationOperationalMetrics;
import com.kora.ecommerce.auditnotification.observability.EventLoggingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
        prefix = "audit-notification.mongodb.repositories",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class PushNotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationDispatcher.class);
    private static final String MAX_ATTEMPTS_EXHAUSTED = "push_max_attempts_exhausted";
    private static final String PAYLOAD_INVALID = "push_payload_invalid";
    private static final String ADAPTER_FAILURE = "push_adapter_failure";

    private final NotificationDeliveryRepository repository;
    private final PushNotificationPayloadFactory payloadFactory;
    private final PushNotificationPort pushPort;
    private final PushNotificationProperties properties;
    private final Clock clock;
    private final AuditNotificationOperationalMetrics metrics;

    public PushNotificationDispatcher(
            NotificationDeliveryRepository repository,
            PushNotificationPayloadFactory payloadFactory,
            PushNotificationPort pushPort,
            PushNotificationProperties properties,
            Clock clock,
            AuditNotificationOperationalMetrics metrics) {
        this.repository = repository;
        this.payloadFactory = payloadFactory;
        this.pushPort = pushPort;
        this.properties = properties;
        this.clock = clock;
        this.metrics = metrics;
    }

    public PushDeliveryDispatchResult dispatchPendingPushDeliveries() {
        if (!properties.isEnabled()) {
            return PushDeliveryDispatchResult.empty();
        }

        List<NotificationDeliveryDocument> pendingDeliveries = repository.findByChannelAndStatus(
                NotificationChannel.PUSH,
                NotificationDeliveryStatus.PENDING,
                PageRequest.of(
                        0,
                        properties.getBatchSize(),
                        Sort.by(Sort.Direction.ASC, "createdAt")));

        int attempted = 0;
        int sent = 0;
        int failed = 0;
        int skipped = 0;
        int exhausted = 0;
        for (NotificationDeliveryDocument delivery : pendingDeliveries) {
            DeliveryOutcome outcome = dispatchOne(delivery);
            recordDeliveryMetric(delivery, outcome);
            switch (outcome) {
                case SENT -> {
                    attempted++;
                    sent++;
                }
                case FAILED -> {
                    attempted++;
                    failed++;
                }
                case MAX_ATTEMPTS_EXHAUSTED -> {
                    failed++;
                    exhausted++;
                }
                case SKIPPED -> skipped++;
            }
        }

        return new PushDeliveryDispatchResult(
                pendingDeliveries.size(),
                attempted,
                sent,
                failed,
                skipped,
                exhausted);
    }

    private void recordDeliveryMetric(NotificationDeliveryDocument delivery, DeliveryOutcome outcome) {
        metrics.recordNotificationDelivery(NotificationChannel.PUSH, delivery.getEventType(), metricOutcome(outcome));
    }

    private static String metricOutcome(DeliveryOutcome outcome) {
        return switch (outcome) {
            case SENT -> "sent";
            case FAILED -> "failed";
            case MAX_ATTEMPTS_EXHAUSTED -> "exhausted";
            case SKIPPED -> "skipped";
        };
    }

    private DeliveryOutcome dispatchOne(NotificationDeliveryDocument delivery) {
        Objects.requireNonNull(delivery, "delivery must not be null");
        if (delivery.getChannel() != NotificationChannel.PUSH
                || delivery.getStatus() != NotificationDeliveryStatus.PENDING) {
            return DeliveryOutcome.SKIPPED;
        }
        try (EventLoggingContext ignored = EventLoggingContext.open(
                delivery.getEventId(),
                delivery.getEventType(),
                delivery.getEventVersion(),
                delivery.getOrderId(),
                delivery.getTraceId(),
                delivery.getCorrelationId())) {
            return dispatchPending(delivery);
        }
    }

    private DeliveryOutcome dispatchPending(NotificationDeliveryDocument delivery) {
        if (delivery.getAttemptCount() >= properties.getMaxAttempts()) {
            Instant exhaustedAt = Instant.now(clock);
            delivery.markFailed(MAX_ATTEMPTS_EXHAUSTED, exhaustedAt);
            repository.save(delivery);
            log.warn(
                    "push_notification_max_attempts_exhausted eventId={} eventType={} orderId={} paymentId={} attempts={} traceId={} correlationId={}",
                    delivery.getEventId(),
                    delivery.getEventType(),
                    delivery.getOrderId(),
                    delivery.getPaymentId(),
                    delivery.getAttemptCount(),
                    delivery.getTraceId(),
                    delivery.getCorrelationId());
            return DeliveryOutcome.MAX_ATTEMPTS_EXHAUSTED;
        }

        Instant attemptedAt = Instant.now(clock);
        delivery.markInProgress(attemptedAt);
        repository.save(delivery);

        try {
            PushNotificationPayload payload = payloadFactory.from(delivery);
            pushPort.send(payload);
        } catch (PushNotificationDeliveryException exception) {
            return failDelivery(delivery, exception.safeFailureDetail());
        } catch (IllegalArgumentException exception) {
            return failDelivery(delivery, PAYLOAD_INVALID);
        } catch (RuntimeException exception) {
            return failDelivery(delivery, ADAPTER_FAILURE);
        }

        Instant sentAt = Instant.now(clock);
        delivery.markSent(sentAt);
        repository.save(delivery);
        log.info(
                "push_notification_delivery_sent eventId={} eventType={} orderId={} paymentId={} attempts={} traceId={} correlationId={}",
                delivery.getEventId(),
                delivery.getEventType(),
                delivery.getOrderId(),
                delivery.getPaymentId(),
                delivery.getAttemptCount(),
                delivery.getTraceId(),
                delivery.getCorrelationId());
        return DeliveryOutcome.SENT;
    }

    private DeliveryOutcome failDelivery(
            NotificationDeliveryDocument delivery,
            String safeFailureDetail) {
        Instant failedAt = Instant.now(clock);
        delivery.markFailed(safeFailureDetail, failedAt);
        repository.save(delivery);
        log.warn(
                "push_notification_delivery_failed eventId={} eventType={} orderId={} paymentId={} attempts={} failure={} traceId={} correlationId={}",
                delivery.getEventId(),
                delivery.getEventType(),
                delivery.getOrderId(),
                delivery.getPaymentId(),
                delivery.getAttemptCount(),
                delivery.getSafeFailureDetail(),
                delivery.getTraceId(),
                delivery.getCorrelationId());
        return DeliveryOutcome.FAILED;
    }

    private enum DeliveryOutcome {
        SENT,
        FAILED,
        MAX_ATTEMPTS_EXHAUSTED,
        SKIPPED
    }
}
