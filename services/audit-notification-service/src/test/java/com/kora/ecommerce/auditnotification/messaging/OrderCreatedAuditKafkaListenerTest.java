package com.kora.ecommerce.auditnotification.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import com.kora.ecommerce.auditnotification.order.AuditOrderCreatedEvent;
import com.kora.ecommerce.auditnotification.order.AuditOrderCreatedEventParser;
import com.kora.ecommerce.auditnotification.order.InvalidOrderCreatedEventException;
import com.kora.ecommerce.auditnotification.order.OrderCreatedEventMetadata;
import com.kora.ecommerce.auditnotification.notification.EmailNotificationDispatcher;
import com.kora.ecommerce.auditnotification.notification.NotificationRoutingService;
import com.kora.ecommerce.auditnotification.notification.PushNotificationDispatcher;
import com.kora.ecommerce.auditnotification.observability.AuditNotificationOperationalMetrics;
import com.kora.ecommerce.auditnotification.observability.EventLoggingContext;
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
class OrderCreatedAuditKafkaListenerTest {

    @Mock
    private AuditOrderCreatedEventParser parser;

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
    void persistsValidPayloadBeforeReturningToKafka() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AuditOrderCreatedEvent event = event();
        AuditEventSource source = new AuditEventSource("ecommerce.order.events", 0, 12L);
        when(parser.parse("payload")).thenReturn(event);
        when(persistenceService.persistOrderCreated(event, source))
                .thenReturn(AuditEventPersistenceResult.persisted(event));
        OrderCreatedAuditKafkaListener listener = listener(registry);

        listener.onMessage("payload", event.orderId().toString(), "ecommerce.order.events", 0, 12L);

        verify(persistenceService).persistOrderCreated(event, source);
        verify(notificationRoutingService).routeOrderCreated(event);
        verify(emailNotificationDispatcher).dispatchPendingEmailDeliveries();
        verify(pushNotificationDispatcher).dispatchPendingPushDeliveries();
        assertThat(counter(
                registry,
                "topic", "ecommerce.order.events",
                "event_type", "OrderCreated",
                "outcome", "persisted")).isEqualTo(1.0);
    }

    @Test
    void bindsEnvelopeMetadataToMdcOnlyDuringOrderCreatedHandling() {
        AuditOrderCreatedEvent event = event();
        AuditEventSource source = new AuditEventSource("ecommerce.order.events", 0, 12L);
        MDC.put(EventLoggingContext.TRACE_ID_MDC_KEY, "previous-trace");
        MDC.put(EventLoggingContext.CORRELATION_ID_MDC_KEY, "previous-correlation");
        when(parser.parse("payload")).thenReturn(event);
        when(persistenceService.persistOrderCreated(event, source)).thenAnswer(invocation -> {
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
        OrderCreatedAuditKafkaListener listener = listener();

        listener.onMessage("payload", event.orderId().toString(), "ecommerce.order.events", 0, 12L);

        assertThat(MDC.get(EventLoggingContext.TRACE_ID_MDC_KEY)).isEqualTo("previous-trace");
        assertThat(MDC.get(EventLoggingContext.CORRELATION_ID_MDC_KEY)).isEqualTo("previous-correlation");
        assertThat(MDC.get(EventLoggingContext.EVENT_ID_MDC_KEY)).isNull();
        assertThat(MDC.get(EventLoggingContext.EVENT_TYPE_MDC_KEY)).isNull();
        assertThat(MDC.get(EventLoggingContext.EVENT_VERSION_MDC_KEY)).isNull();
        assertThat(MDC.get(EventLoggingContext.AGGREGATE_ID_MDC_KEY)).isNull();
    }

    @Test
    void acceptsDuplicateDeliverySkipFromPersistenceService() {
        AuditOrderCreatedEvent event = event();
        AuditEventSource source = new AuditEventSource("ecommerce.order.events", 0, 12L);
        when(parser.parse("payload")).thenReturn(event);
        when(persistenceService.persistOrderCreated(event, source))
                .thenReturn(AuditEventPersistenceResult.duplicate(event));
        OrderCreatedAuditKafkaListener listener = listener();

        listener.onMessage("payload", event.orderId().toString(), "ecommerce.order.events", 0, 12L);

        verify(persistenceService).persistOrderCreated(event, source);
        verify(notificationRoutingService).routeOrderCreated(event);
        verify(emailNotificationDispatcher).dispatchPendingEmailDeliveries();
        verify(pushNotificationDispatcher).dispatchPendingPushDeliveries();
    }

    @Test
    void rejectsInvalidPayloadWithoutPersistenceSideEffectAndDelegatesToErrorHandler() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        when(parser.parse("payload")).thenThrow(new InvalidOrderCreatedEventException(
                "event_version_unsupported",
                new OrderCreatedEventMetadata(
                        "11111111-1111-1111-1111-111111111111",
                        "OrderCreated",
                        2,
                        "22222222-2222-2222-2222-222222222222")));
        OrderCreatedAuditKafkaListener listener = listener(registry);

        assertThatThrownBy(() -> listener.onMessage("payload", "key", "ecommerce.order.events", 0, 12L))
                .isInstanceOf(InvalidOrderCreatedEventException.class);

        verifyNoInteractions(persistenceService);
        verifyNoInteractions(notificationRoutingService);
        verifyNoInteractions(emailNotificationDispatcher);
        verifyNoInteractions(pushNotificationDispatcher);
        assertThat(counter(
                registry,
                "topic", "ecommerce.order.events",
                "event_type", "OrderCreated",
                "outcome", "rejected")).isEqualTo(1.0);
    }

    private OrderCreatedAuditKafkaListener listener() {
        return listener(new SimpleMeterRegistry());
    }

    private OrderCreatedAuditKafkaListener listener(SimpleMeterRegistry registry) {
        return new OrderCreatedAuditKafkaListener(
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

    private AuditOrderCreatedEvent event() {
        UUID orderId = UUID.randomUUID();
        return new AuditOrderCreatedEvent(
                UUID.randomUUID(),
                "OrderCreated",
                1,
                orderId,
                Instant.parse("2026-10-04T10:00:00Z"),
                "trace-123",
                "corr-123",
                orderId,
                "jwt-customer-123",
                new Document()
                        .append("eventType", "OrderCreated")
                        .append("data", new Document("orderId", orderId.toString())));
    }
}
