package com.kora.ecommerce.auditnotification.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

class NotificationDeliveryMongoIndexTest {

    @Test
    void declaresNotificationDeliveryCollection() {
        Document document = NotificationDeliveryDocument.class.getAnnotation(Document.class);

        assertThat(document).isNotNull();
        assertThat(document.collection()).isEqualTo("notification_deliveries");
    }

    @Test
    void declaresUniqueEventChannelReplayIndex() {
        CompoundIndex index = NotificationDeliveryDocument.class.getAnnotation(CompoundIndex.class);

        assertThat(index).isNotNull();
        assertThat(index.name()).isEqualTo("ux_notification_deliveries_event_channel");
        assertThat(index.def()).isEqualTo("{'event_id': 1, 'channel': 1}");
        assertThat(index.unique()).isTrue();
    }

    @Test
    void declaresFocusedDeliverySearchIndexes() throws Exception {
        assertIndexedField("eventId", "event_id", "ix_notification_deliveries_event_id");
        assertIndexedField("channel", "channel", "ix_notification_deliveries_channel");
        assertIndexedField("eventType", "event_type", "ix_notification_deliveries_event_type");
        assertIndexedField("orderId", "order_id", "ix_notification_deliveries_order_id");
        assertIndexedField("customerId", "customer_id", "ix_notification_deliveries_customer_id");
        assertIndexedField("paymentId", "payment_id", "ix_notification_deliveries_payment_id");
        assertIndexedField("status", "status", "ix_notification_deliveries_status");
        assertIndexedField("correlationId", "correlation_id", "ix_notification_deliveries_correlation_id");
    }

    private static void assertIndexedField(String propertyName, String fieldName, String indexName) throws Exception {
        java.lang.reflect.Field property = NotificationDeliveryDocument.class.getDeclaredField(propertyName);

        Field field = property.getAnnotation(Field.class);
        Indexed indexed = property.getAnnotation(Indexed.class);

        assertThat(field.value()).isEqualTo(fieldName);
        assertThat(indexed.name()).isEqualTo(indexName);
        assertThat(indexed.unique()).isFalse();
    }
}
