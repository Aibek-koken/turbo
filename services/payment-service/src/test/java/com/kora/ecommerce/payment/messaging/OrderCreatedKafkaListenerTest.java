package com.kora.ecommerce.payment.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.kora.ecommerce.payment.application.OrderCreatedPaymentResult;
import com.kora.ecommerce.payment.application.OrderCreatedPaymentService;
import com.kora.ecommerce.payment.observability.PaymentOperationalMetrics;
import com.kora.ecommerce.payment.order.InvalidOrderCreatedEventException;
import com.kora.ecommerce.payment.order.OrderCreatedEvent;
import com.kora.ecommerce.payment.order.OrderCreatedEventMetadata;
import com.kora.ecommerce.payment.order.OrderCreatedEventParser;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

@ExtendWith(MockitoExtension.class)
class OrderCreatedKafkaListenerTest {

    @Mock
    private OrderCreatedEventParser parser;

    @Mock
    private OrderCreatedPaymentService paymentService;

    @BeforeEach
    void clearMdcBeforeTest() {
        MDC.clear();
    }

    @AfterEach
    void clearMdcAfterTest() {
        MDC.clear();
    }

    @Test
    void delegatesValidPayloadToApplicationService() {
        OrderCreatedEvent event = event();
        when(parser.parse("payload")).thenReturn(event);
        when(paymentService.processOrderCreated(event))
                .thenReturn(OrderCreatedPaymentResult.providerApproved(event, UUID.randomUUID(), UUID.randomUUID()));
        OrderCreatedKafkaListener listener = listener();

        listener.onMessage("payload", event.orderId().toString(), "ecommerce.order.events", 0, 12L);

        verify(paymentService).processOrderCreated(event);
    }

    @Test
    void acceptsDuplicateDeliverySkipFromApplicationService() {
        OrderCreatedEvent event = event();
        when(parser.parse("payload")).thenReturn(event);
        when(paymentService.processOrderCreated(event))
                .thenReturn(OrderCreatedPaymentResult.duplicateEvent(event));
        OrderCreatedKafkaListener listener = listener();

        listener.onMessage("payload", event.orderId().toString(), "ecommerce.order.events", 0, 12L);

        verify(paymentService).processOrderCreated(event);
    }

    @Test
    void rejectsInvalidPayloadWithoutApplicationSideEffectAndDelegatesToErrorHandler() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        when(parser.parse("payload")).thenThrow(new InvalidOrderCreatedEventException(
                "currency_invalid",
                new OrderCreatedEventMetadata(
                        "11111111-1111-1111-1111-111111111111",
                        "OrderCreated",
                        1,
                        "22222222-2222-2222-2222-222222222222")));
        OrderCreatedKafkaListener listener = listener(registry);

        assertThatThrownBy(() -> listener.onMessage("payload", "key", "ecommerce.order.events", 0, 12L))
                .isInstanceOf(InvalidOrderCreatedEventException.class);

        verifyNoInteractions(paymentService);
        assertThat(counter(
                registry,
                PaymentOperationalMetrics.KAFKA_CONSUMER_EVENTS,
                "topic", "ecommerce.order.events",
                "event_type", "OrderCreated",
                "outcome", "rejected")).isEqualTo(1.0);
    }

    @Test
    void throwsRetryableExceptionWhenProviderFailureShouldBeRetriedByKafka() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OrderCreatedEvent event = event();
        when(parser.parse("payload")).thenReturn(event);
        when(paymentService.processOrderCreated(event))
                .thenReturn(OrderCreatedPaymentResult.providerRetryableFailure(
                        event,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        com.kora.ecommerce.payment.persistence.PaymentAttemptOutcome.TIMED_OUT));
        OrderCreatedKafkaListener listener = listener(registry);

        assertThatThrownBy(() -> listener.onMessage(
                "payload",
                event.orderId().toString(),
                "ecommerce.order.events",
                0,
                12L))
                .isInstanceOf(RetryableOrderCreatedEventException.class);

        verify(paymentService).processOrderCreated(event);
        assertThat(counter(
                registry,
                PaymentOperationalMetrics.KAFKA_CONSUMER_EVENTS,
                "topic", "ecommerce.order.events",
                "event_type", "OrderCreated",
                "outcome", "provider_retryable_failure")).isEqualTo(1.0);
    }

    @Test
    void recordsTerminalPaymentFailureMetricForDeclinedProviderResult() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OrderCreatedEvent event = event();
        when(parser.parse("payload")).thenReturn(event);
        when(paymentService.processOrderCreated(event))
                .thenReturn(OrderCreatedPaymentResult.providerDeclined(
                        event,
                        UUID.randomUUID(),
                        UUID.randomUUID()));
        OrderCreatedKafkaListener listener = listener(registry);

        listener.onMessage("payload", event.orderId().toString(), "ecommerce.order.events", 0, 12L);

        assertThat(counter(
                registry,
                PaymentOperationalMetrics.PAYMENT_TERMINAL_OUTCOMES,
                "event_type", "PaymentFailed",
                "outcome", "failed")).isEqualTo(1.0);
        assertThat(counter(
                registry,
                PaymentOperationalMetrics.KAFKA_CONSUMER_EVENTS,
                "topic", "ecommerce.order.events",
                "event_type", "OrderCreated",
                "outcome", "provider_declined")).isEqualTo(1.0);
    }

    @Test
    void bindsEnvelopeMetadataToMdcOnlyDuringOrderCreatedHandling() {
        OrderCreatedEvent event = event();
        MDC.put(KafkaEventProcessingObservation.TRACE_ID_MDC_KEY, "previous-trace");
        MDC.put(KafkaEventProcessingObservation.CORRELATION_ID_MDC_KEY, "previous-correlation");
        when(parser.parse("payload")).thenReturn(event);
        when(paymentService.processOrderCreated(event)).thenAnswer(invocation -> {
            assertThat(MDC.get(KafkaEventProcessingObservation.TRACE_ID_MDC_KEY)).isEqualTo(event.traceId());
            assertThat(MDC.get(KafkaEventProcessingObservation.CORRELATION_ID_MDC_KEY))
                    .isEqualTo(event.correlationId());
            assertThat(MDC.get(KafkaEventProcessingObservation.EVENT_ID_MDC_KEY))
                    .isEqualTo(event.eventId().toString());
            assertThat(MDC.get(KafkaEventProcessingObservation.EVENT_TYPE_MDC_KEY)).isEqualTo("OrderCreated");
            assertThat(MDC.get(KafkaEventProcessingObservation.EVENT_VERSION_MDC_KEY))
                    .isEqualTo(String.valueOf(event.eventVersion()));
            assertThat(MDC.get(KafkaEventProcessingObservation.AGGREGATE_ID_MDC_KEY))
                    .isEqualTo(event.aggregateId().toString());
            return OrderCreatedPaymentResult.providerApproved(event, UUID.randomUUID(), UUID.randomUUID());
        });
        OrderCreatedKafkaListener listener = listener();

        listener.onMessage("payload", event.orderId().toString(), "ecommerce.order.events", 0, 12L);

        assertThat(MDC.get(KafkaEventProcessingObservation.TRACE_ID_MDC_KEY)).isEqualTo("previous-trace");
        assertThat(MDC.get(KafkaEventProcessingObservation.CORRELATION_ID_MDC_KEY))
                .isEqualTo("previous-correlation");
        assertThat(MDC.get(KafkaEventProcessingObservation.EVENT_ID_MDC_KEY)).isNull();
        assertThat(MDC.get(KafkaEventProcessingObservation.EVENT_TYPE_MDC_KEY)).isNull();
        assertThat(MDC.get(KafkaEventProcessingObservation.EVENT_VERSION_MDC_KEY)).isNull();
        assertThat(MDC.get(KafkaEventProcessingObservation.AGGREGATE_ID_MDC_KEY)).isNull();
    }

    private OrderCreatedKafkaListener listener() {
        return listener(new SimpleMeterRegistry());
    }

    private OrderCreatedKafkaListener listener(SimpleMeterRegistry registry) {
        return new OrderCreatedKafkaListener(
                parser,
                paymentService,
                ObservationRegistry.NOOP,
                new PaymentOperationalMetrics(registry));
    }

    private static double counter(
            SimpleMeterRegistry registry,
            String name,
            String... tags) {
        return registry.get(name)
                .tag("service", "payment-service")
                .tags(tags)
                .counter()
                .count();
    }

    private OrderCreatedEvent event() {
        UUID orderId = UUID.randomUUID();
        return new OrderCreatedEvent(
                UUID.randomUUID(),
                1,
                orderId,
                Instant.parse("2026-10-04T10:00:00Z"),
                "trace-123",
                "corr-123",
                orderId,
                "jwt-customer-123",
                new BigDecimal("42.9900"),
                "USD");
    }
}
