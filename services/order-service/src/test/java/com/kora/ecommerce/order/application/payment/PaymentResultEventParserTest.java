package com.kora.ecommerce.order.application.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import com.kora.ecommerce.order.persistence.OrderStatus;
import org.junit.jupiter.api.Test;

class PaymentResultEventParserTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-10-04T10:15:30Z");

    private final PaymentResultEventParser parser = new PaymentResultEventParser();

    @Test
    void parsesPaymentSucceededEnvelopeIntoOrderOwnedContract() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        PaymentResultEnvelope envelope = parser.parse(payload(
                eventId,
                PaymentSucceededEnvelope.EVENT_TYPE,
                1,
                orderId,
                paymentId));

        assertThat(envelope).isInstanceOf(PaymentSucceededEnvelope.class);
        assertThat(envelope.eventId()).isEqualTo(eventId);
        assertThat(envelope.eventType()).isEqualTo(PaymentSucceededEnvelope.EVENT_TYPE);
        assertThat(envelope.eventVersion()).isEqualTo(1);
        assertThat(envelope.aggregateId()).isEqualTo(orderId);
        assertThat(envelope.occurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(envelope.traceId()).isEqualTo("trace-123");
        assertThat(envelope.correlationId()).isEqualTo("correlation-123");
        assertThat(envelope.orderId()).isEqualTo(orderId);
        assertThat(envelope.paymentId()).isEqualTo(paymentId);
        assertThat(envelope.targetStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void parsesPaymentFailedEnvelopeIntoOrderOwnedContract() {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        PaymentResultEnvelope envelope = parser.parse(payload(
                UUID.randomUUID(),
                PaymentFailedEnvelope.EVENT_TYPE,
                1,
                orderId,
                paymentId));

        assertThat(envelope).isInstanceOf(PaymentFailedEnvelope.class);
        assertThat(envelope.targetStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
    }

    @Test
    void rejectsUnsupportedEventType() {
        assertPaymentResultFailure(
                payload(
                        UUID.randomUUID(),
                        "PaymentRefunded",
                        1,
                        UUID.randomUUID(),
                        UUID.randomUUID()),
                PaymentResultEventFailure.UNSUPPORTED_EVENT_TYPE);
    }

    @Test
    void rejectsUnsupportedEventVersion() {
        assertPaymentResultFailure(
                payload(
                        UUID.randomUUID(),
                        PaymentSucceededEnvelope.EVENT_TYPE,
                        2,
                        UUID.randomUUID(),
                        UUID.randomUUID()),
                PaymentResultEventFailure.UNSUPPORTED_EVENT_VERSION);
    }

    @Test
    void rejectsMissingOrderId() {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        assertPaymentResultFailure(
                """
                        {
                          "eventId": "%s",
                          "eventType": "PaymentSucceeded",
                          "eventVersion": 1,
                          "aggregateId": "%s",
                          "occurredAt": "%s",
                          "data": {"paymentId": "%s"}
                        }
                        """.formatted(UUID.randomUUID(), orderId, OCCURRED_AT, paymentId),
                PaymentResultEventFailure.MISSING_ORDER_ID);
    }

    @Test
    void rejectsMissingPaymentId() {
        UUID orderId = UUID.randomUUID();

        assertPaymentResultFailure(
                """
                        {
                          "eventId": "%s",
                          "eventType": "PaymentSucceeded",
                          "eventVersion": 1,
                          "aggregateId": "%s",
                          "occurredAt": "%s",
                          "data": {"orderId": "%s"}
                        }
                        """.formatted(UUID.randomUUID(), orderId, OCCURRED_AT, orderId),
                PaymentResultEventFailure.MISSING_PAYMENT_ID);
    }

    @Test
    void rejectsOrderIdMismatchBetweenEnvelopeAndData() {
        assertPaymentResultFailure(
                payload(
                        UUID.randomUUID(),
                        PaymentSucceededEnvelope.EVENT_TYPE,
                        1,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID()),
                PaymentResultEventFailure.ORDER_ID_MISMATCH);
    }

    @Test
    void rejectsMalformedJsonWithoutLeakingJacksonExceptionType() {
        assertPaymentResultFailure("{not-json", PaymentResultEventFailure.MALFORMED_JSON);
    }

    private void assertPaymentResultFailure(String payload, PaymentResultEventFailure expectedFailure) {
        assertThatThrownBy(() -> parser.parse(payload))
                .isInstanceOfSatisfying(PaymentResultEventException.class, exception ->
                        assertThat(exception.failure()).isEqualTo(expectedFailure));
    }

    private static String payload(
            UUID eventId,
            String eventType,
            int eventVersion,
            UUID orderId,
            UUID paymentId) {
        return payload(eventId, eventType, eventVersion, orderId, orderId, paymentId);
    }

    private static String payload(
            UUID eventId,
            String eventType,
            int eventVersion,
            UUID aggregateId,
            UUID orderId,
            UUID paymentId) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "%s",
                  "eventVersion": %d,
                  "aggregateId": "%s",
                  "occurredAt": "%s",
                  "traceId": "trace-123",
                  "correlationId": "correlation-123",
                  "data": {
                    "orderId": "%s",
                    "paymentId": "%s"
                  }
                }
                """.formatted(eventId, eventType, eventVersion, aggregateId, OCCURRED_AT, orderId, paymentId);
    }
}
