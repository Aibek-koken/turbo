package com.kora.ecommerce.payment.order;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class OrderCreatedEventParser {

    private final ObjectMapper objectMapper;

    public OrderCreatedEventParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public OrderCreatedEvent parse(String payload) {
        if (!StringUtils.hasText(payload)) {
            throw invalid("payload_blank", OrderCreatedEventMetadata.empty());
        }

        JsonNode root = readJson(payload);
        OrderCreatedEventMetadata metadata = metadataFrom(root);
        if (!root.isObject()) {
            throw invalid("payload_not_object", metadata);
        }

        OrderCreatedEnvelope envelope = bindEnvelope(root, metadata);
        return validate(envelope, metadata);
    }

    private JsonNode readJson(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException exception) {
            throw invalid("payload_malformed_json", OrderCreatedEventMetadata.empty(), exception);
        }
    }

    private OrderCreatedEnvelope bindEnvelope(JsonNode root, OrderCreatedEventMetadata metadata) {
        try {
            return objectMapper.treeToValue(root, OrderCreatedEnvelope.class);
        } catch (JsonProcessingException exception) {
            throw invalid("payload_unreadable", metadata, exception);
        }
    }

    private OrderCreatedEvent validate(OrderCreatedEnvelope envelope, OrderCreatedEventMetadata metadata) {
        if (envelope == null) {
            throw invalid("payload_unreadable", metadata);
        }
        if (envelope.eventId() == null) {
            throw invalid("event_id_required", metadata);
        }
        if (!OrderCreatedEnvelope.SUPPORTED_EVENT_TYPE.equals(envelope.eventType())) {
            throw invalid("event_type_unsupported", metadata);
        }
        if (envelope.eventVersion() == null
                || envelope.eventVersion() != OrderCreatedEnvelope.SUPPORTED_EVENT_VERSION) {
            throw invalid("event_version_unsupported", metadata);
        }
        if (envelope.aggregateId() == null) {
            throw invalid("aggregate_id_required", metadata);
        }
        if (envelope.occurredAt() == null) {
            throw invalid("occurred_at_required", metadata);
        }
        if (envelope.data() == null) {
            throw invalid("data_required", metadata);
        }
        if (envelope.data().orderId() == null) {
            throw invalid("order_id_required", metadata);
        }
        if (!envelope.aggregateId().equals(envelope.data().orderId())) {
            throw invalid("aggregate_id_order_id_mismatch", metadata);
        }

        String customerId = requireCustomerId(envelope.data().customerId(), metadata);
        String status = requireText(envelope.data().status(), "status_required", metadata);
        if (!OrderCreatedEnvelope.SUPPORTED_ORDER_STATUS.equals(status)) {
            throw invalid("status_unsupported", metadata);
        }

        return new OrderCreatedEvent(
                envelope.eventId(),
                envelope.eventVersion(),
                envelope.aggregateId(),
                envelope.occurredAt(),
                trimOptional(envelope.traceId()),
                trimOptional(envelope.correlationId()),
                envelope.data().orderId(),
                customerId,
                requireAmount(envelope.data().totalAmount(), metadata),
                requireCurrency(envelope.data().currency(), metadata));
    }

    private String requireCustomerId(String value, OrderCreatedEventMetadata metadata) {
        String customerId = requireText(value, "customer_id_required", metadata);
        if (customerId.length() > 128) {
            throw invalid("customer_id_too_long", metadata);
        }
        return customerId;
    }

    private BigDecimal requireAmount(BigDecimal amount, OrderCreatedEventMetadata metadata) {
        if (amount == null) {
            throw invalid("total_amount_required", metadata);
        }
        BigDecimal scaled;
        try {
            scaled = amount.setScale(4, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw invalid("total_amount_scale_invalid", metadata, exception);
        }
        if (scaled.signum() <= 0) {
            throw invalid("total_amount_must_be_positive", metadata);
        }
        return scaled;
    }

    private String requireCurrency(String value, OrderCreatedEventMetadata metadata) {
        String currency = requireText(value, "currency_required", metadata);
        if (!currency.matches("[A-Z]{3}")) {
            throw invalid("currency_invalid", metadata);
        }
        return currency;
    }

    private String requireText(String value, String reason, OrderCreatedEventMetadata metadata) {
        if (!StringUtils.hasText(value)) {
            throw invalid(reason, metadata);
        }
        return value.trim();
    }

    private String trimOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private OrderCreatedEventMetadata metadataFrom(JsonNode root) {
        if (root == null || !root.isObject()) {
            return OrderCreatedEventMetadata.empty();
        }
        return new OrderCreatedEventMetadata(
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

    private InvalidOrderCreatedEventException invalid(String reason, OrderCreatedEventMetadata metadata) {
        return new InvalidOrderCreatedEventException(reason, metadata);
    }

    private InvalidOrderCreatedEventException invalid(
            String reason,
            OrderCreatedEventMetadata metadata,
            Throwable cause) {
        return new InvalidOrderCreatedEventException(reason, metadata, cause);
    }
}
