package com.kora.ecommerce.order.application.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.kora.ecommerce.order.application.OrderStateMachine;
import com.kora.ecommerce.order.persistence.OrderEntity;
import com.kora.ecommerce.order.persistence.OrderItemEntity;
import com.kora.ecommerce.order.persistence.OrderItemRepository;
import com.kora.ecommerce.order.persistence.OrderRepository;
import com.kora.ecommerce.order.persistence.OrderStatus;
import com.kora.ecommerce.order.persistence.OrderStatusHistoryEntity;
import com.kora.ecommerce.order.persistence.OrderStatusHistoryRepository;
import com.kora.ecommerce.order.persistence.ProcessedPaymentEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        PaymentResultOrderUpdater.class,
        OrderStateMachine.class,
        PaymentResultOrderUpdaterTest.PaymentResultOrderUpdaterTestConfiguration.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PaymentResultOrderUpdaterTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-03T12:00:00Z");
    private static final Instant PAYMENT_PENDING_AT = Instant.parse("2026-10-03T12:01:00Z");
    private static final Instant NOW = Instant.parse("2026-10-04T10:15:30Z");

    @Autowired
    private PaymentResultOrderUpdater paymentResultOrderUpdater;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Autowired
    private ProcessedPaymentEventRepository processedPaymentEventRepository;

    @BeforeEach
    void setUp() {
        processedPaymentEventRepository.deleteAllInBatch();
        orderStatusHistoryRepository.deleteAllInBatch();
        orderItemRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
    }

    @Test
    void appliesSucceededResultToPaymentPendingOrderExactlyOnce() {
        UUID orderId = persistOrder(OrderStatus.PAYMENT_PENDING);
        PaymentResultEnvelope event = succeededEvent(UUID.randomUUID(), orderId, UUID.randomUUID());

        PaymentResultHandlingResult result = paymentResultOrderUpdater.handle(event);

        assertThat(result.status()).isEqualTo(PaymentResultHandlingStatus.PROCESSED);
        assertThat(result.previousStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(result.currentStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(result.appliedTransitions()).containsExactly(OrderStatus.PAID);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(statusHistory(orderId))
                .extracting(OrderStatusHistoryEntity::getStatus)
                .containsExactly(OrderStatus.CREATED, OrderStatus.PAYMENT_PENDING, OrderStatus.PAID);
        assertThat(statusHistory(orderId).get(2)).satisfies(history -> {
            assertThat(history.getChangedAt()).isEqualTo(NOW);
            assertThat(history.getReason()).isEqualTo("payment succeeded: paymentId=" + event.paymentId());
        });
        assertThat(processedPaymentEventRepository.count()).isEqualTo(1);

        PaymentResultHandlingResult duplicate = paymentResultOrderUpdater.handle(event);

        assertThat(duplicate.status()).isEqualTo(PaymentResultHandlingStatus.DUPLICATE);
        assertThat(processedPaymentEventRepository.count()).isEqualTo(1);
        assertThat(statusHistory(orderId))
                .extracting(OrderStatusHistoryEntity::getStatus)
                .containsExactly(OrderStatus.CREATED, OrderStatus.PAYMENT_PENDING, OrderStatus.PAID);
    }

    @Test
    void appliesFailedResultToPaymentPendingOrder() {
        UUID orderId = persistOrder(OrderStatus.PAYMENT_PENDING);
        PaymentResultEnvelope event = failedEvent(UUID.randomUUID(), orderId, UUID.randomUUID());

        PaymentResultHandlingResult result = paymentResultOrderUpdater.handle(event);

        assertThat(result.status()).isEqualTo(PaymentResultHandlingStatus.PROCESSED);
        assertThat(result.currentStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(result.appliedTransitions()).containsExactly(OrderStatus.PAYMENT_FAILED);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(statusHistory(orderId))
                .extracting(OrderStatusHistoryEntity::getStatus)
                .containsExactly(OrderStatus.CREATED, OrderStatus.PAYMENT_PENDING, OrderStatus.PAYMENT_FAILED);
        assertThat(processedPaymentEventRepository.count()).isEqualTo(1);
    }

    @Test
    void movesCreatedOrderThroughPaymentPendingBeforeTerminalResult() {
        UUID orderId = persistOrder(OrderStatus.CREATED);
        PaymentResultEnvelope event = succeededEvent(UUID.randomUUID(), orderId, UUID.randomUUID());

        PaymentResultHandlingResult result = paymentResultOrderUpdater.handle(event);

        assertThat(result.status()).isEqualTo(PaymentResultHandlingStatus.PROCESSED);
        assertThat(result.previousStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(result.currentStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(result.appliedTransitions()).containsExactly(OrderStatus.PAYMENT_PENDING, OrderStatus.PAID);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(statusHistory(orderId))
                .extracting(OrderStatusHistoryEntity::getStatus)
                .containsExactly(OrderStatus.CREATED, OrderStatus.PAYMENT_PENDING, OrderStatus.PAID);
        assertThat(statusHistory(orderId).get(1).getChangedAt()).isEqualTo(NOW);
        assertThat(statusHistory(orderId).get(2).getChangedAt()).isEqualTo(NOW.plusMillis(1));
        assertThat(processedPaymentEventRepository.count()).isEqualTo(1);
    }

    @Test
    void marksMissingOrderPaymentResultAsSkippedWithoutPersistenceLeak() {
        PaymentResultEnvelope event = failedEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        PaymentResultHandlingResult result = paymentResultOrderUpdater.handle(event);

        assertThat(result.status()).isEqualTo(PaymentResultHandlingStatus.MISSING_ORDER);
        assertThat(result.orderId()).isEqualTo(event.orderId());
        assertThat(orderRepository.findAll()).isEmpty();
        assertThat(orderStatusHistoryRepository.findAll()).isEmpty();
        assertThat(processedPaymentEventRepository.count()).isEqualTo(1);
    }

    @Test
    void marksInvalidTransitionAsSkippedWithoutAppendingHistory() {
        UUID orderId = persistOrder(OrderStatus.CANCELLED);
        PaymentResultEnvelope event = succeededEvent(UUID.randomUUID(), orderId, UUID.randomUUID());

        PaymentResultHandlingResult result = paymentResultOrderUpdater.handle(event);

        assertThat(result.status()).isEqualTo(PaymentResultHandlingStatus.INVALID_TRANSITION);
        assertThat(result.previousStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(result.currentStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(statusHistory(orderId))
                .extracting(OrderStatusHistoryEntity::getStatus)
                .containsExactly(OrderStatus.CREATED, OrderStatus.CANCELLED);
        assertThat(processedPaymentEventRepository.count()).isEqualTo(1);
    }

    private UUID persistOrder(OrderStatus status) {
        UUID orderId = UUID.randomUUID();
        OrderEntity order = OrderEntity.create(
                orderId,
                "customer-123",
                new BigDecimal("25.0000"),
                new BigDecimal("25.0000"),
                "USD",
                CREATED_AT);
        order.addItem(OrderItemEntity.snapshot(
                UUID.randomUUID(),
                1,
                UUID.randomUUID(),
                "SKU-001",
                "Snapshot Product",
                1,
                new BigDecimal("25.0000"),
                "USD",
                new BigDecimal("25.0000")));
        order.appendStatusHistory(OrderStatus.CREATED, CREATED_AT, "order created");
        if (status == OrderStatus.PAYMENT_PENDING) {
            order.transitionTo(OrderStatus.PAYMENT_PENDING, PAYMENT_PENDING_AT, "send to payment");
        } else if (status == OrderStatus.CANCELLED) {
            order.transitionTo(OrderStatus.CANCELLED, PAYMENT_PENDING_AT, "customer cancelled");
        }
        return orderRepository.saveAndFlush(order).getId();
    }

    private List<OrderStatusHistoryEntity> statusHistory(UUID orderId) {
        return orderStatusHistoryRepository.findByOrder_IdOrderByChangedAtAsc(orderId);
    }

    private static PaymentResultEnvelope succeededEvent(UUID eventId, UUID orderId, UUID paymentId) {
        return new PaymentSucceededEnvelope(
                eventId,
                1,
                orderId,
                NOW,
                "trace-123",
                "correlation-123",
                new PaymentResultData(orderId, paymentId));
    }

    private static PaymentResultEnvelope failedEvent(UUID eventId, UUID orderId, UUID paymentId) {
        return new PaymentFailedEnvelope(
                eventId,
                1,
                orderId,
                NOW,
                "trace-123",
                "correlation-123",
                new PaymentResultData(orderId, paymentId));
    }

    @TestConfiguration
    static class PaymentResultOrderUpdaterTestConfiguration {

        @Bean
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
