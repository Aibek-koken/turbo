package com.kora.ecommerce.auditnotification.notification;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record EmailNotificationPayload(
        String messageId,
        String templateKey,
        String recipientCustomerId,
        String orderId,
        String paymentId,
        String eventId,
        String eventType,
        int eventVersion,
        Instant eventOccurredAt,
        String traceId,
        String correlationId,
        String subject,
        List<String> bodyLines) {

    public EmailNotificationPayload {
        messageId = requireText(messageId, "messageId");
        templateKey = requireText(templateKey, "templateKey");
        recipientCustomerId = requireText(recipientCustomerId, "recipientCustomerId");
        orderId = requireText(orderId, "orderId");
        paymentId = optionalText(paymentId);
        eventId = requireText(eventId, "eventId");
        eventType = requireText(eventType, "eventType");
        if (eventVersion < 1) {
            throw new IllegalArgumentException("eventVersion must be positive");
        }
        eventOccurredAt = Objects.requireNonNull(eventOccurredAt, "eventOccurredAt must not be null");
        traceId = requireText(traceId, "traceId");
        correlationId = requireText(correlationId, "correlationId");
        subject = requireText(subject, "subject");
        bodyLines = List.copyOf(Objects.requireNonNull(bodyLines, "bodyLines must not be null"));
        if (bodyLines.isEmpty()) {
            throw new IllegalArgumentException("bodyLines must not be empty");
        }
        bodyLines.forEach(line -> requireText(line, "bodyLine"));
    }

    private static String requireText(String value, String fieldName) {
        String normalized = optionalText(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

    private static String optionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
