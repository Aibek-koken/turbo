package com.kora.ecommerce.payment.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.kora.ecommerce.payment.application.OrderCreatedPaymentResult;
import com.kora.ecommerce.payment.application.OrderCreatedPaymentService;
import com.kora.ecommerce.payment.order.InvalidOrderCreatedEventException;
import com.kora.ecommerce.payment.order.OrderCreatedEvent;
import com.kora.ecommerce.payment.order.OrderCreatedEventMetadata;
import com.kora.ecommerce.payment.order.OrderCreatedEventParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderCreatedKafkaListenerTest {

    @Mock
    private OrderCreatedEventParser parser;

    @Mock
    private OrderCreatedPaymentService paymentService;

    @Test
    void delegatesValidPayloadToApplicationService() {
        OrderCreatedEvent event = event();
        when(parser.parse("payload")).thenReturn(event);
        when(paymentService.processOrderCreated(event))
                .thenReturn(OrderCreatedPaymentResult.providerApproved(event, UUID.randomUUID(), UUID.randomUUID()));
        OrderCreatedKafkaListener listener = new OrderCreatedKafkaListener(parser, paymentService);

        listener.onMessage("payload", event.orderId().toString(), "ecommerce.order.events", 0, 12L);

        verify(paymentService).processOrderCreated(event);
    }

    @Test
    void acceptsDuplicateDeliverySkipFromApplicationService() {
        OrderCreatedEvent event = event();
        when(parser.parse("payload")).thenReturn(event);
        when(paymentService.processOrderCreated(event))
                .thenReturn(OrderCreatedPaymentResult.duplicateEvent(event));
        OrderCreatedKafkaListener listener = new OrderCreatedKafkaListener(parser, paymentService);

        listener.onMessage("payload", event.orderId().toString(), "ecommerce.order.events", 0, 12L);

        verify(paymentService).processOrderCreated(event);
    }

    @Test
    void rejectsInvalidPayloadWithoutApplicationSideEffectAndDelegatesToErrorHandler() {
        when(parser.parse("payload")).thenThrow(new InvalidOrderCreatedEventException(
                "currency_invalid",
                new OrderCreatedEventMetadata(
                        "11111111-1111-1111-1111-111111111111",
                        "OrderCreated",
                        1,
                        "22222222-2222-2222-2222-222222222222")));
        OrderCreatedKafkaListener listener = new OrderCreatedKafkaListener(parser, paymentService);

        assertThatThrownBy(() -> listener.onMessage("payload", "key", "ecommerce.order.events", 0, 12L))
                .isInstanceOf(InvalidOrderCreatedEventException.class);

        verifyNoInteractions(paymentService);
    }

    @Test
    void throwsRetryableExceptionWhenProviderFailureShouldBeRetriedByKafka() {
        OrderCreatedEvent event = event();
        when(parser.parse("payload")).thenReturn(event);
        when(paymentService.processOrderCreated(event))
                .thenReturn(OrderCreatedPaymentResult.providerRetryableFailure(
                        event,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        com.kora.ecommerce.payment.persistence.PaymentAttemptOutcome.TIMED_OUT));
        OrderCreatedKafkaListener listener = new OrderCreatedKafkaListener(parser, paymentService);

        assertThatThrownBy(() -> listener.onMessage(
                "payload",
                event.orderId().toString(),
                "ecommerce.order.events",
                0,
                12L))
                .isInstanceOf(RetryableOrderCreatedEventException.class);

        verify(paymentService).processOrderCreated(event);
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
