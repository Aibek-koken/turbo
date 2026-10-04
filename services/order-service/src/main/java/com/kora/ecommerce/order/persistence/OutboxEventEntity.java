package com.kora.ecommerce.order.persistence;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox_events")
public class OutboxEventEntity {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    @Column(name = "aggregate_type", nullable = false, updatable = false, length = 64)
    private String aggregateType;

    @Column(name = "event_type", nullable = false, updatable = false, length = 128)
    private String eventType;

    @Column(name = "event_version", nullable = false, updatable = false)
    private int eventVersion;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "trace_id", updatable = false, length = 128)
    private String traceId;

    @Column(name = "correlation_id", updatable = false, length = 128)
    private String correlationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "json")
    private JsonNode payload;

    protected OutboxEventEntity() {
    }

    private OutboxEventEntity(
            UUID id,
            UUID aggregateId,
            String aggregateType,
            String eventType,
            int eventVersion,
            Instant occurredAt,
            String traceId,
            String correlationId,
            JsonNode payload) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.aggregateId = Objects.requireNonNull(aggregateId, "aggregateId is required");
        this.aggregateType = Objects.requireNonNull(aggregateType, "aggregateType is required");
        this.eventType = Objects.requireNonNull(eventType, "eventType is required");
        this.eventVersion = eventVersion;
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt is required");
        this.traceId = traceId;
        this.correlationId = correlationId;
        this.payload = Objects.requireNonNull(payload, "payload is required");
    }

    public static OutboxEventEntity orderEvent(
            UUID eventId,
            UUID aggregateId,
            String eventType,
            int eventVersion,
            Instant occurredAt,
            String traceId,
            String correlationId,
            String payload) {
        return new OutboxEventEntity(
                eventId,
                aggregateId,
                "Order",
                eventType,
                eventVersion,
                occurredAt,
                traceId,
                correlationId,
                parsePayload(payload));
    }

    public UUID getId() {
        return id;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public String getEventType() {
        return eventType;
    }

    public int getEventVersion() {
        return eventVersion;
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

    public String getPayload() {
        return payload.toString();
    }

    private static JsonNode parsePayload(String payload) {
        try {
            return JSON_MAPPER.readTree(Objects.requireNonNull(payload, "payload is required"));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Outbox payload must be valid JSON.", exception);
        }
    }
}
