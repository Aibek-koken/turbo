package com.kora.ecommerce.order.application.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.kora.ecommerce.order.observability.OrderOperationalMetrics;
import com.kora.ecommerce.order.persistence.OrderStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class PaymentResultKafkaListenerTest {

    @BeforeEach
    void clearMdcBeforeTest() {
        MDC.clear();
    }

    @AfterEach
    void clearMdcAfterTest() {
        MDC.clear();
    }

    @Test
    void handlesValidPaymentResultEventThroughUpdater() {
        PaymentResultEventParser parser = mock(PaymentResultEventParser.class);
        PaymentResultOrderUpdater updater = mock(PaymentResultOrderUpdater.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PaymentResultKafkaListener listener = listener(parser, updater, registry);
        PaymentResultEnvelope event = succeededEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        when(parser.parse("payload")).thenReturn(event);
        when(updater.handle(event)).thenReturn(PaymentResultHandlingResult.processed(
                event,
                OrderStatus.PAYMENT_PENDING,
                OrderStatus.PAID,
                List.of(OrderStatus.PAID)));

        listener.handle("payload", event.orderId().toString(), "ecommerce.payment.events", 42L);

        verify(updater).handle(event);
        assertThat(counter(
                registry,
                OrderOperationalMetrics.KAFKA_CONSUMER_EVENTS,
                "topic", "ecommerce.payment.events",
                "event_type", "PaymentSucceeded",
                "outcome", "processed")).isEqualTo(1.0);
    }

    @Test
    void rejectsMalformedPayloadWithoutCallingUpdaterAndDelegatesToErrorHandler() {
        PaymentResultEventParser parser = mock(PaymentResultEventParser.class);
        PaymentResultOrderUpdater updater = mock(PaymentResultOrderUpdater.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PaymentResultKafkaListener listener = listener(parser, updater, registry);
        when(parser.parse("{bad-json")).thenThrow(PaymentResultEventException.of(
                PaymentResultEventFailure.MALFORMED_JSON,
                "Payment result event payload is not valid JSON."));

        assertThatThrownBy(() -> listener.handle("{bad-json", "order-key", "ecommerce.payment.events", 43L))
                .isInstanceOf(PaymentResultEventException.class);

        verifyNoInteractions(updater);
        assertThat(counter(
                registry,
                OrderOperationalMetrics.KAFKA_CONSUMER_EVENTS,
                "topic", "ecommerce.payment.events",
                "event_type", "PaymentResult",
                "outcome", "rejected")).isEqualTo(1.0);
    }

    @Test
    void bindsEnvelopeMetadataToMdcOnlyDuringPaymentResultHandling() {
        PaymentResultEventParser parser = mock(PaymentResultEventParser.class);
        PaymentResultOrderUpdater updater = mock(PaymentResultOrderUpdater.class);
        PaymentResultKafkaListener listener = listener(parser, updater, new SimpleMeterRegistry());
        PaymentResultEnvelope event = succeededEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        MDC.put(KafkaEventProcessingObservation.TRACE_ID_MDC_KEY, "previous-trace");
        MDC.put(KafkaEventProcessingObservation.CORRELATION_ID_MDC_KEY, "previous-correlation");
        when(parser.parse("payload")).thenReturn(event);
        when(updater.handle(event)).thenAnswer(invocation -> {
            assertThat(MDC.get(KafkaEventProcessingObservation.TRACE_ID_MDC_KEY)).isEqualTo(event.traceId());
            assertThat(MDC.get(KafkaEventProcessingObservation.CORRELATION_ID_MDC_KEY))
                    .isEqualTo(event.correlationId());
            assertThat(MDC.get(KafkaEventProcessingObservation.EVENT_ID_MDC_KEY))
                    .isEqualTo(event.eventId().toString());
            assertThat(MDC.get(KafkaEventProcessingObservation.EVENT_TYPE_MDC_KEY)).isEqualTo(event.eventType());
            assertThat(MDC.get(KafkaEventProcessingObservation.EVENT_VERSION_MDC_KEY))
                    .isEqualTo(String.valueOf(event.eventVersion()));
            assertThat(MDC.get(KafkaEventProcessingObservation.AGGREGATE_ID_MDC_KEY))
                    .isEqualTo(event.aggregateId().toString());
            return PaymentResultHandlingResult.processed(
                    event,
                    OrderStatus.PAYMENT_PENDING,
                    OrderStatus.PAID,
                    List.of(OrderStatus.PAID));
        });

        listener.handle("payload", event.orderId().toString(), "ecommerce.payment.events", 42L);

        assertThat(MDC.get(KafkaEventProcessingObservation.TRACE_ID_MDC_KEY)).isEqualTo("previous-trace");
        assertThat(MDC.get(KafkaEventProcessingObservation.CORRELATION_ID_MDC_KEY))
                .isEqualTo("previous-correlation");
        assertThat(MDC.get(KafkaEventProcessingObservation.EVENT_ID_MDC_KEY)).isNull();
        assertThat(MDC.get(KafkaEventProcessingObservation.EVENT_TYPE_MDC_KEY)).isNull();
        assertThat(MDC.get(KafkaEventProcessingObservation.EVENT_VERSION_MDC_KEY)).isNull();
        assertThat(MDC.get(KafkaEventProcessingObservation.AGGREGATE_ID_MDC_KEY)).isNull();
    }

    @Test
    void recordsConsumerFailureMetricWhenUpdaterThrows() {
        PaymentResultEventParser parser = mock(PaymentResultEventParser.class);
        PaymentResultOrderUpdater updater = mock(PaymentResultOrderUpdater.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PaymentResultKafkaListener listener = listener(parser, updater, registry);
        PaymentResultEnvelope event = succeededEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        when(parser.parse("payload")).thenReturn(event);
        when(updater.handle(event)).thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> listener.handle(
                "payload",
                event.orderId().toString(),
                "ecommerce.payment.events",
                42L))
                .isInstanceOf(IllegalStateException.class);

        assertThat(counter(
                registry,
                OrderOperationalMetrics.KAFKA_CONSUMER_EVENTS,
                "topic", "ecommerce.payment.events",
                "event_type", "PaymentSucceeded",
                "outcome", "failure")).isEqualTo(1.0);
    }

    private static PaymentResultKafkaListener listener(
            PaymentResultEventParser parser,
            PaymentResultOrderUpdater updater,
            SimpleMeterRegistry registry) {
        return new PaymentResultKafkaListener(
                parser,
                updater,
                ObservationRegistry.NOOP,
                new OrderOperationalMetrics(registry));
    }

    private static double counter(
            SimpleMeterRegistry registry,
            String name,
            String... tags) {
        return registry.get(name)
                .tag("service", "order-service")
                .tags(tags)
                .counter()
                .count();
    }

    private static PaymentResultEnvelope succeededEvent(UUID eventId, UUID orderId, UUID paymentId) {
        return new PaymentSucceededEnvelope(
                eventId,
                1,
                orderId,
                Instant.parse("2026-10-04T10:15:30Z"),
                "trace-123",
                "correlation-123",
                new PaymentResultData(orderId, paymentId));
    }
}
