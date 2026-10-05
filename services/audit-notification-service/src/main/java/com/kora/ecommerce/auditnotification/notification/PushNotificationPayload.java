package com.kora.ecommerce.auditnotification.notification;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record PushNotificationPayload(
        String messageId,
        String notificationType,
        String recipientCustomerId,
        String orderId,
        String paymentId,
        String eventId,
        String eventType,
        int eventVersion,
        Instant eventOccurredAt,
        String traceId,
        String correlationId,
        String title,
        String body,
        Map<String, String> data) {

    public PushNotificationPayload {
        messageId = requireText(messageId, "messageId");
        notificationType = requireText(notificationType, "notificationType");
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
        title = requireText(title, "title");
        body = requireText(body, "body");
        data = copyData(data);
    }

    private static Map<String, String> copyData(Map<String, String> data) {
        Objects.requireNonNull(data, "data must not be null");
        if (data.isEmpty()) {
            throw new IllegalArgumentException("data must not be empty");
        }
        LinkedHashMap<String, String> copied = new LinkedHashMap<>();
        data.forEach((key, value) -> copied.put(
                requireText(key, "data key"),
                requireText(value, "data value")));
        return Collections.unmodifiableMap(copied);
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
