package com.kora.ecommerce.auditnotification.notification;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

class MockPushNotificationAdapterTest {

    private final MockPushNotificationAdapter adapter = new MockPushNotificationAdapter();

    @Test
    void acceptsLocalMockPayloadWithoutProviderCredentialsOrDeviceTokens() {
        PushNotificationPayload payload = new PushNotificationPayload(
                "push:evt-1",
                "order-created-push-v1",
                "customer-1",
                "order-1",
                null,
                "evt-1",
                "OrderCreated",
                1,
                Instant.parse("2026-10-04T10:15:30Z"),
                "trace-1",
                "corr-1",
                "Order created",
                "Order created for order order-1",
                Map.of(
                        "notificationType", "order-created-push-v1",
                        "customerId", "customer-1",
                        "orderId", "order-1",
                        "eventId", "evt-1"));

        assertThatCode(() -> adapter.send(payload)).doesNotThrowAnyException();
    }

    @Test
    void rejectsNullPayloads() {
        assertThatNullPointerException()
                .isThrownBy(() -> adapter.send(null))
                .withMessageContaining("payload");
    }
}
