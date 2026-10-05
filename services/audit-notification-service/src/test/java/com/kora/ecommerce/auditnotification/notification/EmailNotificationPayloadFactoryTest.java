package com.kora.ecommerce.auditnotification.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class EmailNotificationPayloadFactoryTest {

    private static final Instant EVENT_OCCURRED_AT = Instant.parse("2026-10-04T10:15:30Z");
    private static final Instant CREATED_AT = Instant.parse("2026-10-04T10:15:31Z");

    private final EmailNotificationPayloadFactory factory = new EmailNotificationPayloadFactory();

    @Test
    void buildsDeterministicOrderCreatedEmailPayloadFromDeliveryRecord() {
        NotificationDeliveryDocument delivery = delivery("OrderCreated", null);

        EmailNotificationPayload payload = factory.from(delivery);

        assertThat(payload.messageId()).isEqualTo("email:evt-1");
        assertThat(payload.templateKey()).isEqualTo("order-created-email-v1");
        assertThat(payload.recipientCustomerId()).isEqualTo("customer-1");
        assertThat(payload.orderId()).isEqualTo("order-1");
        assertThat(payload.paymentId()).isNull();
        assertThat(payload.subject()).isEqualTo("Order created for order order-1");
        assertThat(payload.bodyLines()).containsExactly(
                "Customer reference: customer-1",
                "Order reference: order-1",
                "Order status: CREATED",
                "Event: OrderCreated v1",
                "Event ID: evt-1",
                "Occurred at: 2026-10-04T10:15:30Z",
                "Correlation ID: corr-1");
    }

    @Test
    void buildsDeterministicPaymentSucceededEmailPayloadFromDeliveryRecord() {
        NotificationDeliveryDocument delivery = delivery("PaymentSucceeded", "payment-1");

        EmailNotificationPayload payload = factory.from(delivery);

        assertThat(payload.templateKey()).isEqualTo("payment-succeeded-email-v1");
        assertThat(payload.paymentId()).isEqualTo("payment-1");
        assertThat(payload.subject()).isEqualTo("Payment succeeded for order order-1");
        assertThat(payload.bodyLines()).contains(
                "Payment reference: payment-1",
                "Payment status: SUCCEEDED");
    }

    @Test
    void buildsDeterministicPaymentFailedEmailPayloadFromDeliveryRecord() {
        NotificationDeliveryDocument delivery = delivery("PaymentFailed", "payment-1");

        EmailNotificationPayload payload = factory.from(delivery);

        assertThat(payload.templateKey()).isEqualTo("payment-failed-email-v1");
        assertThat(payload.subject()).isEqualTo("Payment failed for order order-1");
        assertThat(payload.bodyLines()).contains(
                "Payment reference: payment-1",
                "Payment status: FAILED");
    }

    @Test
    void rejectsNonEmailOrUnsupportedDeliveryRecords() {
        assertThatThrownBy(() -> factory.from(pushDelivery()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("EMAIL");

        assertThatThrownBy(() -> factory.from(delivery("CatalogProductUpdated", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported");
    }

    private static NotificationDeliveryDocument delivery(String eventType, String paymentId) {
        return new NotificationDeliveryDocument(
                "evt-1",
                NotificationChannel.EMAIL,
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

    private static NotificationDeliveryDocument pushDelivery() {
        return new NotificationDeliveryDocument(
                "evt-1",
                NotificationChannel.PUSH,
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
