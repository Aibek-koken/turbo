package com.kora.ecommerce.order.persistence;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "processed_events")
public class ProcessedPaymentEventEntity {

    @EmbeddedId
    private ProcessedPaymentEventId id;

    @Column(name = "event_type", nullable = false, updatable = false, length = 128)
    private String eventType;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    protected ProcessedPaymentEventEntity() {
    }

    private ProcessedPaymentEventEntity(
            ProcessedPaymentEventId id,
            String eventType,
            UUID aggregateId,
            UUID paymentId,
            Instant processedAt) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.eventType = Objects.requireNonNull(eventType, "eventType is required");
        this.aggregateId = Objects.requireNonNull(aggregateId, "aggregateId is required");
        this.paymentId = Objects.requireNonNull(paymentId, "paymentId is required");
        this.processedAt = Objects.requireNonNull(processedAt, "processedAt is required");
    }

    public static ProcessedPaymentEventEntity record(
            String consumerName,
            UUID eventId,
            String eventType,
            UUID aggregateId,
            UUID paymentId,
            Instant processedAt) {
        return new ProcessedPaymentEventEntity(
                ProcessedPaymentEventId.of(consumerName, eventId),
                eventType,
                aggregateId,
                paymentId,
                processedAt);
    }

    public ProcessedPaymentEventId getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
