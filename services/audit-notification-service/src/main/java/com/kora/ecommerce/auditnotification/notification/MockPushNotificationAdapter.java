package com.kora.ecommerce.auditnotification.notification;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MockPushNotificationAdapter implements PushNotificationPort {

    private static final Logger log = LoggerFactory.getLogger(MockPushNotificationAdapter.class);

    @Override
    public void send(PushNotificationPayload payload) {
        Objects.requireNonNull(payload, "payload must not be null");
        log.info(
                "mock_push_notification_sent messageId={} notificationType={} eventId={} eventType={} orderId={} paymentId={} traceId={} correlationId={}",
                payload.messageId(),
                payload.notificationType(),
                payload.eventId(),
                payload.eventType(),
                payload.orderId(),
                payload.paymentId(),
                payload.traceId(),
                payload.correlationId());
    }
}
