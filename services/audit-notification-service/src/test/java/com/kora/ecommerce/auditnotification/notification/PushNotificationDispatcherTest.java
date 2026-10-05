package com.kora.ecommerce.auditnotification.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import com.kora.ecommerce.auditnotification.observability.AuditNotificationOperationalMetrics;
import com.kora.ecommerce.auditnotification.observability.EventLoggingContext;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class PushNotificationDispatcherTest {

    private static final Instant EVENT_OCCURRED_AT = Instant.parse("2026-10-04T10:15:30Z");
    private static final Instant NOW = Instant.parse("2026-10-04T10:16:00Z");

    @Mock
    private NotificationDeliveryRepository repository;

    @Mock
    private PushNotificationPort pushPort;

    @BeforeEach
    void clearMdcBeforeTest() {
        MDC.clear();
    }

    @AfterEach
    void clearMdcAfterTest() {
        MDC.clear();
    }

    @Test
    void sendsPendingPushAndMarksDeliverySent() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        NotificationDeliveryDocument delivery = delivery(NotificationDeliveryStatus.PENDING, 0);
        List<NotificationDeliveryStatus> savedStatuses = captureSavedStatuses(delivery);
        when(repository.findByChannelAndStatus(
                eq(NotificationChannel.PUSH),
                eq(NotificationDeliveryStatus.PENDING),
                any(Pageable.class)))
                .thenReturn(List.of(delivery));
        PushNotificationDispatcher dispatcher = dispatcher(properties(3, true), registry);

        PushDeliveryDispatchResult result = dispatcher.dispatchPendingPushDeliveries();

        assertThat(result).isEqualTo(new PushDeliveryDispatchResult(1, 1, 1, 0, 0, 0));
        assertThat(delivery.getStatus()).isEqualTo(NotificationDeliveryStatus.SENT);
        assertThat(delivery.getAttemptCount()).isEqualTo(1);
        assertThat(delivery.getLastAttemptAt()).isEqualTo(NOW);
        assertThat(delivery.getSafeFailureDetail()).isNull();
        assertThat(savedStatuses).containsExactly(
                NotificationDeliveryStatus.IN_PROGRESS,
                NotificationDeliveryStatus.SENT);
        ArgumentCaptor<PushNotificationPayload> payloadCaptor =
                ArgumentCaptor.forClass(PushNotificationPayload.class);
        verify(pushPort).send(payloadCaptor.capture());
        assertThat(payloadCaptor.getValue().messageId()).isEqualTo("push:evt-1");
        assertThat(payloadCaptor.getValue().recipientCustomerId()).isEqualTo("customer-1");
        assertThat(payloadCaptor.getValue().notificationType()).isEqualTo("payment-succeeded-push-v1");
        assertThat(counter(
                registry,
                "channel", "push",
                "event_type", "PaymentSucceeded",
                "outcome", "sent")).isEqualTo(1.0);
    }

    @Test
    void marksAdapterFailureFailedWithSafeDetail() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        NotificationDeliveryDocument delivery = delivery(NotificationDeliveryStatus.PENDING, 0);
        List<NotificationDeliveryStatus> savedStatuses = captureSavedStatuses(delivery);
        when(repository.findByChannelAndStatus(
                eq(NotificationChannel.PUSH),
                eq(NotificationDeliveryStatus.PENDING),
                any(Pageable.class)))
                .thenReturn(List.of(delivery));
        org.mockito.Mockito.doThrow(new PushNotificationDeliveryException("mock_push_unavailable"))
                .when(pushPort)
                .send(any(PushNotificationPayload.class));
        PushNotificationDispatcher dispatcher = dispatcher(properties(3, true), registry);

        PushDeliveryDispatchResult result = dispatcher.dispatchPendingPushDeliveries();

        assertThat(result).isEqualTo(new PushDeliveryDispatchResult(1, 1, 0, 1, 0, 0));
        assertThat(delivery.getStatus()).isEqualTo(NotificationDeliveryStatus.FAILED);
        assertThat(delivery.getAttemptCount()).isEqualTo(1);
        assertThat(delivery.getSafeFailureDetail()).isEqualTo("mock_push_unavailable");
        assertThat(savedStatuses).containsExactly(
                NotificationDeliveryStatus.IN_PROGRESS,
                NotificationDeliveryStatus.FAILED);
        assertThat(counter(
                registry,
                "channel", "push",
                "event_type", "PaymentSucceeded",
                "outcome", "failed")).isEqualTo(1.0);
    }

    @Test
    void bindsDeliveryMetadataToMdcOnlyWhileSendingPush() {
        NotificationDeliveryDocument delivery = delivery(NotificationDeliveryStatus.PENDING, 0);
        captureSavedStatuses(delivery);
        when(repository.findByChannelAndStatus(
                eq(NotificationChannel.PUSH),
                eq(NotificationDeliveryStatus.PENDING),
                any(Pageable.class)))
                .thenReturn(List.of(delivery));
        MDC.put(EventLoggingContext.EVENT_ID_MDC_KEY, "current-listener-event");
        MDC.put(EventLoggingContext.TRACE_ID_MDC_KEY, "current-listener-trace");
        doAnswer(invocation -> {
            assertThat(MDC.get(EventLoggingContext.EVENT_ID_MDC_KEY)).isEqualTo(delivery.getEventId());
            assertThat(MDC.get(EventLoggingContext.EVENT_TYPE_MDC_KEY)).isEqualTo(delivery.getEventType());
            assertThat(MDC.get(EventLoggingContext.EVENT_VERSION_MDC_KEY))
                    .isEqualTo(String.valueOf(delivery.getEventVersion()));
            assertThat(MDC.get(EventLoggingContext.AGGREGATE_ID_MDC_KEY)).isEqualTo(delivery.getOrderId());
            assertThat(MDC.get(EventLoggingContext.TRACE_ID_MDC_KEY)).isEqualTo(delivery.getTraceId());
            assertThat(MDC.get(EventLoggingContext.CORRELATION_ID_MDC_KEY))
                    .isEqualTo(delivery.getCorrelationId());
            return null;
        }).when(pushPort).send(any(PushNotificationPayload.class));
        PushNotificationDispatcher dispatcher = dispatcher(properties(3, true));

        dispatcher.dispatchPendingPushDeliveries();

        assertThat(MDC.get(EventLoggingContext.EVENT_ID_MDC_KEY)).isEqualTo("current-listener-event");
        assertThat(MDC.get(EventLoggingContext.TRACE_ID_MDC_KEY)).isEqualTo("current-listener-trace");
        assertThat(MDC.get(EventLoggingContext.EVENT_TYPE_MDC_KEY)).isNull();
        assertThat(MDC.get(EventLoggingContext.EVENT_VERSION_MDC_KEY)).isNull();
        assertThat(MDC.get(EventLoggingContext.AGGREGATE_ID_MDC_KEY)).isNull();
        assertThat(MDC.get(EventLoggingContext.CORRELATION_ID_MDC_KEY)).isNull();
    }


    @Test
    void doesNotSendWhenMaxAttemptsAlreadyReached() {
        NotificationDeliveryDocument delivery = delivery(NotificationDeliveryStatus.PENDING, 3);
        when(repository.findByChannelAndStatus(
                eq(NotificationChannel.PUSH),
                eq(NotificationDeliveryStatus.PENDING),
                any(Pageable.class)))
                .thenReturn(List.of(delivery));
        when(repository.save(delivery)).thenReturn(delivery);
        PushNotificationDispatcher dispatcher = dispatcher(properties(3, true));

        PushDeliveryDispatchResult result = dispatcher.dispatchPendingPushDeliveries();

        assertThat(result).isEqualTo(new PushDeliveryDispatchResult(1, 0, 0, 1, 0, 1));
        assertThat(delivery.getStatus()).isEqualTo(NotificationDeliveryStatus.FAILED);
        assertThat(delivery.getAttemptCount()).isEqualTo(3);
        assertThat(delivery.getSafeFailureDetail()).isEqualTo("push_max_attempts_exhausted");
        verify(pushPort, never()).send(any());
    }

    @Test
    void completedDeliveryReplayDoesNotSendSecondPush() {
        NotificationDeliveryDocument delivery = delivery(NotificationDeliveryStatus.PENDING, 0);
        when(repository.findByChannelAndStatus(
                eq(NotificationChannel.PUSH),
                eq(NotificationDeliveryStatus.PENDING),
                any(Pageable.class)))
                .thenReturn(List.of(delivery), List.of());
        when(repository.save(delivery)).thenReturn(delivery);
        PushNotificationDispatcher dispatcher = dispatcher(properties(3, true));

        PushDeliveryDispatchResult first = dispatcher.dispatchPendingPushDeliveries();
        PushDeliveryDispatchResult second = dispatcher.dispatchPendingPushDeliveries();

        assertThat(first).isEqualTo(new PushDeliveryDispatchResult(1, 1, 1, 0, 0, 0));
        assertThat(second).isEqualTo(PushDeliveryDispatchResult.empty());
        assertThat(delivery.getStatus()).isEqualTo(NotificationDeliveryStatus.SENT);
        verify(pushPort, times(1)).send(any(PushNotificationPayload.class));
    }

    @Test
    void skipsRecordsThatAreNoLongerPendingPushDeliveries() {
        NotificationDeliveryDocument delivery = delivery(NotificationDeliveryStatus.SENT, 1);
        when(repository.findByChannelAndStatus(
                eq(NotificationChannel.PUSH),
                eq(NotificationDeliveryStatus.PENDING),
                any(Pageable.class)))
                .thenReturn(List.of(delivery));
        PushNotificationDispatcher dispatcher = dispatcher(properties(3, true));

        PushDeliveryDispatchResult result = dispatcher.dispatchPendingPushDeliveries();

        assertThat(result).isEqualTo(new PushDeliveryDispatchResult(1, 0, 0, 0, 1, 0));
        verify(repository, never()).save(any());
        verify(pushPort, never()).send(any());
    }

    @Test
    void disabledDispatcherDoesNotLoadOrSendDeliveries() {
        PushNotificationDispatcher dispatcher = dispatcher(properties(3, false));

        PushDeliveryDispatchResult result = dispatcher.dispatchPendingPushDeliveries();

        assertThat(result).isEqualTo(PushDeliveryDispatchResult.empty());
        verify(repository, never()).findByChannelAndStatus(
                any(NotificationChannel.class),
                any(NotificationDeliveryStatus.class),
                any(Pageable.class));
        verify(pushPort, never()).send(any());
    }

    private PushNotificationDispatcher dispatcher(PushNotificationProperties properties) {
        return dispatcher(properties, new SimpleMeterRegistry());
    }

    private PushNotificationDispatcher dispatcher(
            PushNotificationProperties properties,
            SimpleMeterRegistry registry) {
        return new PushNotificationDispatcher(
                repository,
                new PushNotificationPayloadFactory(),
                pushPort,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new AuditNotificationOperationalMetrics(registry));
    }

    private static double counter(
            SimpleMeterRegistry registry,
            String... tags) {
        return registry.get(AuditNotificationOperationalMetrics.NOTIFICATION_DELIVERIES)
                .tag("service", "audit-notification-service")
                .tags(tags)
                .counter()
                .count();
    }

    private List<NotificationDeliveryStatus> captureSavedStatuses(NotificationDeliveryDocument delivery) {
        List<NotificationDeliveryStatus> statuses = new ArrayList<>();
        when(repository.save(delivery)).thenAnswer(invocation -> {
            statuses.add(delivery.getStatus());
            return delivery;
        });
        return statuses;
    }

    private static PushNotificationProperties properties(int maxAttempts, boolean enabled) {
        PushNotificationProperties properties = new PushNotificationProperties();
        properties.setMaxAttempts(maxAttempts);
        properties.setEnabled(enabled);
        return properties;
    }

    private static NotificationDeliveryDocument delivery(
            NotificationDeliveryStatus status,
            int attemptCount) {
        return new NotificationDeliveryDocument(
                "evt-1",
                NotificationChannel.PUSH,
                "PaymentSucceeded",
                1,
                "order-1",
                "customer-1",
                "payment-1",
                status,
                attemptCount,
                attemptCount == 0 ? null : NOW.minusSeconds(60),
                status == NotificationDeliveryStatus.FAILED ? "mock_push_unavailable" : null,
                EVENT_OCCURRED_AT,
                NOW.minusSeconds(120),
                NOW.minusSeconds(90),
                "trace-1",
                "corr-1");
    }
}
