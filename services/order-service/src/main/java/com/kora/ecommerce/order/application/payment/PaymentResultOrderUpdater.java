package com.kora.ecommerce.order.application.payment;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.kora.ecommerce.order.application.OrderStateMachine;
import com.kora.ecommerce.order.persistence.OrderEntity;
import com.kora.ecommerce.order.persistence.OrderRepository;
import com.kora.ecommerce.order.persistence.OrderStatus;
import com.kora.ecommerce.order.persistence.ProcessedPaymentEventEntity;
import com.kora.ecommerce.order.persistence.ProcessedPaymentEventId;
import com.kora.ecommerce.order.persistence.ProcessedPaymentEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentResultOrderUpdater {

    private final OrderRepository orderRepository;
    private final ProcessedPaymentEventRepository processedPaymentEventRepository;
    private final OrderStateMachine orderStateMachine;
    private final Clock clock;
    private final String consumerName;

    PaymentResultOrderUpdater(
            OrderRepository orderRepository,
            ProcessedPaymentEventRepository processedPaymentEventRepository,
            OrderStateMachine orderStateMachine,
            Clock clock,
            @Value("${order.payment-results.group-id:order-service.payment-results.v1}") String consumerName) {
        this.orderRepository = orderRepository;
        this.processedPaymentEventRepository = processedPaymentEventRepository;
        this.orderStateMachine = orderStateMachine;
        this.clock = clock;
        this.consumerName = requireText(consumerName, "consumerName");
    }

    @Transactional
    public PaymentResultHandlingResult handle(PaymentResultEnvelope event) {
        PaymentResultEnvelope requiredEvent = Objects.requireNonNull(event, "event is required");
        ProcessedPaymentEventId processedEventId = ProcessedPaymentEventId.of(
                consumerName,
                requiredEvent.eventId());
        if (processedPaymentEventRepository.existsById(processedEventId)) {
            return PaymentResultHandlingResult.duplicate(requiredEvent);
        }

        Instant processedAt = Instant.now(clock);
        processedPaymentEventRepository.save(ProcessedPaymentEventEntity.record(
                consumerName,
                requiredEvent.eventId(),
                requiredEvent.eventType(),
                requiredEvent.orderId(),
                requiredEvent.paymentId(),
                processedAt));

        return orderRepository.findById(requiredEvent.orderId())
                .map(order -> applyResult(requiredEvent, order, processedAt))
                .orElseGet(() -> PaymentResultHandlingResult.missingOrder(requiredEvent));
    }

    private PaymentResultHandlingResult applyResult(
            PaymentResultEnvelope event,
            OrderEntity order,
            Instant firstChangedAt) {
        OrderStatus previousStatus = order.getStatus();
        OrderStatus targetStatus = event.targetStatus();

        if (previousStatus == targetStatus) {
            return PaymentResultHandlingResult.alreadyTerminal(event, previousStatus);
        }

        List<OrderStatus> appliedTransitions = new ArrayList<>();
        Instant terminalChangedAt = firstChangedAt;
        OrderStatus transitionBase = previousStatus;

        if (previousStatus == OrderStatus.CREATED) {
            if (!orderStateMachine.canTransition(OrderStatus.CREATED, OrderStatus.PAYMENT_PENDING)) {
                return PaymentResultHandlingResult.invalidTransition(event, previousStatus, targetStatus);
            }
            order.transitionTo(
                    OrderStatus.PAYMENT_PENDING,
                    firstChangedAt,
                    pendingReason(event));
            appliedTransitions.add(OrderStatus.PAYMENT_PENDING);
            transitionBase = OrderStatus.PAYMENT_PENDING;
            terminalChangedAt = firstChangedAt.plusMillis(1);
        }

        if (!orderStateMachine.canTransition(transitionBase, targetStatus)) {
            return PaymentResultHandlingResult.invalidTransition(event, previousStatus, targetStatus);
        }

        order.transitionTo(targetStatus, terminalChangedAt, terminalReason(event));
        appliedTransitions.add(targetStatus);
        OrderEntity saved = orderRepository.saveAndFlush(order);

        return PaymentResultHandlingResult.processed(
                event,
                previousStatus,
                saved.getStatus(),
                appliedTransitions);
    }

    private static String pendingReason(PaymentResultEnvelope event) {
        return "payment result received before pending: paymentId=" + event.paymentId();
    }

    private static String terminalReason(PaymentResultEnvelope event) {
        if (event instanceof PaymentSucceededEnvelope) {
            return "payment succeeded: paymentId=" + event.paymentId();
        }
        return "payment failed: paymentId=" + event.paymentId();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}
