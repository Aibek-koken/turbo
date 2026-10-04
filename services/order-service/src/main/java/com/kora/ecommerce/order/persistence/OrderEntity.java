package com.kora.ecommerce.order.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "orders")
public class OrderEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "customer_id", nullable = false, updatable = false, length = 128)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private OrderStatus status;

    @Column(name = "subtotal_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal subtotalAmount;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @OneToMany(mappedBy = "order", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @OrderBy("itemNumber ASC")
    private List<OrderItemEntity> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @OrderBy("changedAt ASC")
    private List<OrderStatusHistoryEntity> statusHistory = new ArrayList<>();

    protected OrderEntity() {
    }

    private OrderEntity(
            UUID id,
            String customerId,
            OrderStatus status,
            BigDecimal subtotalAmount,
            BigDecimal totalAmount,
            String currency,
            Instant createdAt,
            Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.customerId = Objects.requireNonNull(customerId, "customerId is required");
        this.status = Objects.requireNonNull(status, "status is required");
        this.subtotalAmount = Objects.requireNonNull(subtotalAmount, "subtotalAmount is required");
        this.totalAmount = Objects.requireNonNull(totalAmount, "totalAmount is required");
        this.currency = Objects.requireNonNull(currency, "currency is required");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt is required");
    }

    public static OrderEntity create(
            UUID id,
            String customerId,
            BigDecimal subtotalAmount,
            BigDecimal totalAmount,
            String currency,
            Instant createdAt) {
        return new OrderEntity(
                id,
                customerId,
                OrderStatus.CREATED,
                subtotalAmount,
                totalAmount,
                currency,
                createdAt,
                createdAt);
    }

    public void addItem(OrderItemEntity item) {
        OrderItemEntity requiredItem = Objects.requireNonNull(item, "item is required");
        requiredItem.attachTo(this);
        items.add(requiredItem);
    }

    public void appendStatusHistory(OrderStatus status, Instant changedAt, String reason) {
        OrderStatusHistoryEntity history = OrderStatusHistoryEntity.record(status, changedAt, reason);
        history.attachTo(this);
        statusHistory.add(history);
    }

    public void transitionTo(OrderStatus nextStatus, Instant changedAt, String reason) {
        this.status = Objects.requireNonNull(nextStatus, "nextStatus is required");
        this.updatedAt = Objects.requireNonNull(changedAt, "changedAt is required");
        appendStatusHistory(nextStatus, changedAt, reason);
    }

    public UUID getId() {
        return id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getSubtotalAmount() {
        return subtotalAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    public List<OrderItemEntity> getItems() {
        return Collections.unmodifiableList(items);
    }

    public List<OrderStatusHistoryEntity> getStatusHistory() {
        return Collections.unmodifiableList(statusHistory);
    }
}
