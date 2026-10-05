package com.kora.ecommerce.auditnotification.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

class AuditEventMongoIndexTest {

    @Test
    void declaresAuditCollection() {
        Document document = AuditEventDocument.class.getAnnotation(Document.class);

        assertThat(document).isNotNull();
        assertThat(document.collection()).isEqualTo("audit_events");
    }

    @Test
    void declaresUniqueEventIdReplayIndex() throws Exception {
        java.lang.reflect.Field eventId = AuditEventDocument.class.getDeclaredField("eventId");

        Field field = eventId.getAnnotation(Field.class);
        Indexed indexed = eventId.getAnnotation(Indexed.class);

        assertThat(field.value()).isEqualTo("event_id");
        assertThat(indexed.name()).isEqualTo("ux_audit_events_event_id");
        assertThat(indexed.unique()).isTrue();
    }

    @Test
    void declaresFocusedSearchIndexes() throws Exception {
        assertIndexedField("eventType", "event_type", "ix_audit_events_event_type");
        assertIndexedField("aggregateId", "aggregate_id", "ix_audit_events_aggregate_id");
        assertIndexedField("orderId", "order_id", "ix_audit_events_order_id");
        assertIndexedField("customerId", "customer_id", "ix_audit_events_customer_id");
        assertIndexedField("paymentId", "payment_id", "ix_audit_events_payment_id");
        assertIndexedField("correlationId", "correlation_id", "ix_audit_events_correlation_id");
        assertIndexedField("occurredAt", "occurred_at", "ix_audit_events_occurred_at");
    }

    private static void assertIndexedField(String propertyName, String fieldName, String indexName) throws Exception {
        java.lang.reflect.Field property = AuditEventDocument.class.getDeclaredField(propertyName);

        Field field = property.getAnnotation(Field.class);
        Indexed indexed = property.getAnnotation(Indexed.class);

        assertThat(field.value()).isEqualTo(fieldName);
        assertThat(indexed.name()).isEqualTo(indexName);
        assertThat(indexed.unique()).isFalse();
    }
}
