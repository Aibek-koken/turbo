package com.kora.ecommerce.payment.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.kora.ecommerce.payment.persistence.Payment;
import com.kora.ecommerce.payment.persistence.PaymentAttempt;
import io.swagger.v3.oas.annotations.media.Schema;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    @Schema(description = "Payment status and safe attempt history for one order payment.")
    public record PaymentResponse(
            UUID paymentId,
            UUID orderId,
            String customerId,
            String status,
            BigDecimal amount,
            String currency,
            String providerReference,
            String failureReason,
            Instant createdAt,
            Instant updatedAt,
            List<PaymentAttemptResponse> attempts) {

        static PaymentResponse from(Payment payment, List<PaymentAttempt> attempts) {
            return new PaymentResponse(
                    payment.getId(),
                    payment.getOrderId(),
                    payment.getCustomerId(),
                    payment.getStatus().name(),
                    payment.getAmount(),
                    payment.getCurrency(),
                    payment.getProviderReference(),
                    payment.getFailureReason(),
                    payment.getCreatedAt(),
                    payment.getUpdatedAt(),
                    attempts.stream()
                            .map(PaymentAttemptResponse::from)
                            .toList());
        }
    }

    @Schema(description = "A safe view of a payment provider attempt.")
    public record PaymentAttemptResponse(
            UUID attemptId,
            int attemptNumber,
            String status,
            String outcome,
            BigDecimal amount,
            String currency,
            String providerReference,
            String failureReason,
            Instant requestedAt,
            Instant completedAt) {

        static PaymentAttemptResponse from(PaymentAttempt attempt) {
            return new PaymentAttemptResponse(
                    attempt.getId(),
                    attempt.getAttemptNumber(),
                    attempt.getStatus().name(),
                    attempt.getOutcome().name(),
                    attempt.getAmount(),
                    attempt.getCurrency(),
                    attempt.getProviderReference(),
                    attempt.getFailureReason(),
                    attempt.getRequestedAt(),
                    attempt.getCompletedAt());
        }
    }
}
