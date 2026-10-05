package com.kora.ecommerce.auditnotification.persistence;

import com.kora.ecommerce.auditnotification.order.AuditOrderCreatedEvent;
import com.kora.ecommerce.auditnotification.payment.AuditPaymentResultEvent;

public record AuditEventPersistenceResult(
        Outcome outcome,
        String eventId,
        String orderId) {

    public static AuditEventPersistenceResult persisted(AuditOrderCreatedEvent event) {
        return new AuditEventPersistenceResult(
                Outcome.PERSISTED,
                event.eventId().toString(),
                event.orderId().toString());
    }

    public static AuditEventPersistenceResult duplicate(AuditOrderCreatedEvent event) {
        return new AuditEventPersistenceResult(
                Outcome.DUPLICATE,
                event.eventId().toString(),
                event.orderId().toString());
    }

    public static AuditEventPersistenceResult persisted(AuditPaymentResultEvent event) {
        return new AuditEventPersistenceResult(
                Outcome.PERSISTED,
                event.eventId().toString(),
                event.orderId().toString());
    }

    public static AuditEventPersistenceResult duplicate(AuditPaymentResultEvent event) {
        return new AuditEventPersistenceResult(
                Outcome.DUPLICATE,
                event.eventId().toString(),
                event.orderId().toString());
    }

    public enum Outcome {
        PERSISTED,
        DUPLICATE
    }
}
