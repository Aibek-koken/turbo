package com.kora.ecommerce.payment.persistence;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 128, updatable = false)
    private String eventType;

    @Column(name = "event_version", nullable = false, updatable = false)
    private int eventVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> payload;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "trace_id", length = 64, updatable = false)
    private String traceId;

    @Column(name = "correlation_id", length = 128, updatable = false)
    private String correlationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OutboxEvent() {
    }

    private OutboxEvent(
            UUID id,
            UUID aggregateId,
            String eventType,
            int eventVersion,
            Map<String, Object> payload,
            Instant occurredAt,
            String traceId,
            String correlationId) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.aggregateId = Objects.requireNonNull(aggregateId, "aggregateId is required");
        this.eventType = requireText(eventType, "eventType");
        if (eventVersion <= 0) {
            throw new IllegalArgumentException("eventVersion must be positive");
        }
        this.eventVersion = eventVersion;
        this.payload = new LinkedHashMap<>(Objects.requireNonNull(payload, "payload is required"));
        if (this.payload.isEmpty()) {
            throw new IllegalArgumentException("payload is required");
        }
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt is required");
        this.traceId = trimOptional(traceId);
        this.correlationId = trimOptional(correlationId);
        this.createdAt = occurredAt;
    }

    public static OutboxEvent paymentResult(
            UUID id,
            UUID aggregateId,
            String eventType,
            int eventVersion,
            Map<String, Object> payload,
            Instant occurredAt,
            String traceId,
            String correlationId) {
        return new OutboxEvent(
                id,
                aggregateId,
                eventType,
                eventVersion,
                payload,
                occurredAt,
                traceId,
                correlationId);
    }

    public UUID getId() {
        return id;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public int getEventVersion() {
        return eventVersion;
    }

    public Map<String, Object> getPayload() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @PrePersist
    void beforeInsert() {
        if (createdAt == null) {
            createdAt = occurredAt == null ? Instant.now() : occurredAt;
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private static String trimOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
