package com.kora.ecommerce.payment.messaging;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import com.kora.ecommerce.payment.order.OrderCreatedEvent;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;

final class KafkaEventProcessingObservation {

    static final String TRACE_ID_MDC_KEY = "traceId";
    static final String CORRELATION_ID_MDC_KEY = "correlationId";
    static final String EVENT_ID_MDC_KEY = "eventId";
    static final String EVENT_TYPE_MDC_KEY = "eventType";
    static final String EVENT_VERSION_MDC_KEY = "eventVersion";
    static final String AGGREGATE_ID_MDC_KEY = "aggregateId";

    private static final String ORDER_CREATED_EVENT_TYPE = "OrderCreated";

    private final ObservationRegistry observationRegistry;

    KafkaEventProcessingObservation(ObservationRegistry observationRegistry) {
        this.observationRegistry = Objects.requireNonNull(
                observationRegistry,
                "observationRegistry must not be null");
    }

    <T> T observeOrderCreated(OrderCreatedEvent event, Supplier<T> handler) {
        Objects.requireNonNull(event, "event must not be null");
        return observe(
                "payment.order-created.process",
                new EventLoggingContext(
                        event.eventId().toString(),
                        ORDER_CREATED_EVENT_TYPE,
                        event.eventVersion(),
                        event.aggregateId().toString(),
                        event.traceId(),
                        event.correlationId()),
                handler);
    }

    private <T> T observe(
            String observationName,
            EventLoggingContext context,
            Supplier<T> handler) {
        Objects.requireNonNull(handler, "handler must not be null");
        return Observation.createNotStarted(observationName, observationRegistry)
                .contextualName(observationName)
                .lowCardinalityKeyValue("messaging.operation", "process")
                .lowCardinalityKeyValue("event.type", context.eventType())
                .lowCardinalityKeyValue("event.version", String.valueOf(context.eventVersion()))
                .observe(() -> {
                    try (MdcScope ignored = MdcScope.open(context)) {
                        return handler.get();
                    }
                });
    }

    private record EventLoggingContext(
            String eventId,
            String eventType,
            int eventVersion,
            String aggregateId,
            String traceId,
            String correlationId) {
    }

    private static final class MdcScope implements AutoCloseable {

        private final Map<String, String> previousValues = new LinkedHashMap<>();

        private MdcScope(EventLoggingContext context) {
            putIfPresent(TRACE_ID_MDC_KEY, context.traceId());
            putIfPresent(CORRELATION_ID_MDC_KEY, context.correlationId());
            putIfPresent(EVENT_ID_MDC_KEY, context.eventId());
            putIfPresent(EVENT_TYPE_MDC_KEY, context.eventType());
            putIfPresent(EVENT_VERSION_MDC_KEY, String.valueOf(context.eventVersion()));
            putIfPresent(AGGREGATE_ID_MDC_KEY, context.aggregateId());
        }

        static MdcScope open(EventLoggingContext context) {
            return new MdcScope(context);
        }

        private void putIfPresent(String key, String value) {
            if (!StringUtils.hasText(value)) {
                return;
            }
            previousValues.put(key, MDC.get(key));
            MDC.put(key, value.trim());
        }

        @Override
        public void close() {
            previousValues.forEach((key, previousValue) -> {
                if (previousValue == null) {
                    MDC.remove(key);
                } else {
                    MDC.put(key, previousValue);
                }
            });
        }
    }
}
