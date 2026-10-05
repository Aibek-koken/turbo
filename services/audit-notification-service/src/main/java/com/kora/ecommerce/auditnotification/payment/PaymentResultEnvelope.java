package com.kora.ecommerce.auditnotification.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
record PaymentResultEnvelope(
        UUID eventId,
        String eventType,
        Integer eventVersion,
        UUID aggregateId,
        Instant occurredAt,
        String traceId,
        String correlationId,
        PaymentResultData data) {

    static final String PAYMENT_SUCCEEDED = "PaymentSucceeded";
    static final String PAYMENT_FAILED = "PaymentFailed";
    static final int SUPPORTED_EVENT_VERSION = 1;
    static final String STATUS_SUCCEEDED = "SUCCEEDED";
    static final String STATUS_FAILED = "FAILED";
    static final String OUTCOME_SUCCEEDED = "SUCCEEDED";
    static final String OUTCOME_DECLINED = "DECLINED";
    static final String OUTCOME_MALFORMED_RESPONSE = "MALFORMED_RESPONSE";

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PaymentResultData(
            UUID paymentId,
            UUID orderId,
            String customerId,
            BigDecimal amount,
            String currency,
            String paymentStatus,
            UUID providerAttemptId,
            String providerAttemptOutcome,
            String providerReference,
            String failureReason,
            UUID orderCreatedEventId) {
    }
}
