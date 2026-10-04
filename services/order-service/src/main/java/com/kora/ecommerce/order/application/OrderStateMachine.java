package com.kora.ecommerce.order.application;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import com.kora.ecommerce.order.persistence.OrderStatus;
import org.springframework.stereotype.Component;

@Component
public class OrderStateMachine {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = allowedTransitions();

    public boolean canTransition(OrderStatus currentStatus, OrderStatus targetStatus) {
        if (currentStatus == null || targetStatus == null) {
            return false;
        }
        return ALLOWED_TRANSITIONS.getOrDefault(currentStatus, Set.of()).contains(targetStatus);
    }

    public Set<OrderStatus> allowedTransitionsFrom(OrderStatus currentStatus) {
        if (currentStatus == null) {
            return Set.of();
        }
        return ALLOWED_TRANSITIONS.getOrDefault(currentStatus, Set.of());
    }

    private static Map<OrderStatus, Set<OrderStatus>> allowedTransitions() {
        EnumMap<OrderStatus, Set<OrderStatus>> transitions = new EnumMap<>(OrderStatus.class);
        transitions.put(OrderStatus.CREATED, EnumSet.of(
                OrderStatus.PAYMENT_PENDING,
                OrderStatus.CANCELLED));
        transitions.put(OrderStatus.PAYMENT_PENDING, EnumSet.of(
                OrderStatus.PAID,
                OrderStatus.PAYMENT_FAILED,
                OrderStatus.CANCELLED));
        transitions.put(OrderStatus.PAYMENT_FAILED, EnumSet.of(
                OrderStatus.PAYMENT_PENDING,
                OrderStatus.CANCELLED));
        transitions.put(OrderStatus.PAID, EnumSet.noneOf(OrderStatus.class));
        transitions.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
        transitions.replaceAll((status, allowed) -> Set.copyOf(allowed));
        return Map.copyOf(transitions);
    }
}
