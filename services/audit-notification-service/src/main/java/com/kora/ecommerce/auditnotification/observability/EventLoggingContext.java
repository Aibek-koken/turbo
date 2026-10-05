package com.kora.ecommerce.auditnotification.observability;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.MDC;
import org.springframework.util.StringUtils;

public final class EventLoggingContext implements AutoCloseable {

    public static final String TRACE_ID_MDC_KEY = "traceId";
    public static final String CORRELATION_ID_MDC_KEY = "correlationId";
    public static final String EVENT_ID_MDC_KEY = "eventId";
    public static final String EVENT_TYPE_MDC_KEY = "eventType";
    public static final String EVENT_VERSION_MDC_KEY = "eventVersion";
    public static final String AGGREGATE_ID_MDC_KEY = "aggregateId";

    private final Map<String, String> previousValues = new LinkedHashMap<>();

    private EventLoggingContext(
            String eventId,
            String eventType,
            int eventVersion,
            String aggregateId,
            String traceId,
            String correlationId) {
        putIfPresent(TRACE_ID_MDC_KEY, traceId);
        putIfPresent(CORRELATION_ID_MDC_KEY, correlationId);
        putIfPresent(EVENT_ID_MDC_KEY, eventId);
        putIfPresent(EVENT_TYPE_MDC_KEY, eventType);
        putIfPresent(EVENT_VERSION_MDC_KEY, String.valueOf(eventVersion));
        putIfPresent(AGGREGATE_ID_MDC_KEY, aggregateId);
    }

    public static EventLoggingContext open(
            String eventId,
            String eventType,
            int eventVersion,
            String aggregateId,
            String traceId,
            String correlationId) {
        return new EventLoggingContext(
                eventId,
                eventType,
                eventVersion,
                aggregateId,
                traceId,
                correlationId);
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
