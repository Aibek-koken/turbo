package com.kora.ecommerce.order.application.payment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.kora.ecommerce.order.persistence.OrderStatus;
import org.junit.jupiter.api.Test;

class PaymentResultKafkaListenerTest {

    @Test
    void handlesValidPaymentResultEventThroughUpdater() {
        PaymentResultEventParser parser = mock(PaymentResultEventParser.class);
        PaymentResultOrderUpdater updater = mock(PaymentResultOrderUpdater.class);
        PaymentResultKafkaListener listener = new PaymentResultKafkaListener(parser, updater);
        PaymentResultEnvelope event = succeededEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        when(parser.parse("payload")).thenReturn(event);
        when(updater.handle(event)).thenReturn(PaymentResultHandlingResult.processed(
                event,
                OrderStatus.PAYMENT_PENDING,
                OrderStatus.PAID,
                List.of(OrderStatus.PAID)));

        listener.handle("payload", event.orderId().toString(), "ecommerce.payment.events", 42L);

        verify(updater).handle(event);
    }

    @Test
    void rejectsMalformedPayloadWithoutCallingUpdaterAndDelegatesToErrorHandler() {
        PaymentResultEventParser parser = mock(PaymentResultEventParser.class);
        PaymentResultOrderUpdater updater = mock(PaymentResultOrderUpdater.class);
        PaymentResultKafkaListener listener = new PaymentResultKafkaListener(parser, updater);
        when(parser.parse("{bad-json")).thenThrow(PaymentResultEventException.of(
                PaymentResultEventFailure.MALFORMED_JSON,
                "Payment result event payload is not valid JSON."));

        assertThatThrownBy(() -> listener.handle("{bad-json", "order-key", "ecommerce.payment.events", 43L))
                .isInstanceOf(PaymentResultEventException.class);

        verifyNoInteractions(updater);
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
