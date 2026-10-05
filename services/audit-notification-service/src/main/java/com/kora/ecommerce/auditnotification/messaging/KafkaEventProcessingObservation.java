package com.kora.ecommerce.auditnotification.messaging;

import java.util.Objects;
import java.util.function.Supplier;

import com.kora.ecommerce.auditnotification.observability.EventLoggingContext;
import com.kora.ecommerce.auditnotification.order.AuditOrderCreatedEvent;
import com.kora.ecommerce.auditnotification.payment.AuditPaymentResultEvent;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

final class KafkaEventProcessingObservation {

    private final ObservationRegistry observationRegistry;

    KafkaEventProcessingObservation(ObservationRegistry observationRegistry) {
        this.observationRegistry = Objects.requireNonNull(
                observationRegistry,
                "observationRegistry must not be null");
    }

    <T> T observeOrderCreated(AuditOrderCreatedEvent event, Supplier<T> handler) {
        Objects.requireNonNull(event, "event must not be null");
        return observe(
                "audit.order-created.process",
                event.eventId().toString(),
                event.eventType(),
                event.eventVersion(),
                event.aggregateId().toString(),
                event.traceId(),
                event.correlationId(),
                handler);
    }

    <T> T observePaymentResult(AuditPaymentResultEvent event, Supplier<T> handler) {
        Objects.requireNonNull(event, "event must not be null");
        return observe(
                "audit.payment-result.process",
                event.eventId().toString(),
                event.eventType(),
                event.eventVersion(),
                event.aggregateId().toString(),
                event.traceId(),
                event.correlationId(),
                handler);
    }

    private <T> T observe(
            String observationName,
            String eventId,
            String eventType,
            int eventVersion,
            String aggregateId,
            String traceId,
            String correlationId,
            Supplier<T> handler) {
        Objects.requireNonNull(handler, "handler must not be null");
        return Observation.createNotStarted(observationName, observationRegistry)
                .contextualName(observationName)
                .lowCardinalityKeyValue("messaging.operation", "process")
                .lowCardinalityKeyValue("event.type", eventType)
                .lowCardinalityKeyValue("event.version", String.valueOf(eventVersion))
                .observe(() -> {
                    try (EventLoggingContext ignored = EventLoggingContext.open(
                            eventId,
                            eventType,
                            eventVersion,
                            aggregateId,
                            traceId,
                            correlationId)) {
                        return handler.get();
                    }
                });
    }
}
