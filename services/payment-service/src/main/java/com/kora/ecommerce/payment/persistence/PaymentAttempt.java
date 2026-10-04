package com.kora.ecommerce.payment.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "payment_attempts")
public class PaymentAttempt {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Column(name = "attempt_number", nullable = false, updatable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentAttemptStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentAttemptOutcome outcome;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "provider_request_id", updatable = false)
    private UUID providerRequestId;

    @Column(name = "provider_reference", length = 128)
    private String providerReference;

    @Column(name = "failure_reason", length = 512)
    private String failureReason;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PaymentAttempt() {
    }

    private PaymentAttempt(
            UUID id,
            UUID paymentId,
            int attemptNumber,
            PaymentAttemptStatus status,
            BigDecimal amount,
            String currency,
            UUID providerRequestId,
            Instant requestedAt) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.paymentId = Objects.requireNonNull(paymentId, "paymentId is required");
        if (attemptNumber <= 0) {
            throw new IllegalArgumentException("attemptNumber must be positive");
        }
        this.attemptNumber = attemptNumber;
        this.status = Objects.requireNonNull(status, "status is required");
        this.outcome = PaymentAttemptOutcome.REQUESTED;
        this.amount = Payment.requirePositiveAmount(amount);
        this.currency = Payment.requireCurrency(currency);
        this.providerRequestId = providerRequestId;
        this.requestedAt = Objects.requireNonNull(requestedAt, "requestedAt is required");
        this.createdAt = requestedAt;
    }

    public static PaymentAttempt pending(
            UUID id,
            UUID paymentId,
            int attemptNumber,
            BigDecimal amount,
            String currency,
            UUID providerRequestId,
            Instant requestedAt) {
        return new PaymentAttempt(
                id,
                paymentId,
                attemptNumber,
                PaymentAttemptStatus.PENDING,
                amount,
                currency,
                providerRequestId,
                requestedAt);
    }

    public void markSucceeded(String providerReference, Instant completedAt) {
        this.status = PaymentAttemptStatus.SUCCEEDED;
        this.outcome = PaymentAttemptOutcome.SUCCEEDED;
        this.providerReference = requireText(providerReference, "providerReference");
        this.failureReason = null;
        this.completedAt = Objects.requireNonNull(completedAt, "completedAt is required");
    }

    public void markFailed(String failureReason, Instant completedAt) {
        markFailed(PaymentAttemptOutcome.MALFORMED_RESPONSE, failureReason, null, completedAt);
    }

    public void markFailed(
            PaymentAttemptOutcome outcome,
            String failureReason,
            String providerReference,
            Instant completedAt) {
        this.status = PaymentAttemptStatus.FAILED;
        this.outcome = requireFailureOutcome(outcome);
        this.providerReference = normalizeOptional(providerReference);
        this.failureReason = requireText(failureReason, "failureReason");
        this.completedAt = Objects.requireNonNull(completedAt, "completedAt is required");
    }

    public UUID getId() {
        return id;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public PaymentAttemptStatus getStatus() {
        return status;
    }

    public PaymentAttemptOutcome getOutcome() {
        return outcome;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public UUID getProviderRequestId() {
        return providerRequestId;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @PrePersist
    void beforeInsert() {
        Instant now = Instant.now();
        if (outcome == null) {
            outcome = PaymentAttemptOutcome.REQUESTED;
        }
        if (requestedAt == null) {
            requestedAt = now;
        }
        if (createdAt == null) {
            createdAt = requestedAt;
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private static PaymentAttemptOutcome requireFailureOutcome(PaymentAttemptOutcome outcome) {
        Objects.requireNonNull(outcome, "outcome is required");
        if (outcome == PaymentAttemptOutcome.REQUESTED || outcome == PaymentAttemptOutcome.SUCCEEDED) {
            throw new IllegalArgumentException("failure outcome is required");
        }
        return outcome;
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
