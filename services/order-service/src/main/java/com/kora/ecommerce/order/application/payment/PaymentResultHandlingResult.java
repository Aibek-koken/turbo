package com.kora.ecommerce.order.application.payment;

import java.util.List;
import java.util.UUID;

import com.kora.ecommerce.order.persistence.OrderStatus;

public record PaymentResultHandlingResult(
        PaymentResultHandlingStatus status,
        UUID eventId,
        UUID orderId,
        UUID paymentId,
        OrderStatus previousStatus,
        OrderStatus currentStatus,
        List<OrderStatus> appliedTransitions) {

    static PaymentResultHandlingResult processed(
            PaymentResultEnvelope event,
            OrderStatus previousStatus,
            OrderStatus currentStatus,
            List<OrderStatus> appliedTransitions) {
        return new PaymentResultHandlingResult(
                PaymentResultHandlingStatus.PROCESSED,
                event.eventId(),
                event.orderId(),
                event.paymentId(),
                previousStatus,
                currentStatus,
                List.copyOf(appliedTransitions));
    }

    static PaymentResultHandlingResult duplicate(PaymentResultEnvelope event) {
        return skipped(PaymentResultHandlingStatus.DUPLICATE, event, null, null);
    }

    static PaymentResultHandlingResult missingOrder(PaymentResultEnvelope event) {
        return skipped(PaymentResultHandlingStatus.MISSING_ORDER, event, null, null);
    }

    static PaymentResultHandlingResult alreadyTerminal(PaymentResultEnvelope event, OrderStatus status) {
        return skipped(PaymentResultHandlingStatus.ALREADY_TERMINAL, event, status, status);
    }

    static PaymentResultHandlingResult invalidTransition(
            PaymentResultEnvelope event,
            OrderStatus previousStatus,
            OrderStatus targetStatus) {
        return skipped(PaymentResultHandlingStatus.INVALID_TRANSITION, event, previousStatus, targetStatus);
    }

    private static PaymentResultHandlingResult skipped(
            PaymentResultHandlingStatus status,
            PaymentResultEnvelope event,
            OrderStatus previousStatus,
            OrderStatus currentStatus) {
        return new PaymentResultHandlingResult(
                status,
                event.eventId(),
                event.orderId(),
                event.paymentId(),
                previousStatus,
                currentStatus,
                List.of());
    }
}
