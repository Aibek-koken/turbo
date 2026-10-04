package com.kora.ecommerce.payment.persistence;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "consumer_name", nullable = false, length = 128, updatable = false)
    private String consumerName;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 128, updatable = false)
    private String eventType;

    @Column(name = "event_version", nullable = false, updatable = false)
    private int eventVersion;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    @Column(name = "trace_id", length = 64, updatable = false)
    private String traceId;

    @Column(name = "correlation_id", length = 128, updatable = false)
    private String correlationId;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ProcessedEvent() {
    }

    private ProcessedEvent(
            UUID id,
            String consumerName,
            UUID eventId,
            String eventType,
            int eventVersion,
            UUID aggregateId,
            String traceId,
            String correlationId,
            Instant processedAt) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.consumerName = requireText(consumerName, "consumerName");
        this.eventId = Objects.requireNonNull(eventId, "eventId is required");
        this.eventType = requireText(eventType, "eventType");
        if (eventVersion <= 0) {
            throw new IllegalArgumentException("eventVersion must be positive");
        }
        this.eventVersion = eventVersion;
        this.aggregateId = Objects.requireNonNull(aggregateId, "aggregateId is required");
        this.traceId = trimOptional(traceId);
        this.correlationId = trimOptional(correlationId);
        this.processedAt = Objects.requireNonNull(processedAt, "processedAt is required");
        this.createdAt = processedAt;
    }

    public static ProcessedEvent record(
            UUID id,
            String consumerName,
            UUID eventId,
            String eventType,
            int eventVersion,
            UUID aggregateId,
            String traceId,
            String correlationId,
            Instant processedAt) {
        return new ProcessedEvent(
                id,
                consumerName,
                eventId,
                eventType,
                eventVersion,
                aggregateId,
                traceId,
                correlationId,
                processedAt);
    }

    public UUID getId() {
        return id;
    }

    public String getConsumerName() {
        return consumerName;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public int getEventVersion() {
        return eventVersion;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @PrePersist
    void beforeInsert() {
        if (processedAt == null) {
            processedAt = Instant.now();
        }
        if (createdAt == null) {
            createdAt = processedAt;
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
