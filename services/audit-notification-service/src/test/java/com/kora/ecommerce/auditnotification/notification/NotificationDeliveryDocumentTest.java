package com.kora.ecommerce.auditnotification.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class NotificationDeliveryDocumentTest {

    private static final Instant EVENT_OCCURRED_AT = Instant.parse("2026-10-04T10:15:30Z");
    private static final Instant CREATED_AT = Instant.parse("2026-10-04T10:15:31Z");
    private static final Instant ATTEMPTED_AT = Instant.parse("2026-10-04T10:16:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-10-04T10:17:00Z");

    @Test
    void storesDeliveryLedgerMetadata() {
        NotificationDeliveryDocument document = new NotificationDeliveryDocument(
                "evt-1",
                NotificationChannel.EMAIL,
                "PaymentFailed",
                1,
                "order-1",
                "customer-1",
                "payment-1",
                NotificationDeliveryStatus.FAILED,
                2,
                ATTEMPTED_AT,
                " provider_declined ",
                EVENT_OCCURRED_AT,
                CREATED_AT,
                UPDATED_AT,
                "trace-1",
                "corr-1");

        assertThat(document.getEventId()).isEqualTo("evt-1");
        assertThat(document.getChannel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(document.getEventType()).isEqualTo("PaymentFailed");
        assertThat(document.getEventVersion()).isEqualTo(1);
        assertThat(document.getOrderId()).isEqualTo("order-1");
        assertThat(document.getCustomerId()).isEqualTo("customer-1");
        assertThat(document.getPaymentId()).isEqualTo("payment-1");
        assertThat(document.getStatus()).isEqualTo(NotificationDeliveryStatus.FAILED);
        assertThat(document.getAttemptCount()).isEqualTo(2);
        assertThat(document.getLastAttemptAt()).isEqualTo(ATTEMPTED_AT);
        assertThat(document.getSafeFailureDetail()).isEqualTo("provider_declined");
        assertThat(document.getEventOccurredAt()).isEqualTo(EVENT_OCCURRED_AT);
        assertThat(document.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(document.getUpdatedAt()).isEqualTo(UPDATED_AT);
        assertThat(document.getTraceId()).isEqualTo("trace-1");
        assertThat(document.getCorrelationId()).isEqualTo("corr-1");
    }

    @Test
    void pendingFactoryCreatesUnattemptedDeliveryRecord() {
        NotificationRouteRequest request = request("OrderCreated", null);

        NotificationDeliveryDocument document = NotificationDeliveryDocument.pending(
                request,
                NotificationChannel.PUSH,
                CREATED_AT);

        assertThat(document.getEventId()).isEqualTo(request.eventId());
        assertThat(document.getChannel()).isEqualTo(NotificationChannel.PUSH);
        assertThat(document.getStatus()).isEqualTo(NotificationDeliveryStatus.PENDING);
        assertThat(document.getAttemptCount()).isZero();
        assertThat(document.getLastAttemptAt()).isNull();
        assertThat(document.getSafeFailureDetail()).isNull();
        assertThat(document.getPaymentId()).isNull();
        assertThat(document.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(document.getUpdatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void retryableStatusesCanBeReturnedToPendingWithoutLosingAttemptMetadata() {
        NotificationDeliveryDocument failed = failedDelivery();

        failed.markPendingForRetry(UPDATED_AT);

        assertThat(failed.getStatus()).isEqualTo(NotificationDeliveryStatus.PENDING);
        assertThat(failed.getAttemptCount()).isEqualTo(2);
        assertThat(failed.getLastAttemptAt()).isEqualTo(ATTEMPTED_AT);
        assertThat(failed.getSafeFailureDetail()).isEqualTo("provider_declined");
        assertThat(failed.getUpdatedAt()).isEqualTo(UPDATED_AT);
    }

    @Test
    void completedDeliveriesAreNotMadePendingByReplayRouting() {
        NotificationDeliveryDocument sent = delivery(NotificationDeliveryStatus.SENT, "already_sent");

        sent.markPendingForRetry(UPDATED_AT);

        assertThat(sent.getStatus()).isEqualTo(NotificationDeliveryStatus.SENT);
        assertThat(sent.getUpdatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void attemptStateTransitionsMaintainSafeMetadata() {
        NotificationDeliveryDocument document = pendingDelivery();

        document.markInProgress(ATTEMPTED_AT);
        assertThat(document.getStatus()).isEqualTo(NotificationDeliveryStatus.IN_PROGRESS);
        assertThat(document.getAttemptCount()).isEqualTo(1);
        assertThat(document.getLastAttemptAt()).isEqualTo(ATTEMPTED_AT);

        document.markFailed(" adapter unavailable ", UPDATED_AT);
        assertThat(document.getStatus()).isEqualTo(NotificationDeliveryStatus.FAILED);
        assertThat(document.getSafeFailureDetail()).isEqualTo("adapter unavailable");

        document.markSent(UPDATED_AT.plusSeconds(5));
        assertThat(document.getStatus()).isEqualTo(NotificationDeliveryStatus.SENT);
        assertThat(document.getSafeFailureDetail()).isNull();
    }

    @Test
    void rejectsInvalidRequiredMetadata() {
        assertThatThrownBy(() -> new NotificationDeliveryDocument(
                        " ",
                        NotificationChannel.EMAIL,
                        "OrderCreated",
                        1,
                        "order-1",
                        "customer-1",
                        null,
                        NotificationDeliveryStatus.PENDING,
                        0,
                        null,
                        null,
                        EVENT_OCCURRED_AT,
                        CREATED_AT,
                        CREATED_AT,
                        "trace-1",
                        "corr-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventId");
    }

    @Test
    void rejectsInvalidAttemptMetadata() {
        assertThatThrownBy(() -> deliveryWithAttemptCount(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attemptCount");

        assertThatThrownBy(() -> deliveryWithSafeFailureDetail("x".repeat(513)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("safeFailureDetail");
    }

    private static NotificationDeliveryDocument pendingDelivery() {
        return new NotificationDeliveryDocument(
                "evt-1",
                NotificationChannel.EMAIL,
                "OrderCreated",
                1,
                "order-1",
                "customer-1",
                null,
                NotificationDeliveryStatus.PENDING,
                0,
                null,
                null,
                EVENT_OCCURRED_AT,
                CREATED_AT,
                CREATED_AT,
                "trace-1",
                "corr-1");
    }

    private static NotificationDeliveryDocument failedDelivery() {
        return delivery(NotificationDeliveryStatus.FAILED, "provider_declined");
    }

    private static NotificationDeliveryDocument delivery(
            NotificationDeliveryStatus status,
            String safeFailureDetail) {
        return new NotificationDeliveryDocument(
                "evt-1",
                NotificationChannel.EMAIL,
                "PaymentFailed",
                1,
                "order-1",
                "customer-1",
                "payment-1",
                status,
                2,
                ATTEMPTED_AT,
                safeFailureDetail,
                EVENT_OCCURRED_AT,
                CREATED_AT,
                CREATED_AT,
                "trace-1",
                "corr-1");
    }

    private static NotificationDeliveryDocument deliveryWithAttemptCount(int attemptCount) {
        return new NotificationDeliveryDocument(
                "evt-1",
                NotificationChannel.EMAIL,
                "OrderCreated",
                1,
                "order-1",
                "customer-1",
                null,
                NotificationDeliveryStatus.PENDING,
                attemptCount,
                null,
                null,
                EVENT_OCCURRED_AT,
                CREATED_AT,
                CREATED_AT,
                "trace-1",
                "corr-1");
    }

    private static NotificationDeliveryDocument deliveryWithSafeFailureDetail(String safeFailureDetail) {
        return new NotificationDeliveryDocument(
                "evt-1",
                NotificationChannel.EMAIL,
                "OrderCreated",
                1,
                "order-1",
                "customer-1",
                null,
                NotificationDeliveryStatus.FAILED,
                1,
                ATTEMPTED_AT,
                safeFailureDetail,
                EVENT_OCCURRED_AT,
                CREATED_AT,
                CREATED_AT,
                "trace-1",
                "corr-1");
    }

    private static NotificationRouteRequest request(String eventType, String paymentId) {
        return new NotificationRouteRequest(
                "evt-1",
                eventType,
                1,
                "order-1",
                "customer-1",
                paymentId,
                EVENT_OCCURRED_AT,
                "trace-1",
                "corr-1");
    }
}
