package com.kora.ecommerce.auditnotification.payment;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.Document;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AuditPaymentResultEventParser {

    private final ObjectMapper objectMapper;

    public AuditPaymentResultEventParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AuditPaymentResultEvent parse(String payload) {
        if (!StringUtils.hasText(payload)) {
            throw invalid("payload_blank", PaymentResultEventMetadata.empty());
        }

        JsonNode root = readJson(payload);
        PaymentResultEventMetadata metadata = metadataFrom(root);
        if (!root.isObject()) {
            throw invalid("payload_not_object", metadata);
        }

        PaymentResultEnvelope envelope = bindEnvelope(root, metadata);
        return validate(envelope, metadata, root);
    }

    private JsonNode readJson(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException exception) {
            throw invalid("payload_malformed_json", PaymentResultEventMetadata.empty(), exception);
        }
    }

    private PaymentResultEnvelope bindEnvelope(JsonNode root, PaymentResultEventMetadata metadata) {
        try {
            return objectMapper.treeToValue(root, PaymentResultEnvelope.class);
        } catch (JsonProcessingException exception) {
            throw invalid("payload_unreadable", metadata, exception);
        }
    }

    private AuditPaymentResultEvent validate(
            PaymentResultEnvelope envelope,
            PaymentResultEventMetadata metadata,
            JsonNode root) {
        if (envelope == null) {
            throw invalid("payload_unreadable", metadata);
        }
        if (envelope.eventId() == null) {
            throw invalid("event_id_required", metadata);
        }
        if (!PaymentResultEnvelope.PAYMENT_SUCCEEDED.equals(envelope.eventType())
                && !PaymentResultEnvelope.PAYMENT_FAILED.equals(envelope.eventType())) {
            throw invalid("event_type_unsupported", metadata);
        }
        if (envelope.eventVersion() == null
                || envelope.eventVersion() != PaymentResultEnvelope.SUPPORTED_EVENT_VERSION) {
            throw invalid("event_version_unsupported", metadata);
        }
        if (envelope.aggregateId() == null) {
            throw invalid("aggregate_id_required", metadata);
        }
        if (envelope.occurredAt() == null) {
            throw invalid("occurred_at_required", metadata);
        }
        String traceId = requireText(envelope.traceId(), "trace_id_required", metadata);
        String correlationId = requireText(envelope.correlationId(), "correlation_id_required", metadata);
        if (envelope.data() == null) {
            throw invalid("data_required", metadata);
        }
        if (envelope.data().paymentId() == null) {
            throw invalid("payment_id_required", metadata);
        }
        if (envelope.data().orderId() == null) {
            throw invalid("order_id_required", metadata);
        }
        if (!envelope.aggregateId().equals(envelope.data().orderId())) {
            throw invalid("aggregate_id_order_id_mismatch", metadata);
        }
        String customerId = requireCustomerId(envelope.data().customerId(), metadata);
        requireAmount(envelope.data().amount(), "amount_required", metadata);
        requireCurrency(envelope.data().currency(), metadata);
        String paymentStatus = requireText(envelope.data().paymentStatus(), "payment_status_required", metadata);
        if (envelope.data().providerAttemptId() == null) {
            throw invalid("provider_attempt_id_required", metadata);
        }
        String providerAttemptOutcome = requireText(
                envelope.data().providerAttemptOutcome(),
                "provider_attempt_outcome_required",
                metadata);
        if (envelope.data().orderCreatedEventId() == null) {
            throw invalid("order_created_event_id_required", metadata);
        }
        validateTerminalResult(envelope, paymentStatus, providerAttemptOutcome, metadata);

        return new AuditPaymentResultEvent(
                envelope.eventId(),
                envelope.eventType(),
                envelope.eventVersion(),
                envelope.aggregateId(),
                envelope.occurredAt(),
                traceId,
                correlationId,
                envelope.data().orderId(),
                envelope.data().paymentId(),
                customerId,
                paymentStatus,
                toDocument(root));
    }

    private void validateTerminalResult(
            PaymentResultEnvelope envelope,
            String paymentStatus,
            String providerAttemptOutcome,
            PaymentResultEventMetadata metadata) {
        if (PaymentResultEnvelope.PAYMENT_SUCCEEDED.equals(envelope.eventType())) {
            if (!PaymentResultEnvelope.STATUS_SUCCEEDED.equals(paymentStatus)) {
                throw invalid("payment_status_event_type_mismatch", metadata);
            }
            if (!PaymentResultEnvelope.OUTCOME_SUCCEEDED.equals(providerAttemptOutcome)) {
                throw invalid("provider_attempt_outcome_event_type_mismatch", metadata);
            }
            requireText(envelope.data().providerReference(), "provider_reference_required", metadata);
            return;
        }

        if (!PaymentResultEnvelope.STATUS_FAILED.equals(paymentStatus)) {
            throw invalid("payment_status_event_type_mismatch", metadata);
        }
        if (!PaymentResultEnvelope.OUTCOME_DECLINED.equals(providerAttemptOutcome)
                && !PaymentResultEnvelope.OUTCOME_MALFORMED_RESPONSE.equals(providerAttemptOutcome)) {
            throw invalid("provider_attempt_outcome_event_type_mismatch", metadata);
        }
        requireText(envelope.data().failureReason(), "failure_reason_required", metadata);
    }

    private String requireCustomerId(String value, PaymentResultEventMetadata metadata) {
        String customerId = requireText(value, "customer_id_required", metadata);
        if (customerId.length() > 128) {
            throw invalid("customer_id_too_long", metadata);
        }
        return customerId;
    }

    private BigDecimal requireAmount(
            BigDecimal amount,
            String missingReason,
            PaymentResultEventMetadata metadata) {
        if (amount == null) {
            throw invalid(missingReason, metadata);
        }
        try {
            amount.setScale(4, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw invalid("amount_scale_invalid", metadata, exception);
        }
        if (amount.signum() <= 0) {
            throw invalid("amount_must_be_positive", metadata);
        }
        return amount;
    }

    private String requireCurrency(String value, PaymentResultEventMetadata metadata) {
        String currency = requireText(value, "currency_required", metadata);
        if (!currency.matches("[A-Z]{3}")) {
            throw invalid("currency_invalid", metadata);
        }
        return currency;
    }

    private String requireText(String value, String reason, PaymentResultEventMetadata metadata) {
        if (!StringUtils.hasText(value)) {
            throw invalid(reason, metadata);
        }
        return value.trim();
    }

    private PaymentResultEventMetadata metadataFrom(JsonNode root) {
        if (root == null || !root.isObject()) {
            return PaymentResultEventMetadata.empty();
        }
        return new PaymentResultEventMetadata(
                textOrNull(root.get("eventId")),
                textOrNull(root.get("eventType")),
                integerOrNull(root.get("eventVersion")),
                textOrNull(root.get("aggregateId")));
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asText();
    }

    private Integer integerOrNull(JsonNode node) {
        if (node == null || node.isNull() || !node.canConvertToInt()) {
            return null;
        }
        return node.asInt();
    }

    private Document toDocument(JsonNode node) {
        Document document = new Document();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            document.append(field.getKey(), toBsonValue(field.getValue()));
        }
        return document;
    }

    private Object toBsonValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isObject()) {
            return toDocument(node);
        }
        if (node.isArray()) {
            List<Object> values = new ArrayList<>();
            node.forEach(value -> values.add(toBsonValue(value)));
            return values;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isNumber()) {
            return switch (node.numberType()) {
                case INT -> node.intValue();
                case LONG -> node.longValue();
                case BIG_INTEGER -> node.bigIntegerValue();
                case FLOAT, DOUBLE -> node.decimalValue();
                case BIG_DECIMAL -> node.decimalValue();
            };
        }
        if (node.isBinary()) {
            try {
                return node.binaryValue();
            } catch (IOException exception) {
                throw new IllegalArgumentException("Unable to convert binary JSON node.", exception);
            }
        }
        return node.asText();
    }

    private InvalidPaymentResultEventException invalid(
            String reason,
            PaymentResultEventMetadata metadata) {
        return new InvalidPaymentResultEventException(reason, metadata);
    }

    private InvalidPaymentResultEventException invalid(
            String reason,
            PaymentResultEventMetadata metadata,
            Throwable cause) {
        return new InvalidPaymentResultEventException(reason, metadata, cause);
    }
}
