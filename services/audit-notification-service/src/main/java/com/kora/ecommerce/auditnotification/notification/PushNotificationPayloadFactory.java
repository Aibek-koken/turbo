package com.kora.ecommerce.auditnotification.notification;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

@Component
public class PushNotificationPayloadFactory {

    public PushNotificationPayload from(NotificationDeliveryDocument delivery) {
        Objects.requireNonNull(delivery, "delivery must not be null");
        if (delivery.getChannel() != NotificationChannel.PUSH) {
            throw new IllegalArgumentException("delivery channel must be PUSH");
        }

        PushTemplate template = templateFor(delivery.getEventType());
        return new PushNotificationPayload(
                "push:" + delivery.getEventId(),
                template.notificationType(),
                delivery.getCustomerId(),
                delivery.getOrderId(),
                delivery.getPaymentId(),
                delivery.getEventId(),
                delivery.getEventType(),
                delivery.getEventVersion(),
                delivery.getEventOccurredAt(),
                delivery.getTraceId(),
                delivery.getCorrelationId(),
                template.title(),
                template.body(delivery),
                data(delivery, template.notificationType()));
    }

    private PushTemplate templateFor(String eventType) {
        return switch (eventType) {
            case "OrderCreated" -> new PushTemplate(
                    "order-created-push-v1",
                    "Order created",
                    "Order created");
            case "PaymentSucceeded" -> new PushTemplate(
                    "payment-succeeded-push-v1",
                    "Payment succeeded",
                    "Payment succeeded");
            case "PaymentFailed" -> new PushTemplate(
                    "payment-failed-push-v1",
                    "Payment failed",
                    "Payment failed");
            default -> throw new IllegalArgumentException("unsupported push event type");
        };
    }

    private Map<String, String> data(NotificationDeliveryDocument delivery, String notificationType) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("notificationType", notificationType);
        data.put("customerId", delivery.getCustomerId());
        data.put("orderId", delivery.getOrderId());
        if (delivery.getPaymentId() != null) {
            data.put("paymentId", delivery.getPaymentId());
        }
        data.put("eventId", delivery.getEventId());
        data.put("eventType", delivery.getEventType());
        data.put("eventVersion", Integer.toString(delivery.getEventVersion()));
        data.put("eventOccurredAt", delivery.getEventOccurredAt().toString());
        data.put("traceId", delivery.getTraceId());
        data.put("correlationId", delivery.getCorrelationId());
        return data;
    }

    private record PushTemplate(
            String notificationType,
            String title,
            String bodyPrefix) {

        private String body(NotificationDeliveryDocument delivery) {
            return bodyPrefix + " for order " + delivery.getOrderId();
        }
    }
}
