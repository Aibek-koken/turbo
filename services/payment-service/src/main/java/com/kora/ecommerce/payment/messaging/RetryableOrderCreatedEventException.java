package com.kora.ecommerce.payment.messaging;

import java.util.Objects;

import com.kora.ecommerce.payment.application.OrderCreatedPaymentResult;

public class RetryableOrderCreatedEventException extends RuntimeException {

    private final OrderCreatedPaymentResult result;

    RetryableOrderCreatedEventException(OrderCreatedPaymentResult result) {
        super("Retryable OrderCreated processing failure: eventId=%s orderId=%s attemptOutcome=%s"
                .formatted(result.eventId(), result.orderId(), result.attemptOutcome()));
        this.result = Objects.requireNonNull(result, "result is required");
    }

    public OrderCreatedPaymentResult result() {
        return result;
    }
}
