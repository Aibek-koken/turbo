package com.kora.ecommerce.auditnotification.order;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.Document;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AuditOrderCreatedEventParser {

    private final ObjectMapper objectMapper;

    public AuditOrderCreatedEventParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AuditOrderCreatedEvent parse(String payload) {
        if (!StringUtils.hasText(payload)) {
            throw invalid("payload_blank", OrderCreatedEventMetadata.empty());
        }

        JsonNode root = readJson(payload);
        OrderCreatedEventMetadata metadata = metadataFrom(root);
        if (!root.isObject()) {
            throw invalid("payload_not_object", metadata);
        }

        OrderCreatedEnvelope envelope = bindEnvelope(root, metadata);
        return validate(envelope, metadata, root);
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

    private AuditOrderCreatedEvent validate(
            OrderCreatedEnvelope envelope,
            OrderCreatedEventMetadata metadata,
            JsonNode root) {
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
        String traceId = requireText(envelope.traceId(), "trace_id_required", metadata);
        String correlationId = requireText(envelope.correlationId(), "correlation_id_required", metadata);
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
        requireAmount(envelope.data().subtotalAmount(), "subtotal_amount_required", metadata);
        requireAmount(envelope.data().totalAmount(), "total_amount_required", metadata);
        String currency = requireCurrency(envelope.data().currency(), metadata);
        if (envelope.data().createdAt() == null) {
            throw invalid("created_at_required", metadata);
        }
        requireItems(envelope.data().items(), currency, metadata);

        return new AuditOrderCreatedEvent(
                envelope.eventId(),
                envelope.eventType(),
                envelope.eventVersion(),
                envelope.aggregateId(),
                envelope.occurredAt(),
                traceId,
                correlationId,
                envelope.data().orderId(),
                customerId,
                toDocument(root));
    }

    private String requireCustomerId(String value, OrderCreatedEventMetadata metadata) {
        String customerId = requireText(value, "customer_id_required", metadata);
        if (customerId.length() > 128) {
            throw invalid("customer_id_too_long", metadata);
        }
        return customerId;
    }

    private void requireItems(
            List<OrderCreatedEnvelope.OrderCreatedItemData> items,
            String orderCurrency,
            OrderCreatedEventMetadata metadata) {
        if (items == null || items.isEmpty()) {
            throw invalid("items_required", metadata);
        }
        for (OrderCreatedEnvelope.OrderCreatedItemData item : items) {
            if (item == null) {
                throw invalid("item_required", metadata);
            }
            if (item.itemNumber() == null || item.itemNumber() < 1) {
                throw invalid("item_number_invalid", metadata);
            }
            if (item.productId() == null) {
                throw invalid("item_product_id_required", metadata);
            }
            requireText(item.productSku(), "item_product_sku_required", metadata);
            requireText(item.productName(), "item_product_name_required", metadata);
            if (item.quantity() == null || item.quantity() < 1) {
                throw invalid("item_quantity_invalid", metadata);
            }
            requireAmount(item.unitPriceAmount(), "item_unit_price_amount_required", metadata);
            requireAmount(item.lineTotalAmount(), "item_line_total_amount_required", metadata);
            String itemCurrency = requireCurrency(item.currency(), metadata);
            if (!orderCurrency.equals(itemCurrency)) {
                throw invalid("item_currency_mismatch", metadata);
            }
        }
    }

    private BigDecimal requireAmount(
            BigDecimal amount,
            String missingReason,
            OrderCreatedEventMetadata metadata) {
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
