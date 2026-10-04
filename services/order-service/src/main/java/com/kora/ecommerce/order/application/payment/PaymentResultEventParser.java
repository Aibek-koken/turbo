package com.kora.ecommerce.order.application.payment;

import static com.kora.ecommerce.order.application.payment.PaymentResultEventFailure.EMPTY_PAYLOAD;
import static com.kora.ecommerce.order.application.payment.PaymentResultEventFailure.MALFORMED_AGGREGATE_ID;
import static com.kora.ecommerce.order.application.payment.PaymentResultEventFailure.MALFORMED_EVENT_ID;
import static com.kora.ecommerce.order.application.payment.PaymentResultEventFailure.MALFORMED_JSON;
import static com.kora.ecommerce.order.application.payment.PaymentResultEventFailure.MALFORMED_OCCURRED_AT;
import static com.kora.ecommerce.order.application.payment.PaymentResultEventFailure.MALFORMED_ORDER_ID;
import static com.kora.ecommerce.order.application.payment.PaymentResultEventFailure.MALFORMED_PAYMENT_ID;
import static com.kora.ecommerce.order.application.payment.PaymentResultEventFailure.MISSING_AGGREGATE_ID;
import static com.kora.ecommerce.order.application.payment.PaymentResultEventFailure.MISSING_DATA;
import static com.kora.ecommerce.order.application.payment.PaymentResultEventFailure.MISSING_EVENT_ID;
import static com.kora.ecommerce.order.application.payment.PaymentResultEventFailure.MISSING_EVENT_TYPE;
import static com.kora.ecommerce.order.application.payment.PaymentResultEventFailure.MISSING_EVENT_VERSION;
import static com.kora.ecommerce.order.application.payment.PaymentResultEventFailure.MISSING_OCCURRED_AT;
import static com.kora.ecommerce.order.application.payment.PaymentResultEventFailure.MISSING_ORDER_ID;
import static com.kora.ecommerce.order.application.payment.PaymentResultEventFailure.MISSING_PAYMENT_ID;
import static com.kora.ecommerce.order.application.payment.PaymentResultEventFailure.ORDER_ID_MISMATCH;
import static com.kora.ecommerce.order.application.payment.PaymentResultEventFailure.UNSUPPORTED_EVENT_TYPE;
import static com.kora.ecommerce.order.application.payment.PaymentResultEventFailure.UNSUPPORTED_EVENT_VERSION;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PaymentResultEventParser {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    public PaymentResultEnvelope parse(String payload) {
        if (!StringUtils.hasText(payload)) {
            throw PaymentResultEventException.of(EMPTY_PAYLOAD, "Payment result event payload is empty.");
        }

        JsonNode root = parseJson(payload);
        UUID eventId = requiredUuid(root, "eventId", MISSING_EVENT_ID, MALFORMED_EVENT_ID);
        String eventType = requiredText(root, "eventType", MISSING_EVENT_TYPE);
        int eventVersion = requiredEventVersion(root);
        UUID aggregateId = requiredUuid(root, "aggregateId", MISSING_AGGREGATE_ID, MALFORMED_AGGREGATE_ID);
        Instant occurredAt = requiredInstant(root, "occurredAt", MISSING_OCCURRED_AT, MALFORMED_OCCURRED_AT);
        JsonNode data = requiredObject(root, "data", MISSING_DATA);
        UUID orderId = requiredUuid(data, "orderId", MISSING_ORDER_ID, MALFORMED_ORDER_ID);
        UUID paymentId = requiredUuid(data, "paymentId", MISSING_PAYMENT_ID, MALFORMED_PAYMENT_ID);

        if (!aggregateId.equals(orderId)) {
            throw PaymentResultEventException.of(
                    ORDER_ID_MISMATCH,
                    "Payment result event aggregateId must match data.orderId.");
        }

        PaymentResultData resultData = new PaymentResultData(orderId, paymentId);
        String traceId = optionalText(root, "traceId");
        String correlationId = optionalText(root, "correlationId");

        return switch (eventType) {
            case PaymentSucceededEnvelope.EVENT_TYPE -> new PaymentSucceededEnvelope(
                    eventId,
                    eventVersion,
                    aggregateId,
                    occurredAt,
                    traceId,
                    correlationId,
                    resultData);
            case PaymentFailedEnvelope.EVENT_TYPE -> new PaymentFailedEnvelope(
                    eventId,
                    eventVersion,
                    aggregateId,
                    occurredAt,
                    traceId,
                    correlationId,
                    resultData);
            default -> throw PaymentResultEventException.of(
                    UNSUPPORTED_EVENT_TYPE,
                    "Payment result event type is not supported by Order Service.");
        };
    }

    private static JsonNode parseJson(String payload) {
        try {
            JsonNode root = JSON_MAPPER.readTree(payload);
            if (root == null || !root.isObject()) {
                throw PaymentResultEventException.of(MALFORMED_JSON, "Payment result event must be a JSON object.");
            }
            return root;
        } catch (JsonProcessingException exception) {
            throw PaymentResultEventException.of(
                    MALFORMED_JSON,
                    "Payment result event payload is not valid JSON.",
                    exception);
        }
    }

    private static int requiredEventVersion(JsonNode root) {
        JsonNode version = root.get("eventVersion");
        if (version == null || version.isNull() || !version.canConvertToInt()) {
            throw PaymentResultEventException.of(
                    MISSING_EVENT_VERSION,
                    "Payment result eventVersion is required.");
        }
        int eventVersion = version.intValue();
        if (eventVersion != PaymentResultEnvelope.SUPPORTED_EVENT_VERSION) {
            throw PaymentResultEventException.of(
                    UNSUPPORTED_EVENT_VERSION,
                    "Payment result eventVersion is not supported by Order Service.");
        }
        return eventVersion;
    }

    private static JsonNode requiredObject(JsonNode root, String field, PaymentResultEventFailure missingFailure) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull() || !node.isObject()) {
            throw PaymentResultEventException.of(missingFailure, "Payment result " + field + " object is required.");
        }
        return node;
    }

    private static UUID requiredUuid(
            JsonNode root,
            String field,
            PaymentResultEventFailure missingFailure,
            PaymentResultEventFailure malformedFailure) {
        String value = requiredText(root, field, missingFailure);
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw PaymentResultEventException.of(
                    malformedFailure,
                    "Payment result " + field + " must be a UUID.",
                    exception);
        }
    }

    private static Instant requiredInstant(
            JsonNode root,
            String field,
            PaymentResultEventFailure missingFailure,
            PaymentResultEventFailure malformedFailure) {
        String value = requiredText(root, field, missingFailure);
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw PaymentResultEventException.of(
                    malformedFailure,
                    "Payment result " + field + " must be an ISO-8601 instant.",
                    exception);
        }
    }

    private static String requiredText(JsonNode root, String field, PaymentResultEventFailure missingFailure) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull() || !node.isTextual() || !StringUtils.hasText(node.textValue())) {
            throw PaymentResultEventException.of(
                    missingFailure,
                    "Payment result " + field + " is required.");
        }
        return node.textValue().trim();
    }

    private static String optionalText(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull() || !node.isTextual() || !StringUtils.hasText(node.textValue())) {
            return null;
        }
        return node.textValue().trim();
    }
}
