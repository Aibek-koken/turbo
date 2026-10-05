package com.kora.ecommerce.auditnotification.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class NotificationRoutingServiceTest {

    private static final Instant EVENT_OCCURRED_AT = Instant.parse("2026-10-04T10:15:30Z");
    private static final Instant ROUTED_AT = Instant.parse("2026-10-04T10:15:31Z");
    private static final Instant ATTEMPTED_AT = Instant.parse("2026-10-04T10:16:00Z");
    private static final String EVENT_ID = "11111111-1111-1111-1111-111111111111";

    @Mock
    private NotificationDeliveryRepository repository;

    @Test
    void createsEmailAndPushRecordsForRelevantOrderCreatedEvent() {
        when(repository.findByEventIdAndChannel(EVENT_ID, NotificationChannel.EMAIL))
                .thenReturn(Optional.empty());
        when(repository.findByEventIdAndChannel(EVENT_ID, NotificationChannel.PUSH))
                .thenReturn(Optional.empty());
        when(repository.save(any(NotificationDeliveryDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        NotificationRoutingService service = service();

        NotificationRoutingResult result = service.route(request("OrderCreated", 1, null));

        assertThat(result.outcome()).isEqualTo(NotificationRoutingResult.Outcome.ROUTED);
        assertThat(result.channels())
                .extracting(NotificationRoutingResult.ChannelDeliveryResult::outcome)
                .containsExactly(
                        NotificationRoutingResult.ChannelOutcome.CREATED,
                        NotificationRoutingResult.ChannelOutcome.CREATED);
        ArgumentCaptor<NotificationDeliveryDocument> captor =
                ArgumentCaptor.forClass(NotificationDeliveryDocument.class);
        verify(repository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(NotificationDeliveryDocument::getChannel)
                .containsExactly(NotificationChannel.EMAIL, NotificationChannel.PUSH);
        assertThat(captor.getAllValues())
                .allSatisfy(document -> {
                    assertThat(document.getStatus()).isEqualTo(NotificationDeliveryStatus.PENDING);
                    assertThat(document.getAttemptCount()).isZero();
                    assertThat(document.getCreatedAt()).isEqualTo(ROUTED_AT);
                    assertThat(document.getUpdatedAt()).isEqualTo(ROUTED_AT);
                    assertThat(document.getEventId()).isEqualTo(EVENT_ID);
                    assertThat(document.getOrderId()).isEqualTo("order-1");
                    assertThat(document.getCustomerId()).isEqualTo("customer-1");
                });
    }

    @Test
    void ignoresIrrelevantAuditedEventsWithoutDeliverySideEffects() {
        NotificationRoutingService service = service();

        NotificationRoutingResult result = service.route(request("CatalogProductUpdated", 1, null));

        assertThat(result.outcome()).isEqualTo(NotificationRoutingResult.Outcome.IGNORED);
        assertThat(result.channels()).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void ignoresUnsupportedVersionsWithoutDeliverySideEffects() {
        NotificationRoutingService service = service();

        NotificationRoutingResult result = service.route(request("OrderCreated", 2, null));

        assertThat(result.outcome()).isEqualTo(NotificationRoutingResult.Outcome.IGNORED);
        assertThat(result.channels()).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void treatsExistingPendingRecordsAsDuplicateReplaySkip() {
        NotificationDeliveryDocument email = delivery(NotificationChannel.EMAIL, NotificationDeliveryStatus.PENDING);
        NotificationDeliveryDocument push = delivery(NotificationChannel.PUSH, NotificationDeliveryStatus.PENDING);
        when(repository.findByEventIdAndChannel(EVENT_ID, NotificationChannel.EMAIL))
                .thenReturn(Optional.of(email));
        when(repository.findByEventIdAndChannel(EVENT_ID, NotificationChannel.PUSH))
                .thenReturn(Optional.of(push));
        NotificationRoutingService service = service();

        NotificationRoutingResult result = service.route(request("PaymentSucceeded", 1, "payment-1"));

        assertThat(result.channels())
                .extracting(NotificationRoutingResult.ChannelDeliveryResult::outcome)
                .containsExactly(
                        NotificationRoutingResult.ChannelOutcome.ALREADY_PENDING,
                        NotificationRoutingResult.ChannelOutcome.ALREADY_PENDING);
        verify(repository, never()).save(any(NotificationDeliveryDocument.class));
    }

    @Test
    void recoversMissingChannelOnReplayWithoutDuplicatingCompletedDelivery() {
        NotificationDeliveryDocument email = delivery(NotificationChannel.EMAIL, NotificationDeliveryStatus.SENT);
        when(repository.findByEventIdAndChannel(EVENT_ID, NotificationChannel.EMAIL))
                .thenReturn(Optional.of(email));
        when(repository.findByEventIdAndChannel(EVENT_ID, NotificationChannel.PUSH))
                .thenReturn(Optional.empty());
        when(repository.save(any(NotificationDeliveryDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        NotificationRoutingService service = service();

        NotificationRoutingResult result = service.route(request("PaymentFailed", 1, "payment-1"));

        assertThat(result.channels())
                .extracting(NotificationRoutingResult.ChannelDeliveryResult::outcome)
                .containsExactly(
                        NotificationRoutingResult.ChannelOutcome.ALREADY_SENT,
                        NotificationRoutingResult.ChannelOutcome.CREATED);
        ArgumentCaptor<NotificationDeliveryDocument> captor =
                ArgumentCaptor.forClass(NotificationDeliveryDocument.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getChannel()).isEqualTo(NotificationChannel.PUSH);
        assertThat(email.getStatus()).isEqualTo(NotificationDeliveryStatus.SENT);
    }

    @Test
    void requeuesFailedAndInterruptedDeliveriesForDeterministicRetry() {
        NotificationDeliveryDocument email = delivery(NotificationChannel.EMAIL, NotificationDeliveryStatus.FAILED);
        NotificationDeliveryDocument push = delivery(NotificationChannel.PUSH, NotificationDeliveryStatus.IN_PROGRESS);
        when(repository.findByEventIdAndChannel(EVENT_ID, NotificationChannel.EMAIL))
                .thenReturn(Optional.of(email));
        when(repository.findByEventIdAndChannel(EVENT_ID, NotificationChannel.PUSH))
                .thenReturn(Optional.of(push));
        NotificationRoutingService service = service();

        NotificationRoutingResult result = service.route(request("PaymentFailed", 1, "payment-1"));

        assertThat(result.channels())
                .extracting(NotificationRoutingResult.ChannelDeliveryResult::outcome)
                .containsExactly(
                        NotificationRoutingResult.ChannelOutcome.REQUEUED_FAILED,
                        NotificationRoutingResult.ChannelOutcome.REQUEUED_IN_PROGRESS);
        assertThat(email.getStatus()).isEqualTo(NotificationDeliveryStatus.PENDING);
        assertThat(push.getStatus()).isEqualTo(NotificationDeliveryStatus.PENDING);
        assertThat(email.getAttemptCount()).isEqualTo(1);
        assertThat(push.getAttemptCount()).isEqualTo(1);
        assertThat(email.getUpdatedAt()).isEqualTo(ROUTED_AT);
        assertThat(push.getUpdatedAt()).isEqualTo(ROUTED_AT);
        verify(repository).save(email);
        verify(repository).save(push);
    }

    @Test
    void recoversDuplicateKeyRaceByLoadingExistingChannelRecord() {
        NotificationDeliveryDocument email = delivery(NotificationChannel.EMAIL, NotificationDeliveryStatus.PENDING);
        when(repository.findByEventIdAndChannel(EVENT_ID, NotificationChannel.EMAIL))
                .thenReturn(Optional.empty(), Optional.of(email));
        when(repository.findByEventIdAndChannel(EVENT_ID, NotificationChannel.PUSH))
                .thenReturn(Optional.empty());
        when(repository.save(any(NotificationDeliveryDocument.class)))
                .thenThrow(new DuplicateKeyException("duplicate event/channel"))
                .thenAnswer(invocation -> invocation.getArgument(0));
        NotificationRoutingService service = service();

        NotificationRoutingResult result = service.route(request("OrderCreated", 1, null));

        assertThat(result.channels())
                .extracting(NotificationRoutingResult.ChannelDeliveryResult::outcome)
                .containsExactly(
                        NotificationRoutingResult.ChannelOutcome.ALREADY_PENDING,
                        NotificationRoutingResult.ChannelOutcome.CREATED);
    }

    private NotificationRoutingService service() {
        return new NotificationRoutingService(
                repository,
                Clock.fixed(ROUTED_AT, ZoneOffset.UTC));
    }

    private static NotificationRouteRequest request(String eventType, int eventVersion, String paymentId) {
        return new NotificationRouteRequest(
                EVENT_ID,
                eventType,
                eventVersion,
                "order-1",
                "customer-1",
                paymentId,
                EVENT_OCCURRED_AT,
                "trace-1",
                "corr-1");
    }

    private static NotificationDeliveryDocument delivery(
            NotificationChannel channel,
            NotificationDeliveryStatus status) {
        return new NotificationDeliveryDocument(
                EVENT_ID,
                channel,
                "PaymentFailed",
                1,
                "order-1",
                "customer-1",
                "payment-1",
                status,
                status == NotificationDeliveryStatus.PENDING ? 0 : 1,
                status == NotificationDeliveryStatus.PENDING ? null : ATTEMPTED_AT,
                status == NotificationDeliveryStatus.FAILED ? "adapter_unavailable" : null,
                EVENT_OCCURRED_AT,
                ROUTED_AT.minusSeconds(60),
                ROUTED_AT.minusSeconds(30),
                "trace-1",
                "corr-1");
    }
}
