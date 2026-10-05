package com.kora.ecommerce.auditnotification.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.kora.ecommerce.auditnotification.observability.AuditNotificationOperationalMetrics;
import com.kora.ecommerce.auditnotification.order.AuditOrderCreatedEvent;
import com.kora.ecommerce.auditnotification.payment.AuditPaymentResultEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class AuditEventPersistenceServiceTest {

    private static final Instant RECEIVED_AT = Instant.parse("2026-10-04T10:15:31Z");

    @Mock
    private AuditEventRepository repository;

    @Test
    void persistsCompleteOrderCreatedAuditDocument() {
        AuditOrderCreatedEvent event = event();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AuditEventPersistenceService service = service(registry);

        AuditEventPersistenceResult result = service.persistOrderCreated(
                event,
                new AuditEventSource("ecommerce.order.events", 2, 42L));

        assertThat(result.outcome()).isEqualTo(AuditEventPersistenceResult.Outcome.PERSISTED);
        ArgumentCaptor<AuditEventDocument> captor = ArgumentCaptor.forClass(AuditEventDocument.class);
        verify(repository).save(captor.capture());
        AuditEventDocument document = captor.getValue();
        assertThat(document.getEventId()).isEqualTo(event.eventId().toString());
        assertThat(document.getEventType()).isEqualTo("OrderCreated");
        assertThat(document.getEventVersion()).isEqualTo(1);
        assertThat(document.getAggregateId()).isEqualTo(event.aggregateId().toString());
        assertThat(document.getOrderId()).isEqualTo(event.orderId().toString());
        assertThat(document.getCustomerId()).isEqualTo("jwt-customer-123");
        assertThat(document.getPaymentId()).isNull();
        assertThat(document.getOccurredAt()).isEqualTo(event.occurredAt());
        assertThat(document.getReceivedAt()).isEqualTo(RECEIVED_AT);
        assertThat(document.getTraceId()).isEqualTo("trace-123");
        assertThat(document.getCorrelationId()).isEqualTo("corr-123");
        assertThat(document.getSourceTopic()).isEqualTo("ecommerce.order.events");
        assertThat(document.getSourcePartition()).isEqualTo(2);
        assertThat(document.getSourceOffset()).isEqualTo(42L);
        assertThat(document.getPayload().get("data", Document.class))
                .containsEntry("customerId", "jwt-customer-123");
        assertThat(counter(
                registry,
                AuditNotificationOperationalMetrics.AUDIT_PERSISTENCE_EVENTS,
                "topic", "ecommerce.order.events",
                "event_type", "OrderCreated",
                "outcome", "persisted")).isEqualTo(1.0);
    }

    @Test
    void treatsDuplicateEventIdAsSuccessfulReplaySkip() {
        AuditOrderCreatedEvent event = event();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        when(repository.save(any(AuditEventDocument.class)))
                .thenThrow(new DuplicateKeyException("duplicate event_id"));
        AuditEventPersistenceService service = service(registry);

        AuditEventPersistenceResult result = service.persistOrderCreated(
                event,
                new AuditEventSource("ecommerce.order.events", 0, 1L));

        assertThat(result.outcome()).isEqualTo(AuditEventPersistenceResult.Outcome.DUPLICATE);
        assertThat(result.eventId()).isEqualTo(event.eventId().toString());
        assertThat(result.orderId()).isEqualTo(event.orderId().toString());
        assertThat(counter(
                registry,
                AuditNotificationOperationalMetrics.AUDIT_PERSISTENCE_EVENTS,
                "topic", "ecommerce.order.events",
                "event_type", "OrderCreated",
                "outcome", "duplicate")).isEqualTo(1.0);
    }

    @Test
    void persistsCompletePaymentResultAuditDocument() {
        AuditPaymentResultEvent event = paymentEvent("PaymentSucceeded", "SUCCEEDED");
        AuditEventPersistenceService service = service();

        AuditEventPersistenceResult result = service.persistPaymentResult(
                event,
                new AuditEventSource("ecommerce.payment.events", 3, 43L));

        assertThat(result.outcome()).isEqualTo(AuditEventPersistenceResult.Outcome.PERSISTED);
        ArgumentCaptor<AuditEventDocument> captor = ArgumentCaptor.forClass(AuditEventDocument.class);
        verify(repository).save(captor.capture());
        AuditEventDocument document = captor.getValue();
        assertThat(document.getEventId()).isEqualTo(event.eventId().toString());
        assertThat(document.getEventType()).isEqualTo("PaymentSucceeded");
        assertThat(document.getEventVersion()).isEqualTo(1);
        assertThat(document.getAggregateId()).isEqualTo(event.aggregateId().toString());
        assertThat(document.getOrderId()).isEqualTo(event.orderId().toString());
        assertThat(document.getCustomerId()).isEqualTo("jwt-customer-123");
        assertThat(document.getPaymentId()).isEqualTo(event.paymentId().toString());
        assertThat(document.getOccurredAt()).isEqualTo(event.occurredAt());
        assertThat(document.getReceivedAt()).isEqualTo(RECEIVED_AT);
        assertThat(document.getTraceId()).isEqualTo("trace-123");
        assertThat(document.getCorrelationId()).isEqualTo("corr-123");
        assertThat(document.getSourceTopic()).isEqualTo("ecommerce.payment.events");
        assertThat(document.getSourcePartition()).isEqualTo(3);
        assertThat(document.getSourceOffset()).isEqualTo(43L);
        assertThat(document.getPayload().get("data", Document.class))
                .containsEntry("paymentStatus", "SUCCEEDED")
                .containsEntry("customerId", "jwt-customer-123");
    }

    @Test
    void treatsDuplicatePaymentResultEventIdAsSuccessfulReplaySkip() {
        AuditPaymentResultEvent event = paymentEvent("PaymentFailed", "FAILED");
        when(repository.save(any(AuditEventDocument.class)))
                .thenThrow(new DuplicateKeyException("duplicate event_id"));
        AuditEventPersistenceService service = service();

        AuditEventPersistenceResult result = service.persistPaymentResult(
                event,
                new AuditEventSource("ecommerce.payment.events", 0, 1L));

        assertThat(result.outcome()).isEqualTo(AuditEventPersistenceResult.Outcome.DUPLICATE);
        assertThat(result.eventId()).isEqualTo(event.eventId().toString());
        assertThat(result.orderId()).isEqualTo(event.orderId().toString());
    }

    @Test
    void leavesTransientMongoFailuresRetryableForKafka() {
        when(repository.save(any(AuditEventDocument.class)))
                .thenThrow(new DataAccessResourceFailureException("mongo unavailable"));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AuditEventPersistenceService service = service(registry);

        assertThatThrownBy(() -> service.persistOrderCreated(
                        event(),
                        new AuditEventSource("ecommerce.order.events", 0, 1L)))
                .isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(counter(
                registry,
                AuditNotificationOperationalMetrics.AUDIT_PERSISTENCE_EVENTS,
                "topic", "ecommerce.order.events",
                "event_type", "OrderCreated",
                "outcome", "failure")).isEqualTo(1.0);
    }

    @Test
    void leavesTransientMongoFailuresRetryableForPaymentResultKafka() {
        when(repository.save(any(AuditEventDocument.class)))
                .thenThrow(new DataAccessResourceFailureException("mongo unavailable"));
        AuditEventPersistenceService service = service();

        assertThatThrownBy(() -> service.persistPaymentResult(
                        paymentEvent("PaymentSucceeded", "SUCCEEDED"),
                        new AuditEventSource("ecommerce.payment.events", 0, 1L)))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }

    private AuditEventPersistenceService service() {
        return service(new SimpleMeterRegistry());
    }

    private AuditEventPersistenceService service(SimpleMeterRegistry registry) {
        return new AuditEventPersistenceService(
                repository,
                Clock.fixed(RECEIVED_AT, ZoneOffset.UTC),
                new AuditNotificationOperationalMetrics(registry));
    }

    private static double counter(
            SimpleMeterRegistry registry,
            String name,
            String... tags) {
        return registry.get(name)
                .tag("service", "audit-notification-service")
                .tags(tags)
                .counter()
                .count();
    }

    private AuditOrderCreatedEvent event() {
        UUID eventId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID orderId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        return new AuditOrderCreatedEvent(
                eventId,
                "OrderCreated",
                1,
                orderId,
                Instant.parse("2026-10-04T10:00:00Z"),
                "trace-123",
                "corr-123",
                orderId,
                "jwt-customer-123",
                new Document()
                        .append("eventId", eventId.toString())
                        .append("eventType", "OrderCreated")
                        .append("eventVersion", 1)
                        .append("aggregateId", orderId.toString())
                        .append("data", new Document()
                                .append("orderId", orderId.toString())
                                .append("customerId", "jwt-customer-123")));
    }

    private AuditPaymentResultEvent paymentEvent(String eventType, String paymentStatus) {
        UUID eventId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID orderId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID paymentId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        return new AuditPaymentResultEvent(
                eventId,
                eventType,
                1,
                orderId,
                Instant.parse("2026-10-04T10:00:00Z"),
                "trace-123",
                "corr-123",
                orderId,
                paymentId,
                "jwt-customer-123",
                paymentStatus,
                new Document()
                        .append("eventId", eventId.toString())
                        .append("eventType", eventType)
                        .append("eventVersion", 1)
                        .append("aggregateId", orderId.toString())
                        .append("data", new Document()
                                .append("paymentId", paymentId.toString())
                                .append("orderId", orderId.toString())
                                .append("customerId", "jwt-customer-123")
                                .append("paymentStatus", paymentStatus)));
    }
}
