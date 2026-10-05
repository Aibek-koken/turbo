package com.kora.ecommerce.auditnotification.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.bson.Document;
import org.junit.jupiter.api.Test;

class AuditPaymentResultEventParserTest {

    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORDER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PAYMENT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID ATTEMPT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID ORDER_CREATED_EVENT_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final Instant OCCURRED_AT = Instant.parse("2026-10-04T10:00:00Z");

    private final AuditPaymentResultEventParser parser = new AuditPaymentResultEventParser(JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build());

    @Test
    void parsesValidPaymentSucceededEnvelopeAndKeepsStructuredPayload() {
        AuditPaymentResultEvent event = parser.parse(validSucceededPayload());

        assertThat(event.eventId()).isEqualTo(EVENT_ID);
        assertThat(event.eventType()).isEqualTo("PaymentSucceeded");
        assertThat(event.eventVersion()).isEqualTo(1);
        assertThat(event.aggregateId()).isEqualTo(ORDER_ID);
        assertThat(event.occurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(event.traceId()).isEqualTo("trace-123");
        assertThat(event.correlationId()).isEqualTo("corr-123");
        assertThat(event.orderId()).isEqualTo(ORDER_ID);
        assertThat(event.paymentId()).isEqualTo(PAYMENT_ID);
        assertThat(event.customerId()).isEqualTo("jwt-customer-123");
        assertThat(event.paymentStatus()).isEqualTo("SUCCEEDED");

        Document payload = event.payload();
        assertThat(payload).containsEntry("eventId", EVENT_ID.toString());
        assertThat(payload.get("data", Document.class))
                .containsEntry("customerId", " jwt-customer-123 ")
                .containsEntry("paymentStatus", "SUCCEEDED")
                .containsEntry("providerReference", "provider-ref-123");
    }

    @Test
    void parsesValidPaymentFailedEnvelope() {
        AuditPaymentResultEvent event = parser.parse(validFailedPayload());

        assertThat(event.eventType()).isEqualTo("PaymentFailed");
        assertThat(event.paymentStatus()).isEqualTo("FAILED");
        assertThat(event.payload().get("data", Document.class))
                .containsEntry("providerAttemptOutcome", "DECLINED")
                .containsEntry("failureReason", "DECLINED");
    }

    @Test
    void payloadIsDefensivelyCopied() {
        AuditPaymentResultEvent event = parser.parse(validSucceededPayload());

        event.payload().put("eventId", "mutated");

        assertThat(event.payload()).containsEntry("eventId", EVENT_ID.toString());
    }

    @Test
    void rejectsMalformedJsonUnsupportedTypeAndUnsupportedVersion() {
        assertInvalid("{", "payload_malformed_json");
        assertInvalid(validSucceededPayload().replace("\"eventType\": \"PaymentSucceeded\"", "\"eventType\": \"OrderCreated\""),
                "event_type_unsupported");
        assertInvalid(validSucceededPayload().replace("\"eventVersion\": 1", "\"eventVersion\": 2"),
                "event_version_unsupported");
    }

    @Test
    void rejectsMissingIdentifiersAndAggregateOrderMismatch() {
        assertInvalid(validSucceededPayload().replace("\"paymentId\": \"33333333-3333-3333-3333-333333333333\",", ""),
                "payment_id_required");
        assertInvalid(validSucceededPayload().replace("\"customerId\": \" jwt-customer-123 \"", "\"customerId\": \" \""),
                "customer_id_required");
        assertInvalid(validSucceededPayload().replace(
                        "\"orderId\": \"22222222-2222-2222-2222-222222222222\"",
                        "\"orderId\": \"66666666-6666-6666-6666-666666666666\""),
                "aggregate_id_order_id_mismatch");
    }

    @Test
    void rejectsInvalidMoneyCurrencyAndTerminalStatusMismatch() {
        assertInvalid(validSucceededPayload().replace("\"amount\": \"42.9900\"", "\"amount\": \"42.99001\""),
                "amount_scale_invalid");
        assertInvalid(validSucceededPayload().replace("\"currency\": \"USD\"", "\"currency\": \"usd\""),
                "currency_invalid");
        assertInvalid(validSucceededPayload().replace("\"paymentStatus\": \"SUCCEEDED\"", "\"paymentStatus\": \"FAILED\""),
                "payment_status_event_type_mismatch");
        assertInvalid(validFailedPayload().replace("\"providerAttemptOutcome\": \"DECLINED\"", "\"providerAttemptOutcome\": \"SUCCEEDED\""),
                "provider_attempt_outcome_event_type_mismatch");
    }

    @Test
    void rejectsMissingOutcomeSpecificDetails() {
        assertInvalid(validSucceededPayload().replace("\"providerReference\": \"provider-ref-123\"", "\"providerReference\": \" \""),
                "provider_reference_required");
        assertInvalid(validFailedPayload().replace("\"failureReason\": \"DECLINED\"", "\"failureReason\": \" \""),
                "failure_reason_required");
    }

    private void assertInvalid(String payload, String reason) {
        InvalidPaymentResultEventException exception =
                catchThrowableOfType(
                        () -> parser.parse(payload),
                        InvalidPaymentResultEventException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.reason()).isEqualTo(reason);
    }

    private String validSucceededPayload() {
        return """
                {
                  "eventId": "11111111-1111-1111-1111-111111111111",
                  "eventType": "PaymentSucceeded",
                  "eventVersion": 1,
                  "aggregateId": "22222222-2222-2222-2222-222222222222",
                  "occurredAt": "2026-10-04T10:00:00Z",
                  "traceId": " trace-123 ",
                  "correlationId": " corr-123 ",
                  "data": {
                    "paymentId": "33333333-3333-3333-3333-333333333333",
                    "orderId": "22222222-2222-2222-2222-222222222222",
                    "customerId": " jwt-customer-123 ",
                    "amount": "42.9900",
                    "currency": "USD",
                    "paymentStatus": "SUCCEEDED",
                    "providerAttemptId": "44444444-4444-4444-4444-444444444444",
                    "providerAttemptOutcome": "SUCCEEDED",
                    "providerReference": "provider-ref-123",
                    "failureReason": null,
                    "orderCreatedEventId": "55555555-5555-5555-5555-555555555555"
                  }
                }
                """;
    }

    private String validFailedPayload() {
        return """
                {
                  "eventId": "11111111-1111-1111-1111-111111111111",
                  "eventType": "PaymentFailed",
                  "eventVersion": 1,
                  "aggregateId": "22222222-2222-2222-2222-222222222222",
                  "occurredAt": "2026-10-04T10:00:00Z",
                  "traceId": " trace-123 ",
                  "correlationId": " corr-123 ",
                  "data": {
                    "paymentId": "33333333-3333-3333-3333-333333333333",
                    "orderId": "22222222-2222-2222-2222-222222222222",
                    "customerId": " jwt-customer-123 ",
                    "amount": "42.9900",
                    "currency": "USD",
                    "paymentStatus": "FAILED",
                    "providerAttemptId": "44444444-4444-4444-4444-444444444444",
                    "providerAttemptOutcome": "DECLINED",
                    "providerReference": null,
                    "failureReason": "DECLINED",
                    "orderCreatedEventId": "55555555-5555-5555-5555-555555555555"
                  }
                }
                """;
    }
}
