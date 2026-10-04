package com.kora.ecommerce.order.application.payment;

import java.time.Instant;
import java.util.UUID;

import com.kora.ecommerce.order.persistence.OrderStatus;

public sealed interface PaymentResultEnvelope permits PaymentSucceededEnvelope, PaymentFailedEnvelope {

    int SUPPORTED_EVENT_VERSION = 1;

    UUID eventId();

    int eventVersion();

    UUID aggregateId();

    Instant occurredAt();

    String traceId();

    String correlationId();

    PaymentResultData data();

    String eventType();

    OrderStatus targetStatus();

    default UUID orderId() {
        return data().orderId();
    }

    default UUID paymentId() {
        return data().paymentId();
    }
}
