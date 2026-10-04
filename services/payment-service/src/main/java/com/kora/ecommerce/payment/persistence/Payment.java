package com.kora.ecommerce.payment.persistence;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentStatus status;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "provider_reference", length = 128)
    private String providerReference;

    @Column(name = "failure_reason", length = 512)
    private String failureReason;

    @Column(name = "source_event_id", nullable = false, updatable = false)
    private UUID sourceEventId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected Payment() {
    }

    private Payment(
            UUID id,
            UUID orderId,
            String customerId,
            PaymentStatus status,
            BigDecimal amount,
            String currency,
            UUID sourceEventId,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.orderId = Objects.requireNonNull(orderId, "orderId is required");
        this.customerId = requireCustomerId(customerId);
        this.status = Objects.requireNonNull(status, "status is required");
        this.amount = requirePositiveAmount(amount);
        this.currency = requireCurrency(currency);
        this.sourceEventId = Objects.requireNonNull(sourceEventId, "sourceEventId is required");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
        this.updatedAt = createdAt;
    }

    public static Payment pending(
            UUID id,
            UUID orderId,
            String customerId,
            BigDecimal amount,
            String currency,
            UUID sourceEventId,
            Instant createdAt) {
        return new Payment(id, orderId, customerId, PaymentStatus.PENDING, amount, currency, sourceEventId, createdAt);
    }

    public void markSucceeded(String providerReference, Instant updatedAt) {
        this.status = PaymentStatus.SUCCEEDED;
        this.providerReference = requireText(providerReference, "providerReference");
        this.failureReason = null;
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt is required");
    }

    public void markFailed(String failureReason, Instant updatedAt) {
        this.status = PaymentStatus.FAILED;
        this.providerReference = null;
        this.failureReason = requireText(failureReason, "failureReason");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt is required");
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public UUID getSourceEventId() {
        return sourceEventId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    @PrePersist
    void beforeInsert() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    @PreUpdate
    void beforeUpdate() {
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    static BigDecimal requirePositiveAmount(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount is required");
        BigDecimal scaled = amount.setScale(4, RoundingMode.UNNECESSARY);
        if (scaled.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        return scaled;
    }

    static String requireCurrency(String currency) {
        String normalized = requireText(currency, "currency");
        if (!normalized.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("currency must be an uppercase ISO-4217 code");
        }
        return normalized;
    }

    static String requireCustomerId(String customerId) {
        String normalized = requireText(customerId, "customerId");
        if (normalized.length() > 128) {
            throw new IllegalArgumentException("customerId must be at most 128 characters");
        }
        return normalized;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}
