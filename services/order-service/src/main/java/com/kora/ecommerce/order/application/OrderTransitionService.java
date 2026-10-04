package com.kora.ecommerce.order.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.kora.ecommerce.order.persistence.OrderEntity;
import com.kora.ecommerce.order.persistence.OrderRepository;
import com.kora.ecommerce.order.persistence.OrderStatus;
import jakarta.persistence.OptimisticLockException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class OrderTransitionService {

    public static final int MAX_REASON_LENGTH = 500;

    private final OrderRepository orderRepository;
    private final OrderStateMachine orderStateMachine;
    private final Clock clock;

    OrderTransitionService(
            OrderRepository orderRepository,
            OrderStateMachine orderStateMachine,
            Clock clock) {
        this.orderRepository = orderRepository;
        this.orderStateMachine = orderStateMachine;
        this.clock = clock;
    }

    @Transactional
    public TransitionedOrder transition(OrderTransitionCommand command) {
        if (command == null || command.orderId() == null) {
            throw OrderTransitionException.orderIdRequired();
        }
        if (command.targetStatus() == null) {
            throw OrderTransitionException.targetStatusRequired(command.orderId());
        }

        OrderEntity order = orderRepository.findById(command.orderId())
                .orElseThrow(() -> OrderTransitionException.orderNotFound(command.orderId()));
        OrderStatus previousStatus = order.getStatus();
        OrderStatus targetStatus = command.targetStatus();

        if (previousStatus == targetStatus) {
            throw OrderTransitionException.noOp(order.getId(), previousStatus);
        }
        if (!orderStateMachine.canTransition(previousStatus, targetStatus)) {
            throw OrderTransitionException.invalid(order.getId(), previousStatus, targetStatus);
        }

        String reason = normalizeReason(command.orderId(), command.reason());
        Instant changedAt = Instant.now(clock);
        order.transitionTo(targetStatus, changedAt, reason);

        try {
            OrderEntity saved = orderRepository.saveAndFlush(order);
            return new TransitionedOrder(
                    saved.getId(),
                    previousStatus,
                    saved.getStatus(),
                    changedAt,
                    reason,
                    saved.getVersion());
        } catch (ObjectOptimisticLockingFailureException | OptimisticLockException exception) {
            throw OrderTransitionException.concurrent(command.orderId(), exception);
        }
    }

    private static String normalizeReason(UUID orderId, String reason) {
        if (!StringUtils.hasText(reason)) {
            return null;
        }
        String normalizedReason = reason.trim();
        if (normalizedReason.length() > MAX_REASON_LENGTH) {
            throw OrderTransitionException.reasonTooLong(orderId, MAX_REASON_LENGTH);
        }
        return normalizedReason;
    }
}
