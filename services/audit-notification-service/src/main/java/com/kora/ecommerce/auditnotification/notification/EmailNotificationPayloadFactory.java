package com.kora.ecommerce.auditnotification.notification;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

@Component
public class EmailNotificationPayloadFactory {

    public EmailNotificationPayload from(NotificationDeliveryDocument delivery) {
        Objects.requireNonNull(delivery, "delivery must not be null");
        if (delivery.getChannel() != NotificationChannel.EMAIL) {
            throw new IllegalArgumentException("delivery channel must be EMAIL");
        }

        EmailTemplate template = templateFor(delivery.getEventType());
        List<String> bodyLines = bodyLines(delivery, template.statusLine());
        return new EmailNotificationPayload(
                "email:" + delivery.getEventId(),
                template.templateKey(),
                delivery.getCustomerId(),
                delivery.getOrderId(),
                delivery.getPaymentId(),
                delivery.getEventId(),
                delivery.getEventType(),
                delivery.getEventVersion(),
                delivery.getEventOccurredAt(),
                delivery.getTraceId(),
                delivery.getCorrelationId(),
                template.subject(delivery),
                bodyLines);
    }

    private EmailTemplate templateFor(String eventType) {
        return switch (eventType) {
            case "OrderCreated" -> new EmailTemplate(
                    "order-created-email-v1",
                    "Order created",
                    "Order status: CREATED");
            case "PaymentSucceeded" -> new EmailTemplate(
                    "payment-succeeded-email-v1",
                    "Payment succeeded",
                    "Payment status: SUCCEEDED");
            case "PaymentFailed" -> new EmailTemplate(
                    "payment-failed-email-v1",
                    "Payment failed",
                    "Payment status: FAILED");
            default -> throw new IllegalArgumentException("unsupported email event type");
        };
    }

    private List<String> bodyLines(NotificationDeliveryDocument delivery, String statusLine) {
        List<String> lines = new ArrayList<>();
        lines.add("Customer reference: " + delivery.getCustomerId());
        lines.add("Order reference: " + delivery.getOrderId());
        if (delivery.getPaymentId() != null) {
            lines.add("Payment reference: " + delivery.getPaymentId());
        }
        lines.add(statusLine);
        lines.add("Event: " + delivery.getEventType() + " v" + delivery.getEventVersion());
        lines.add("Event ID: " + delivery.getEventId());
        lines.add("Occurred at: " + delivery.getEventOccurredAt());
        lines.add("Correlation ID: " + delivery.getCorrelationId());
        return lines;
    }

    private record EmailTemplate(
            String templateKey,
            String subjectPrefix,
            String statusLine) {

        private String subject(NotificationDeliveryDocument delivery) {
            return subjectPrefix + " for order " + delivery.getOrderId();
        }
    }
}
