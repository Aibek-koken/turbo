package com.kora.ecommerce.auditnotification.persistence;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import com.kora.ecommerce.auditnotification.order.AuditOrderCreatedEvent;
import com.kora.ecommerce.auditnotification.observability.AuditNotificationOperationalMetrics;
import com.kora.ecommerce.auditnotification.payment.AuditPaymentResultEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
        prefix = "audit-notification.mongodb.repositories",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class AuditEventPersistenceService {

    private final AuditEventRepository repository;
    private final Clock clock;
    private final AuditNotificationOperationalMetrics metrics;

    public AuditEventPersistenceService(
            AuditEventRepository repository,
            Clock clock,
            AuditNotificationOperationalMetrics metrics) {
        this.repository = repository;
        this.clock = clock;
        this.metrics = metrics;
    }

    public AuditEventPersistenceResult persistOrderCreated(
            AuditOrderCreatedEvent event,
            AuditEventSource source) {
        Objects.requireNonNull(event, "event is required");
        Objects.requireNonNull(source, "source is required");

        AuditEventDocument document = new AuditEventDocument(
                event.eventId().toString(),
                event.eventType(),
                event.eventVersion(),
                event.aggregateId().toString(),
                event.orderId().toString(),
                event.customerId(),
                null,
                event.occurredAt(),
                Instant.now(clock),
                event.traceId(),
                event.correlationId(),
                source.topic(),
                source.partition(),
                source.offset(),
                event.payload());

        try {
            repository.save(document);
            metrics.recordAuditPersistence(source.topic(), event.eventType(), "persisted");
            return AuditEventPersistenceResult.persisted(event);
        } catch (DuplicateKeyException exception) {
            metrics.recordAuditPersistence(source.topic(), event.eventType(), "duplicate");
            return AuditEventPersistenceResult.duplicate(event);
        } catch (RuntimeException exception) {
            metrics.recordAuditPersistence(source.topic(), event.eventType(), "failure");
            throw exception;
        }
    }

    public AuditEventPersistenceResult persistPaymentResult(
            AuditPaymentResultEvent event,
            AuditEventSource source) {
        Objects.requireNonNull(event, "event is required");
        Objects.requireNonNull(source, "source is required");

        AuditEventDocument document = new AuditEventDocument(
                event.eventId().toString(),
                event.eventType(),
                event.eventVersion(),
                event.aggregateId().toString(),
                event.orderId().toString(),
                event.customerId(),
                event.paymentId().toString(),
                event.occurredAt(),
                Instant.now(clock),
                event.traceId(),
                event.correlationId(),
                source.topic(),
                source.partition(),
                source.offset(),
                event.payload());

        try {
            repository.save(document);
            metrics.recordAuditPersistence(source.topic(), event.eventType(), "persisted");
            return AuditEventPersistenceResult.persisted(event);
        } catch (DuplicateKeyException exception) {
            metrics.recordAuditPersistence(source.topic(), event.eventType(), "duplicate");
            return AuditEventPersistenceResult.duplicate(event);
        } catch (RuntimeException exception) {
            metrics.recordAuditPersistence(source.topic(), event.eventType(), "failure");
            throw exception;
        }
    }
}
