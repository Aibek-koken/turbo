package com.kora.ecommerce.auditnotification.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import com.kora.ecommerce.auditnotification.notification.EmailNotificationDispatcher;
import com.kora.ecommerce.auditnotification.notification.NotificationRoutingService;
import com.kora.ecommerce.auditnotification.notification.PushNotificationDispatcher;
import com.kora.ecommerce.auditnotification.observability.AuditNotificationOperationalMetrics;
import com.kora.ecommerce.auditnotification.observability.EventLoggingContext;
import com.kora.ecommerce.auditnotification.payment.AuditPaymentResultEvent;
import com.kora.ecommerce.auditnotification.payment.AuditPaymentResultEventParser;
import com.kora.ecommerce.auditnotification.payment.InvalidPaymentResultEventException;
import com.kora.ecommerce.auditnotification.payment.PaymentResultEventMetadata;
import com.kora.ecommerce.auditnotification.persistence.AuditEventPersistenceResult;
import com.kora.ecommerce.auditnotification.persistence.AuditEventPersistenceService;
import com.kora.ecommerce.auditnotification.persistence.AuditEventSource;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

@ExtendWith(MockitoExtension.class)
class PaymentResultAuditKafkaListenerTest {

    @Mock
    private AuditPaymentResultEventParser parser;

    @Mock
    private AuditEventPersistenceService persistenceService;

    @Mock
    private NotificationRoutingService notificationRoutingService;

    @Mock
    private EmailNotificationDispatcher emailNotificationDispatcher;

    @Mock
    private PushNotificationDispatcher pushNotificationDispatcher;

    @BeforeEach
    void clearMdcBeforeTest() {
        MDC.clear();
    }

    @AfterEach
    void clearMdcAfterTest() {
        MDC.clear();
    }

    @Test
    void persistsValidPaymentSucceededPayloadBeforeReturningToKafka() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AuditPaymentResultEvent event = event("PaymentSucceeded", "SUCCEEDED");
        AuditEventSource source = new AuditEventSource("ecommerce.payment.events", 1, 22L);
        when(parser.parse("payload")).thenReturn(event);
        when(persistenceService.persistPaymentResult(event, source))
                .thenReturn(AuditEventPersistenceResult.persisted(event));
        PaymentResultAuditKafkaListener listener = listener(registry);

        listener.onMessage("payload", event.orderId().toString(), "ecommerce.payment.events", 1, 22L);

        verify(persistenceService).persistPaymentResult(event, source);
        verify(notificationRoutingService).routePaymentResult(event);
        verify(emailNotificationDispatcher).dispatchPendingEmailDeliveries();
        verify(pushNotificationDispatcher).dispatchPendingPushDeliveries();
        assertThat(counter(
                registry,
                "topic", "ecommerce.payment.events",
                "event_type", "PaymentSucceeded",
                "outcome", "persisted")).isEqualTo(1.0);
    }

    @Test
    void bindsEnvelopeMetadataToMdcOnlyDuringPaymentResultHandling() {
        AuditPaymentResultEvent event = event("PaymentSucceeded", "SUCCEEDED");
        AuditEventSource source = new AuditEventSource("ecommerce.payment.events", 1, 22L);
        MDC.put(EventLoggingContext.TRACE_ID_MDC_KEY, "previous-trace");
        MDC.put(EventLoggingContext.CORRELATION_ID_MDC_KEY, "previous-correlation");
        when(parser.parse("payload")).thenReturn(event);
        when(persistenceService.persistPaymentResult(event, source)).thenAnswer(invocation -> {
            assertThat(MDC.get(EventLoggingContext.TRACE_ID_MDC_KEY)).isEqualTo(event.traceId());
            assertThat(MDC.get(EventLoggingContext.CORRELATION_ID_MDC_KEY)).isEqualTo(event.correlationId());
            assertThat(MDC.get(EventLoggingContext.EVENT_ID_MDC_KEY)).isEqualTo(event.eventId().toString());
            assertThat(MDC.get(EventLoggingContext.EVENT_TYPE_MDC_KEY)).isEqualTo(event.eventType());
            assertThat(MDC.get(EventLoggingContext.EVENT_VERSION_MDC_KEY))
                    .isEqualTo(String.valueOf(event.eventVersion()));
            assertThat(MDC.get(EventLoggingContext.AGGREGATE_ID_MDC_KEY))
                    .isEqualTo(event.aggregateId().toString());
            return AuditEventPersistenceResult.persisted(event);
        });
        PaymentResultAuditKafkaListener listener = listener();

        listener.onMessage("payload", event.orderId().toString(), "ecommerce.payment.events", 1, 22L);

        assertThat(MDC.get(EventLoggingContext.TRACE_ID_MDC_KEY)).isEqualTo("previous-trace");
        assertThat(MDC.get(EventLoggingContext.CORRELATION_ID_MDC_KEY)).isEqualTo("previous-correlation");
        assertThat(MDC.get(EventLoggingContext.EVENT_ID_MDC_KEY)).isNull();
        assertThat(MDC.get(EventLoggingContext.EVENT_TYPE_MDC_KEY)).isNull();
        assertThat(MDC.get(EventLoggingContext.EVENT_VERSION_MDC_KEY)).isNull();
        assertThat(MDC.get(EventLoggingContext.AGGREGATE_ID_MDC_KEY)).isNull();
    }

    @Test
    void acceptsDuplicatePaymentResultSkipFromPersistenceService() {
        AuditPaymentResultEvent event = event("PaymentFailed", "FAILED");
        AuditEventSource source = new AuditEventSource("ecommerce.payment.events", 1, 22L);
        when(parser.parse("payload")).thenReturn(event);
        when(persistenceService.persistPaymentResult(event, source))
                .thenReturn(AuditEventPersistenceResult.duplicate(event));
        PaymentResultAuditKafkaListener listener = listener();

        listener.onMessage("payload", event.orderId().toString(), "ecommerce.payment.events", 1, 22L);

        verify(persistenceService).persistPaymentResult(event, source);
        verify(notificationRoutingService).routePaymentResult(event);
        verify(emailNotificationDispatcher).dispatchPendingEmailDeliveries();
        verify(pushNotificationDispatcher).dispatchPendingPushDeliveries();
    }

    @Test
    void rejectsInvalidPaymentResultPayloadWithoutPersistenceSideEffectAndDelegatesToErrorHandler() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        when(parser.parse("payload")).thenThrow(new InvalidPaymentResultEventException(
                "payment_status_event_type_mismatch",
                new PaymentResultEventMetadata(
                        "11111111-1111-1111-1111-111111111111",
                        "PaymentSucceeded",
                        1,
                        "22222222-2222-2222-2222-222222222222")));
        PaymentResultAuditKafkaListener listener = listener(registry);

        assertThatThrownBy(() -> listener.onMessage("payload", "key", "ecommerce.payment.events", 0, 12L))
                .isInstanceOf(InvalidPaymentResultEventException.class);

        verifyNoInteractions(persistenceService);
        verifyNoInteractions(notificationRoutingService);
        verifyNoInteractions(emailNotificationDispatcher);
        verifyNoInteractions(pushNotificationDispatcher);
        assertThat(counter(
                registry,
                "topic", "ecommerce.payment.events",
                "event_type", "PaymentResult",
                "outcome", "rejected")).isEqualTo(1.0);
    }

    private PaymentResultAuditKafkaListener listener() {
        return listener(new SimpleMeterRegistry());
    }

    private PaymentResultAuditKafkaListener listener(SimpleMeterRegistry registry) {
        return new PaymentResultAuditKafkaListener(
                parser,
                persistenceService,
                notificationRoutingService,
                emailNotificationDispatcher,
                pushNotificationDispatcher,
                ObservationRegistry.NOOP,
                new AuditNotificationOperationalMetrics(registry));
    }

    private static double counter(
            SimpleMeterRegistry registry,
            String... tags) {
        return registry.get(AuditNotificationOperationalMetrics.KAFKA_CONSUMER_EVENTS)
                .tag("service", "audit-notification-service")
                .tags(tags)
                .counter()
                .count();
    }

    private AuditPaymentResultEvent event(String eventType, String paymentStatus) {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        return new AuditPaymentResultEvent(
                UUID.randomUUID(),
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
                        .append("eventType", eventType)
                        .append("data", new Document()
                                .append("orderId", orderId.toString())
                                .append("paymentId", paymentId.toString())));
    }
}
