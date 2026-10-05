package com.kora.ecommerce.auditnotification.persistence;

import java.time.Instant;
import java.util.Objects;

import org.bson.Document;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Field;

@org.springframework.data.mongodb.core.mapping.Document(collection = "audit_events")
public class AuditEventDocument {

    @Id
    private String id;

    @Field("event_id")
    @Indexed(name = "ux_audit_events_event_id", unique = true)
    private String eventId;

    @Field("event_type")
    @Indexed(name = "ix_audit_events_event_type")
    private String eventType;

    @Field("event_version")
    private int eventVersion;

    @Field("aggregate_id")
    @Indexed(name = "ix_audit_events_aggregate_id")
    private String aggregateId;

    @Field("order_id")
    @Indexed(name = "ix_audit_events_order_id")
    private String orderId;

    @Field("customer_id")
    @Indexed(name = "ix_audit_events_customer_id")
    private String customerId;

    @Field("payment_id")
    @Indexed(name = "ix_audit_events_payment_id")
    private String paymentId;

    @Field("occurred_at")
    @Indexed(name = "ix_audit_events_occurred_at")
    private Instant occurredAt;

    @Field("received_at")
    private Instant receivedAt;

    @Field("trace_id")
    private String traceId;

    @Field("correlation_id")
    @Indexed(name = "ix_audit_events_correlation_id")
    private String correlationId;

    @Field("source_topic")
    private String sourceTopic;

    @Field("source_partition")
    private int sourcePartition;

    @Field("source_offset")
    private long sourceOffset;

    @Field("payload")
    private Document payload;

    protected AuditEventDocument() {
    }

    public AuditEventDocument(
            String eventId,
            String eventType,
            int eventVersion,
            String aggregateId,
            String orderId,
            String customerId,
            String paymentId,
            Instant occurredAt,
            Instant receivedAt,
            String traceId,
            String correlationId,
            String sourceTopic,
            int sourcePartition,
            long sourceOffset,
            Document payload) {
        this(
                null,
                eventId,
                eventType,
                eventVersion,
                aggregateId,
                orderId,
                customerId,
                paymentId,
                occurredAt,
                receivedAt,
                traceId,
                correlationId,
                sourceTopic,
                sourcePartition,
                sourceOffset,
                payload);
    }

    public AuditEventDocument(
            String id,
            String eventId,
            String eventType,
            int eventVersion,
            String aggregateId,
            String orderId,
            String customerId,
            String paymentId,
            Instant occurredAt,
            Instant receivedAt,
            String traceId,
            String correlationId,
            String sourceTopic,
            int sourcePartition,
            long sourceOffset,
            Document payload) {
        this.id = id;
        this.eventId = requireText(eventId, "eventId");
        this.eventType = requireText(eventType, "eventType");
        if (eventVersion < 1) {
            throw new IllegalArgumentException("eventVersion must be positive");
        }
        this.eventVersion = eventVersion;
        this.aggregateId = requireText(aggregateId, "aggregateId");
        this.orderId = optionalText(orderId);
        this.customerId = optionalText(customerId);
        this.paymentId = optionalText(paymentId);
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        this.receivedAt = Objects.requireNonNull(receivedAt, "receivedAt must not be null");
        this.traceId = requireText(traceId, "traceId");
        this.correlationId = requireText(correlationId, "correlationId");
        this.sourceTopic = requireText(sourceTopic, "sourceTopic");
        if (sourcePartition < 0) {
            throw new IllegalArgumentException("sourcePartition must not be negative");
        }
        this.sourcePartition = sourcePartition;
        if (sourceOffset < 0) {
            throw new IllegalArgumentException("sourceOffset must not be negative");
        }
        this.sourceOffset = sourceOffset;
        this.payload = new Document(Objects.requireNonNull(payload, "payload must not be null"));
    }

    public String getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public int getEventVersion() {
        return eventVersion;
    }

    public String getAggregateId() {
        return aggregateId;
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

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getSourceTopic() {
        return sourceTopic;
    }

    public int getSourcePartition() {
        return sourcePartition;
    }

    public long getSourceOffset() {
        return sourceOffset;
    }

    public Document getPayload() {
        return new Document(payload);
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
}
