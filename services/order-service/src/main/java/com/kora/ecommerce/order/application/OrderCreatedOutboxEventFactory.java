package com.kora.ecommerce.order.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kora.ecommerce.order.observability.CorrelationIdFilter;
import com.kora.ecommerce.order.persistence.OrderStatus;
import com.kora.ecommerce.order.persistence.OutboxEventEntity;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
class OrderCreatedOutboxEventFactory {

    static final String EVENT_TYPE = "OrderCreated";
    static final int EVENT_VERSION = 1;

    private static final ObjectWriter ENVELOPE_WRITER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build()
            .writerFor(OrderCreatedEnvelope.class);

    OutboxEventEntity createEvent(OrderCreationDraft draft) {
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = draft.createdAt();
        String traceId = blankToNull(MDC.get("traceId"));
        String correlationId = blankToNull(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY));

        OrderCreatedEnvelope envelope = new OrderCreatedEnvelope(
                eventId,
                EVENT_TYPE,
                EVENT_VERSION,
                draft.orderId(),
                occurredAt,
                traceId,
                correlationId,
                OrderCreatedData.from(draft));

        return OutboxEventEntity.orderEvent(
                eventId,
                draft.orderId(),
                EVENT_TYPE,
                EVENT_VERSION,
                occurredAt,
                traceId,
                correlationId,
                serialize(envelope));
    }

    private static String serialize(OrderCreatedEnvelope envelope) {
        try {
            return ENVELOPE_WRITER.writeValueAsString(envelope);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize OrderCreated outbox event.", exception);
        }
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    @JsonPropertyOrder({
            "eventId",
            "eventType",
            "eventVersion",
            "aggregateId",
            "occurredAt",
            "traceId",
            "correlationId",
            "data"
    })
    private record OrderCreatedEnvelope(
            UUID eventId,
            String eventType,
            int eventVersion,
            UUID aggregateId,
            Instant occurredAt,
            String traceId,
            String correlationId,
            OrderCreatedData data) {
    }

    @JsonPropertyOrder({
            "orderId",
            "customerId",
            "status",
            "subtotalAmount",
            "totalAmount",
            "currency",
            "createdAt",
            "items"
    })
    private record OrderCreatedData(
            UUID orderId,
            String customerId,
            OrderStatus status,
            BigDecimal subtotalAmount,
            BigDecimal totalAmount,
            String currency,
            Instant createdAt,
            List<OrderCreatedItemData> items) {

        private static OrderCreatedData from(OrderCreationDraft draft) {
            return new OrderCreatedData(
                    draft.orderId(),
                    draft.customerId(),
                    OrderStatus.CREATED,
                    draft.subtotalAmount(),
                    draft.totalAmount(),
                    draft.currency(),
                    draft.createdAt(),
                    draft.items().stream()
                            .map(OrderCreatedItemData::from)
                            .toList());
        }
    }

    @JsonPropertyOrder({
            "itemNumber",
            "productId",
            "productSku",
            "productName",
            "quantity",
            "unitPriceAmount",
            "currency",
            "lineTotalAmount"
    })
    private record OrderCreatedItemData(
            int itemNumber,
            UUID productId,
            String productSku,
            String productName,
            int quantity,
            BigDecimal unitPriceAmount,
            String currency,
            BigDecimal lineTotalAmount) {

        private static OrderCreatedItemData from(OrderCreationDraftItem item) {
            return new OrderCreatedItemData(
                    item.itemNumber(),
                    item.productId(),
                    item.productSku(),
                    item.productName(),
                    item.quantity(),
                    item.unitPriceAmount(),
                    item.currency(),
                    item.lineTotalAmount());
        }
    }
}
