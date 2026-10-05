package com.kora.ecommerce.auditnotification.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class PushNotificationPayloadFactoryTest {

    private static final Instant EVENT_OCCURRED_AT = Instant.parse("2026-10-04T10:15:30Z");
    private static final Instant CREATED_AT = Instant.parse("2026-10-04T10:15:31Z");

    private final PushNotificationPayloadFactory factory = new PushNotificationPayloadFactory();

    @Test
    void buildsDeterministicOrderCreatedPushPayloadFromDeliveryRecord() {
        NotificationDeliveryDocument delivery = delivery("OrderCreated", null);

        PushNotificationPayload payload = factory.from(delivery);

        assertThat(payload.messageId()).isEqualTo("push:evt-1");
        assertThat(payload.notificationType()).isEqualTo("order-created-push-v1");
        assertThat(payload.recipientCustomerId()).isEqualTo("customer-1");
        assertThat(payload.orderId()).isEqualTo("order-1");
        assertThat(payload.paymentId()).isNull();
        assertThat(payload.title()).isEqualTo("Order created");
        assertThat(payload.body()).isEqualTo("Order created for order order-1");
        assertThat(payload.data()).containsExactlyEntriesOf(expectedData("order-created-push-v1", null));
    }

    @Test
    void buildsDeterministicPaymentSucceededPushPayloadFromDeliveryRecord() {
        NotificationDeliveryDocument delivery = delivery("PaymentSucceeded", "payment-1");

        PushNotificationPayload payload = factory.from(delivery);

        assertThat(payload.notificationType()).isEqualTo("payment-succeeded-push-v1");
        assertThat(payload.paymentId()).isEqualTo("payment-1");
        assertThat(payload.title()).isEqualTo("Payment succeeded");
        assertThat(payload.body()).isEqualTo("Payment succeeded for order order-1");
        assertThat(payload.data()).containsEntry("paymentId", "payment-1");
    }

    @Test
    void buildsDeterministicPaymentFailedPushPayloadFromDeliveryRecord() {
        NotificationDeliveryDocument delivery = delivery("PaymentFailed", "payment-1");

        PushNotificationPayload payload = factory.from(delivery);

        assertThat(payload.notificationType()).isEqualTo("payment-failed-push-v1");
        assertThat(payload.title()).isEqualTo("Payment failed");
        assertThat(payload.body()).isEqualTo("Payment failed for order order-1");
        assertThat(payload.data()).containsEntry("paymentId", "payment-1");
    }

    @Test
    void rejectsNonPushOrUnsupportedDeliveryRecords() {
        assertThatThrownBy(() -> factory.from(emailDelivery()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PUSH");

        assertThatThrownBy(() -> factory.from(delivery("CatalogProductUpdated", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported");
    }

    private static Map<String, String> expectedData(String notificationType, String paymentId) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("notificationType", notificationType);
        data.put("customerId", "customer-1");
        data.put("orderId", "order-1");
        if (paymentId != null) {
            data.put("paymentId", paymentId);
        }
        data.put("eventId", "evt-1");
        data.put("eventType", "OrderCreated");
        data.put("eventVersion", "1");
        data.put("eventOccurredAt", "2026-10-04T10:15:30Z");
        data.put("traceId", "trace-1");
        data.put("correlationId", "corr-1");
        return data;
    }

    private static NotificationDeliveryDocument delivery(String eventType, String paymentId) {
        return new NotificationDeliveryDocument(
                "evt-1",
                NotificationChannel.PUSH,
                eventType,
                1,
                "order-1",
                "customer-1",
                paymentId,
                NotificationDeliveryStatus.PENDING,
                0,
                null,
                null,
                EVENT_OCCURRED_AT,
                CREATED_AT,
                CREATED_AT,
                "trace-1",
                "corr-1");
    }

    private static NotificationDeliveryDocument emailDelivery() {
        return new NotificationDeliveryDocument(
                "evt-1",
                NotificationChannel.EMAIL,
                "OrderCreated",
                1,
                "order-1",
                "customer-1",
                null,
                NotificationDeliveryStatus.PENDING,
                0,
                null,
                null,
                EVENT_OCCURRED_AT,
                CREATED_AT,
                CREATED_AT,
                "trace-1",
                "corr-1");
    }
}
