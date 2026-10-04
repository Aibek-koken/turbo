package com.kora.ecommerce.payment.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.kora.ecommerce.payment.order.OrderCreatedEvent;
import com.kora.ecommerce.payment.persistence.OutboxEvent;
import com.kora.ecommerce.payment.persistence.Payment;
import com.kora.ecommerce.payment.persistence.PaymentAttempt;

final class PaymentResultOutboxEventFactory {

    static final String PAYMENT_SUCCEEDED = "PaymentSucceeded";
    static final String PAYMENT_FAILED = "PaymentFailed";
    static final int EVENT_VERSION = 1;

    private PaymentResultOutboxEventFactory() {
    }

    static OutboxEvent paymentSucceeded(
            UUID eventId,
            OrderCreatedEvent sourceEvent,
            Payment payment,
            PaymentAttempt attempt,
            Instant occurredAt) {
        return paymentResult(eventId, PAYMENT_SUCCEEDED, sourceEvent, payment, attempt, occurredAt);
    }

    static OutboxEvent paymentFailed(
            UUID eventId,
            OrderCreatedEvent sourceEvent,
            Payment payment,
            PaymentAttempt attempt,
            Instant occurredAt) {
        return paymentResult(eventId, PAYMENT_FAILED, sourceEvent, payment, attempt, occurredAt);
    }

    private static OutboxEvent paymentResult(
            UUID eventId,
            String eventType,
            OrderCreatedEvent sourceEvent,
            Payment payment,
            PaymentAttempt attempt,
            Instant occurredAt) {
        Objects.requireNonNull(eventId, "eventId is required");
        Objects.requireNonNull(sourceEvent, "sourceEvent is required");
        Objects.requireNonNull(payment, "payment is required");
        Objects.requireNonNull(attempt, "attempt is required");
        Objects.requireNonNull(occurredAt, "occurredAt is required");

        UUID orderId = payment.getOrderId();
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId.toString());
        envelope.put("eventType", eventType);
        envelope.put("eventVersion", EVENT_VERSION);
        envelope.put("aggregateId", orderId.toString());
        envelope.put("occurredAt", occurredAt.toString());
        envelope.put("traceId", sourceEvent.traceId());
        envelope.put("correlationId", sourceEvent.correlationId());
        envelope.put("data", data(payment, attempt));

        return OutboxEvent.paymentResult(
                eventId,
                orderId,
                eventType,
                EVENT_VERSION,
                envelope,
                occurredAt,
                sourceEvent.traceId(),
                sourceEvent.correlationId());
    }

    private static Map<String, Object> data(Payment payment, PaymentAttempt attempt) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("paymentId", payment.getId().toString());
        data.put("orderId", payment.getOrderId().toString());
        data.put("customerId", payment.getCustomerId());
        data.put("amount", formatAmount(payment.getAmount()));
        data.put("currency", payment.getCurrency());
        data.put("paymentStatus", payment.getStatus().name());
        data.put("providerAttemptId", attempt.getId().toString());
        data.put("providerAttemptOutcome", attempt.getOutcome().name());
        data.put("providerReference", attempt.getProviderReference());
        data.put("failureReason", payment.getFailureReason());
        data.put("orderCreatedEventId", payment.getSourceEventId().toString());
        return data;
    }

    private static String formatAmount(BigDecimal amount) {
        return amount.toPlainString();
    }
}
