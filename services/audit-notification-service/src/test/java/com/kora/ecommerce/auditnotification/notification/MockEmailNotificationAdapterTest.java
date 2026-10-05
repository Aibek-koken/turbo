package com.kora.ecommerce.auditnotification.notification;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

class MockEmailNotificationAdapterTest {

    private final MockEmailNotificationAdapter adapter = new MockEmailNotificationAdapter();

    @Test
    void acceptsLocalMockPayloadWithoutProviderCredentials() {
        EmailNotificationPayload payload = new EmailNotificationPayload(
                "email:evt-1",
                "order-created-email-v1",
                "customer-1",
                "order-1",
                null,
                "evt-1",
                "OrderCreated",
                1,
                Instant.parse("2026-10-04T10:15:30Z"),
                "trace-1",
                "corr-1",
                "Order created for order order-1",
                List.of("Customer reference: customer-1", "Order reference: order-1"));

        assertThatCode(() -> adapter.send(payload)).doesNotThrowAnyException();
    }

    @Test
    void rejectsNullPayloads() {
        assertThatNullPointerException()
                .isThrownBy(() -> adapter.send(null))
                .withMessageContaining("payload");
    }
}
