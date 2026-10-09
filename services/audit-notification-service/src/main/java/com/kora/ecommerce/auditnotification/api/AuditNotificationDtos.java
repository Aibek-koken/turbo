package com.kora.ecommerce.auditnotification.api;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.kora.ecommerce.auditnotification.notification.NotificationDeliveryDocument;
import com.kora.ecommerce.auditnotification.persistence.AuditEventDocument;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

public final class AuditNotificationDtos {

    private AuditNotificationDtos() {
    }

    @Schema(description = "A page of audit events.")
    public record AuditEventPageResponse(
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext,
            List<AuditEventResponse> auditEvents) {

        static AuditEventPageResponse from(Page<AuditEventDocument> page) {
            return new AuditEventPageResponse(
                    page.getNumber(),
                    page.getSize(),
                    page.getTotalElements(),
                    page.getTotalPages(),
                    page.hasNext(),
                    page.getContent().stream()
                            .map(AuditEventResponse::from)
                            .toList());
        }
    }

    @Schema(description = "Audit event metadata and sanitized business payload.")
    public record AuditEventResponse(
            String eventId,
            String eventType,
            int eventVersion,
            String aggregateId,
            String orderId,
            String customerId,
            String paymentId,
            Instant occurredAt,
            Instant receivedAt,
            String traceId,
            String correlationId,
            Map<String, Object> payload) {

        static AuditEventResponse from(AuditEventDocument document) {
            return new AuditEventResponse(
                    document.getEventId(),
                    document.getEventType(),
                    document.getEventVersion(),
                    document.getAggregateId(),
                    document.getOrderId(),
                    document.getCustomerId(),
                    document.getPaymentId(),
                    document.getOccurredAt(),
                    document.getReceivedAt(),
                    document.getTraceId(),
                    document.getCorrelationId(),
                    new LinkedHashMap<>(document.getPayload()));
        }
    }

    @Schema(description = "Notification delivery status for an audited business event.")
    public record NotificationDeliveryResponse(
            String eventId,
            String channel,
            String eventType,
            int eventVersion,
            String orderId,
            String customerId,
            String paymentId,
            String status,
            int attemptCount,
            Instant lastAttemptAt,
            String safeFailureDetail,
            Instant eventOccurredAt,
            Instant createdAt,
            Instant updatedAt,
            String traceId,
            String correlationId) {

        static NotificationDeliveryResponse from(NotificationDeliveryDocument document) {
            return new NotificationDeliveryResponse(
                    document.getEventId(),
                    document.getChannel().name(),
                    document.getEventType(),
                    document.getEventVersion(),
                    document.getOrderId(),
                    document.getCustomerId(),
                    document.getPaymentId(),
                    document.getStatus().name(),
                    document.getAttemptCount(),
                    document.getLastAttemptAt(),
                    document.getSafeFailureDetail(),
                    document.getEventOccurredAt(),
                    document.getCreatedAt(),
                    document.getUpdatedAt(),
                    document.getTraceId(),
                    document.getCorrelationId());
        }
    }
}
