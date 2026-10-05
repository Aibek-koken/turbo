package com.kora.ecommerce.auditnotification.notification;

import java.time.Instant;
import java.util.Objects;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Field;

@org.springframework.data.mongodb.core.mapping.Document(collection = "notification_deliveries")
@CompoundIndex(
        name = "ux_notification_deliveries_event_channel",
        def = "{'event_id': 1, 'channel': 1}",
        unique = true)
public class NotificationDeliveryDocument {

    private static final int MAX_SAFE_FAILURE_DETAIL_LENGTH = 512;

    @Id
    private String id;

    @Field("event_id")
    @Indexed(name = "ix_notification_deliveries_event_id")
    private String eventId;

    @Field("channel")
    @Indexed(name = "ix_notification_deliveries_channel")
    private NotificationChannel channel;

    @Field("event_type")
    @Indexed(name = "ix_notification_deliveries_event_type")
    private String eventType;

    @Field("event_version")
    private int eventVersion;

    @Field("order_id")
    @Indexed(name = "ix_notification_deliveries_order_id")
    private String orderId;

    @Field("customer_id")
    @Indexed(name = "ix_notification_deliveries_customer_id")
    private String customerId;

    @Field("payment_id")
    @Indexed(name = "ix_notification_deliveries_payment_id")
    private String paymentId;

    @Field("status")
    @Indexed(name = "ix_notification_deliveries_status")
    private NotificationDeliveryStatus status;

    @Field("attempt_count")
    private int attemptCount;

    @Field("last_attempt_at")
    private Instant lastAttemptAt;

    @Field("safe_failure_detail")
    private String safeFailureDetail;

    @Field("event_occurred_at")
    private Instant eventOccurredAt;

    @Field("created_at")
    private Instant createdAt;

    @Field("updated_at")
    private Instant updatedAt;

    @Field("trace_id")
    private String traceId;

    @Field("correlation_id")
    @Indexed(name = "ix_notification_deliveries_correlation_id")
    private String correlationId;

    protected NotificationDeliveryDocument() {
    }

    public NotificationDeliveryDocument(
            String eventId,
            NotificationChannel channel,
            String eventType,
            int eventVersion,
            String orderId,
            String customerId,
            String paymentId,
            NotificationDeliveryStatus status,
            int attemptCount,
            Instant lastAttemptAt,
            String safeFailureDetail,
            Instant eventOccurredAt,
            Instant createdAt,
            Instant updatedAt,
            String traceId,
            String correlationId) {
        this(
                null,
                eventId,
                channel,
                eventType,
                eventVersion,
                orderId,
                customerId,
                paymentId,
                status,
                attemptCount,
                lastAttemptAt,
                safeFailureDetail,
                eventOccurredAt,
                createdAt,
                updatedAt,
                traceId,
                correlationId);
    }

    public NotificationDeliveryDocument(
            String id,
            String eventId,
            NotificationChannel channel,
            String eventType,
            int eventVersion,
            String orderId,
            String customerId,
            String paymentId,
            NotificationDeliveryStatus status,
            int attemptCount,
            Instant lastAttemptAt,
            String safeFailureDetail,
            Instant eventOccurredAt,
            Instant createdAt,
            Instant updatedAt,
            String traceId,
            String correlationId) {
        this.id = id;
        this.eventId = requireText(eventId, "eventId");
        this.channel = Objects.requireNonNull(channel, "channel must not be null");
        this.eventType = requireText(eventType, "eventType");
        if (eventVersion < 1) {
            throw new IllegalArgumentException("eventVersion must be positive");
        }
        this.eventVersion = eventVersion;
        this.orderId = requireText(orderId, "orderId");
        this.customerId = requireText(customerId, "customerId");
        this.paymentId = optionalText(paymentId);
        this.status = Objects.requireNonNull(status, "status must not be null");
        if (attemptCount < 0) {
            throw new IllegalArgumentException("attemptCount must not be negative");
        }
        this.attemptCount = attemptCount;
        this.lastAttemptAt = lastAttemptAt;
        this.safeFailureDetail = normalizeSafeFailureDetail(safeFailureDetail);
        this.eventOccurredAt = Objects.requireNonNull(eventOccurredAt, "eventOccurredAt must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        this.traceId = requireText(traceId, "traceId");
        this.correlationId = requireText(correlationId, "correlationId");
    }

    static NotificationDeliveryDocument pending(
            NotificationRouteRequest request,
            NotificationChannel channel,
            Instant now) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(now, "now must not be null");
        return new NotificationDeliveryDocument(
                request.eventId(),
                channel,
                request.eventType(),
                request.eventVersion(),
                request.orderId(),
                request.customerId(),
                request.paymentId(),
                NotificationDeliveryStatus.PENDING,
                0,
                null,
                null,
                request.occurredAt(),
                now,
                now,
                request.traceId(),
                request.correlationId());
    }

    public String getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public NotificationChannel getChannel() {
        return channel;
    }

    public String getEventType() {
        return eventType;
    }

    public int getEventVersion() {
        return eventVersion;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public NotificationDeliveryStatus getStatus() {
        return status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Instant getLastAttemptAt() {
        return lastAttemptAt;
    }

    public String getSafeFailureDetail() {
        return safeFailureDetail;
    }

    public Instant getEventOccurredAt() {
        return eventOccurredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public boolean isRetryableForRoutingReplay() {
        return status == NotificationDeliveryStatus.IN_PROGRESS
                || status == NotificationDeliveryStatus.FAILED;
    }

    public void markPendingForRetry(Instant retriedAt) {
        Objects.requireNonNull(retriedAt, "retriedAt must not be null");
        if (!isRetryableForRoutingReplay()) {
            return;
        }
        this.status = NotificationDeliveryStatus.PENDING;
        this.updatedAt = retriedAt;
    }

    public void markInProgress(Instant attemptedAt) {
        Objects.requireNonNull(attemptedAt, "attemptedAt must not be null");
        this.status = NotificationDeliveryStatus.IN_PROGRESS;
        this.attemptCount++;
        this.lastAttemptAt = attemptedAt;
        this.safeFailureDetail = null;
        this.updatedAt = attemptedAt;
    }

    public void markSent(Instant sentAt) {
        Objects.requireNonNull(sentAt, "sentAt must not be null");
        this.status = NotificationDeliveryStatus.SENT;
        this.safeFailureDetail = null;
        this.updatedAt = sentAt;
    }

    public void markFailed(String safeFailureDetail, Instant failedAt) {
        Objects.requireNonNull(failedAt, "failedAt must not be null");
        this.status = NotificationDeliveryStatus.FAILED;
        this.safeFailureDetail = normalizeSafeFailureDetail(safeFailureDetail);
        this.updatedAt = failedAt;
    }

    private static String requireText(String value, String fieldName) {
        String normalized = optionalText(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

    private static String optionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String normalizeSafeFailureDetail(String value) {
        String normalized = optionalText(value);
        if (normalized == null) {
            return null;
        }
        if (normalized.length() > MAX_SAFE_FAILURE_DETAIL_LENGTH) {
            throw new IllegalArgumentException("safeFailureDetail must not exceed "
                    + MAX_SAFE_FAILURE_DETAIL_LENGTH + " characters");
        }
        return normalized;
    }
}
