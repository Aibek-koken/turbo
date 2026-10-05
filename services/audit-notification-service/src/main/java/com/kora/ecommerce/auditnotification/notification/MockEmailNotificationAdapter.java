package com.kora.ecommerce.auditnotification.notification;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MockEmailNotificationAdapter implements EmailNotificationPort {

    private static final Logger log = LoggerFactory.getLogger(MockEmailNotificationAdapter.class);

    @Override
    public void send(EmailNotificationPayload payload) {
        Objects.requireNonNull(payload, "payload must not be null");
        log.info(
                "mock_email_notification_sent messageId={} templateKey={} eventId={} eventType={} orderId={} paymentId={} traceId={} correlationId={}",
                payload.messageId(),
                payload.templateKey(),
                payload.eventId(),
                payload.eventType(),
                payload.orderId(),
                payload.paymentId(),
                payload.traceId(),
                payload.correlationId());
    }
}
