package com.kora.ecommerce.auditnotification.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.bson.Document;
import org.junit.jupiter.api.Test;

class AuditOrderCreatedEventParserTest {

    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORDER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant OCCURRED_AT = Instant.parse("2026-10-04T10:00:00Z");

    private final AuditOrderCreatedEventParser parser = new AuditOrderCreatedEventParser(JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build());

    @Test
    void parsesValidOrderCreatedEnvelopeAndKeepsStructuredPayload() {
        AuditOrderCreatedEvent event = parser.parse(validPayload());

        assertThat(event.eventId()).isEqualTo(EVENT_ID);
        assertThat(event.eventType()).isEqualTo("OrderCreated");
        assertThat(event.eventVersion()).isEqualTo(1);
        assertThat(event.aggregateId()).isEqualTo(ORDER_ID);
        assertThat(event.occurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(event.traceId()).isEqualTo("trace-123");
        assertThat(event.correlationId()).isEqualTo("corr-123");
        assertThat(event.orderId()).isEqualTo(ORDER_ID);
        assertThat(event.customerId()).isEqualTo("jwt-customer-123");

        Document payload = event.payload();
        assertThat(payload).containsEntry("eventId", EVENT_ID.toString());
        assertThat(payload.get("data", Document.class))
                .containsEntry("customerId", " jwt-customer-123 ")
                .containsEntry("currency", "USD");
        assertThat(payload.get("data", Document.class).get("items", List.class)).hasSize(1);
    }

    @Test
    void payloadIsDefensivelyCopied() {
        AuditOrderCreatedEvent event = parser.parse(validPayload());

        event.payload().put("eventId", "mutated");

        assertThat(event.payload()).containsEntry("eventId", EVENT_ID.toString());
    }

    @Test
    void rejectsMalformedJsonAndUnsupportedEnvelope() {
        assertInvalid("{", "payload_malformed_json");
        assertInvalid(validPayload().replace("\"eventType\": \"OrderCreated\"", "\"eventType\": \"OrderCancelled\""),
                "event_type_unsupported");
        assertInvalid(validPayload().replace("\"eventVersion\": 1", "\"eventVersion\": 2"),
                "event_version_unsupported");
    }

    @Test
    void rejectsMissingTraceCorrelationAndIdentifiers() {
        assertInvalid(validPayload().replace("\"traceId\": \" trace-123 \",", ""),
                "trace_id_required");
        assertInvalid(validPayload().replace("\"correlationId\": \" corr-123 \",", ""),
                "correlation_id_required");
        assertInvalid(validPayload().replace("\"customerId\": \" jwt-customer-123 \"", "\"customerId\": \" \""),
                "customer_id_required");
    }

    @Test
    void rejectsAggregateOrderMismatchUnsupportedStatusAndInvalidMoney() {
        assertInvalid(validPayload().replace(
                        "\"orderId\": \"22222222-2222-2222-2222-222222222222\"",
                        "\"orderId\": \"33333333-3333-3333-3333-333333333333\""),
                "aggregate_id_order_id_mismatch");
        assertInvalid(validPayload().replace("\"status\": \"CREATED\"", "\"status\": \"PAID\""),
                "status_unsupported");
        assertInvalid(validPayload().replace("\"totalAmount\": 42.9900", "\"totalAmount\": 42.99001"),
                "amount_scale_invalid");
    }

    @Test
    void rejectsIncompleteItemSnapshot() {
        assertInvalid(validPayload().replace("\"quantity\": 1", "\"quantity\": 0"),
                "item_quantity_invalid");
    }

    private void assertInvalid(String payload, String reason) {
        InvalidOrderCreatedEventException exception =
                catchThrowableOfType(
                        () -> parser.parse(payload),
                        InvalidOrderCreatedEventException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.reason()).isEqualTo(reason);
    }

    private String validPayload() {
        return """
                {
                  "eventId": "11111111-1111-1111-1111-111111111111",
                  "eventType": "OrderCreated",
                  "eventVersion": 1,
                  "aggregateId": "22222222-2222-2222-2222-222222222222",
                  "occurredAt": "2026-10-04T10:00:00Z",
                  "traceId": " trace-123 ",
                  "correlationId": " corr-123 ",
                  "data": {
                    "orderId": "22222222-2222-2222-2222-222222222222",
                    "customerId": " jwt-customer-123 ",
                    "status": "CREATED",
                    "subtotalAmount": 42.9900,
                    "totalAmount": 42.9900,
                    "currency": "USD",
                    "createdAt": "2026-10-04T10:00:00Z",
                    "items": [
                      {
                        "itemNumber": 1,
                        "productId": "44444444-4444-4444-4444-444444444444",
                        "productSku": "SKU-1",
                        "productName": "Test Product",
                        "quantity": 1,
                        "unitPriceAmount": 42.9900,
                        "currency": "USD",
                        "lineTotalAmount": 42.9900
                      }
                    ]
                  }
                }
                """;
    }
}
