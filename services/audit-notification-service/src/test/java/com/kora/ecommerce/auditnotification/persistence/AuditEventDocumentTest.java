package com.kora.ecommerce.auditnotification.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.bson.Document;
import org.junit.jupiter.api.Test;

class AuditEventDocumentTest {

    @Test
    void storesEnvelopeMetadataSourcePositionAndStructuredPayload() {
        Instant occurredAt = Instant.parse("2026-10-04T10:15:30Z");
        Instant receivedAt = Instant.parse("2026-10-04T10:15:31Z");
        Document payload = new Document()
                .append("eventId", "evt-1")
                .append("eventType", "PaymentSucceeded")
                .append("eventVersion", 1)
                .append("aggregateId", "order-1")
                .append("occurredAt", occurredAt.toString())
                .append("traceId", "trace-1")
                .append("correlationId", "corr-1")
                .append("data", new Document()
                        .append("orderId", "order-1")
                        .append("customerId", "customer-1")
                        .append("paymentId", "payment-1"));

        AuditEventDocument document = new AuditEventDocument(
                "evt-1",
                "PaymentSucceeded",
                1,
                "order-1",
                "order-1",
                "customer-1",
                "payment-1",
                occurredAt,
                receivedAt,
                "trace-1",
                "corr-1",
                "ecommerce.payment.events",
                2,
                42L,
                payload);

        assertThat(document.getEventId()).isEqualTo("evt-1");
        assertThat(document.getEventType()).isEqualTo("PaymentSucceeded");
        assertThat(document.getEventVersion()).isEqualTo(1);
        assertThat(document.getAggregateId()).isEqualTo("order-1");
        assertThat(document.getOrderId()).isEqualTo("order-1");
        assertThat(document.getCustomerId()).isEqualTo("customer-1");
        assertThat(document.getPaymentId()).isEqualTo("payment-1");
        assertThat(document.getOccurredAt()).isEqualTo(occurredAt);
        assertThat(document.getReceivedAt()).isEqualTo(receivedAt);
        assertThat(document.getTraceId()).isEqualTo("trace-1");
        assertThat(document.getCorrelationId()).isEqualTo("corr-1");
        assertThat(document.getSourceTopic()).isEqualTo("ecommerce.payment.events");
        assertThat(document.getSourcePartition()).isEqualTo(2);
        assertThat(document.getSourceOffset()).isEqualTo(42L);
        assertThat(document.getPayload()).containsEntry("eventId", "evt-1");
        assertThat(document.getPayload().get("data", Document.class))
                .containsEntry("paymentId", "payment-1");
    }

    @Test
    void payloadIsDefensivelyCopied() {
        Document payload = samplePayload();
        AuditEventDocument document = sampleDocument(payload);

        payload.put("eventId", "mutated");
        Document returnedPayload = document.getPayload();
        returnedPayload.put("eventId", "also-mutated");

        assertThat(document.getPayload()).containsEntry("eventId", "evt-1");
    }

    @Test
    void rejectsInvalidRequiredMetadata() {
        assertThatThrownBy(() -> new AuditEventDocument(
                        " ",
                        "OrderCreated",
                        1,
                        "order-1",
                        "order-1",
                        "customer-1",
                        null,
                        Instant.parse("2026-10-04T10:15:30Z"),
                        Instant.parse("2026-10-04T10:15:31Z"),
                        "trace-1",
                        "corr-1",
                        "ecommerce.order.events",
                        0,
                        1L,
                        samplePayload()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventId");
    }

    @Test
    void rejectsInvalidEventVersionAndSourcePosition() {
        assertThatThrownBy(() -> sampleDocumentWithVersion(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventVersion");

        assertThatThrownBy(() -> sampleDocumentWithSourcePosition(-1, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sourcePartition");

        assertThatThrownBy(() -> sampleDocumentWithSourcePosition(0, -1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sourceOffset");
    }

    private static AuditEventDocument sampleDocument(Document payload) {
        return new AuditEventDocument(
                "evt-1",
                "OrderCreated",
                1,
                "order-1",
                "order-1",
                "customer-1",
                null,
                Instant.parse("2026-10-04T10:15:30Z"),
                Instant.parse("2026-10-04T10:15:31Z"),
                "trace-1",
                "corr-1",
                "ecommerce.order.events",
                0,
                1L,
                payload);
    }

    private static AuditEventDocument sampleDocumentWithVersion(int eventVersion) {
        return new AuditEventDocument(
                "evt-1",
                "OrderCreated",
                eventVersion,
                "order-1",
                "order-1",
                "customer-1",
                null,
                Instant.parse("2026-10-04T10:15:30Z"),
                Instant.parse("2026-10-04T10:15:31Z"),
                "trace-1",
                "corr-1",
                "ecommerce.order.events",
                0,
                1L,
                samplePayload());
    }

    private static AuditEventDocument sampleDocumentWithSourcePosition(int partition, long offset) {
        return new AuditEventDocument(
                "evt-1",
                "OrderCreated",
                1,
                "order-1",
                "order-1",
                "customer-1",
                null,
                Instant.parse("2026-10-04T10:15:30Z"),
                Instant.parse("2026-10-04T10:15:31Z"),
                "trace-1",
                "corr-1",
                "ecommerce.order.events",
                partition,
                offset,
                samplePayload());
    }

    private static Document samplePayload() {
        return new Document()
                .append("eventId", "evt-1")
                .append("eventType", "OrderCreated")
                .append("eventVersion", 1)
                .append("aggregateId", "order-1")
                .append("data", new Document()
                        .append("orderId", "order-1")
                        .append("customerId", "customer-1"));
    }
}
