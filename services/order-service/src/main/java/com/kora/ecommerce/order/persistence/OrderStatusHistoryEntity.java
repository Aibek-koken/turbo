package com.kora.ecommerce.order.persistence;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_status_history")
public class OrderStatusHistoryEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderEntity order;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, updatable = false, length = 32)
    private OrderStatus status;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    @Column(name = "reason", length = 500)
    private String reason;

    protected OrderStatusHistoryEntity() {
    }

    private OrderStatusHistoryEntity(UUID id, OrderStatus status, Instant changedAt, String reason) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.status = Objects.requireNonNull(status, "status is required");
        this.changedAt = Objects.requireNonNull(changedAt, "changedAt is required");
        this.reason = reason;
    }

    public static OrderStatusHistoryEntity record(OrderStatus status, Instant changedAt, String reason) {
        return new OrderStatusHistoryEntity(UUID.randomUUID(), status, changedAt, reason);
    }

    void attachTo(OrderEntity order) {
        this.order = Objects.requireNonNull(order, "order is required");
    }

    public UUID getId() {
        return id;
    }

    public OrderEntity getOrder() {
        return order;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public String getReason() {
        return reason;
    }
}
