package com.kora.ecommerce.payment.application;

import java.util.Objects;
import java.util.UUID;

import com.kora.ecommerce.payment.order.OrderCreatedEvent;
import com.kora.ecommerce.payment.persistence.PaymentAttemptOutcome;

public record OrderCreatedPaymentResult(
        Outcome outcome,
        UUID eventId,
        UUID orderId,
        UUID paymentId,
        UUID attemptId,
        PaymentAttemptOutcome attemptOutcome) {

    public enum Outcome {
        PROVIDER_APPROVED,
        PROVIDER_DECLINED,
        PROVIDER_RETRYABLE_FAILURE,
        PROVIDER_MALFORMED_RESPONSE,
        DUPLICATE_EVENT,
        PAYMENT_ALREADY_EXISTS
    }

    public OrderCreatedPaymentResult {
        Objects.requireNonNull(outcome, "outcome is required");
        Objects.requireNonNull(eventId, "eventId is required");
        Objects.requireNonNull(orderId, "orderId is required");
    }

    public static OrderCreatedPaymentResult providerApproved(
            OrderCreatedEvent event,
            UUID paymentId,
            UUID attemptId) {
        Objects.requireNonNull(paymentId, "paymentId is required");
        Objects.requireNonNull(attemptId, "attemptId is required");
        return new OrderCreatedPaymentResult(
                Outcome.PROVIDER_APPROVED,
                event.eventId(),
                event.orderId(),
                paymentId,
                attemptId,
                PaymentAttemptOutcome.SUCCEEDED);
    }

    public static OrderCreatedPaymentResult providerDeclined(
            OrderCreatedEvent event,
            UUID paymentId,
            UUID attemptId) {
        Objects.requireNonNull(paymentId, "paymentId is required");
        Objects.requireNonNull(attemptId, "attemptId is required");
        return new OrderCreatedPaymentResult(
                Outcome.PROVIDER_DECLINED,
                event.eventId(),
                event.orderId(),
                paymentId,
                attemptId,
                PaymentAttemptOutcome.DECLINED);
    }

    public static OrderCreatedPaymentResult providerRetryableFailure(
            OrderCreatedEvent event,
            UUID paymentId,
            UUID attemptId,
            PaymentAttemptOutcome attemptOutcome) {
        Objects.requireNonNull(paymentId, "paymentId is required");
        Objects.requireNonNull(attemptId, "attemptId is required");
        if (attemptOutcome != PaymentAttemptOutcome.TIMED_OUT
                && attemptOutcome != PaymentAttemptOutcome.PROVIDER_5XX) {
            throw new IllegalArgumentException("retryable provider attempt outcome is required");
        }
        return new OrderCreatedPaymentResult(
                Outcome.PROVIDER_RETRYABLE_FAILURE,
                event.eventId(),
                event.orderId(),
                paymentId,
                attemptId,
                attemptOutcome);
    }

    public static OrderCreatedPaymentResult providerMalformedResponse(
            OrderCreatedEvent event,
            UUID paymentId,
            UUID attemptId) {
        Objects.requireNonNull(paymentId, "paymentId is required");
        Objects.requireNonNull(attemptId, "attemptId is required");
        return new OrderCreatedPaymentResult(
                Outcome.PROVIDER_MALFORMED_RESPONSE,
                event.eventId(),
                event.orderId(),
                paymentId,
                attemptId,
                PaymentAttemptOutcome.MALFORMED_RESPONSE);
    }

    public static OrderCreatedPaymentResult duplicateEvent(OrderCreatedEvent event) {
        return new OrderCreatedPaymentResult(
                Outcome.DUPLICATE_EVENT,
                event.eventId(),
                event.orderId(),
                null,
                null,
                null);
    }

    public static OrderCreatedPaymentResult paymentAlreadyExists(OrderCreatedEvent event, UUID paymentId) {
        Objects.requireNonNull(paymentId, "paymentId is required");
        return new OrderCreatedPaymentResult(
                Outcome.PAYMENT_ALREADY_EXISTS,
                event.eventId(),
                event.orderId(),
                paymentId,
                null,
                null);
    }
}
