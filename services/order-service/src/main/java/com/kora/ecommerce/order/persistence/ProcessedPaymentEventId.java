package com.kora.ecommerce.order.persistence;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class ProcessedPaymentEventId implements Serializable {

    @Column(name = "consumer_name", nullable = false, updatable = false, length = 128)
    private String consumerName;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    protected ProcessedPaymentEventId() {
    }

    private ProcessedPaymentEventId(String consumerName, UUID eventId) {
        this.consumerName = Objects.requireNonNull(consumerName, "consumerName is required");
        this.eventId = Objects.requireNonNull(eventId, "eventId is required");
    }

    public static ProcessedPaymentEventId of(String consumerName, UUID eventId) {
        return new ProcessedPaymentEventId(consumerName, eventId);
    }

    public String getConsumerName() {
        return consumerName;
    }

    public UUID getEventId() {
        return eventId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProcessedPaymentEventId that)) {
            return false;
        }
        return Objects.equals(consumerName, that.consumerName)
                && Objects.equals(eventId, that.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(consumerName, eventId);
    }
}
