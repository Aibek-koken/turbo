package com.kora.ecommerce.order.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;

import com.kora.ecommerce.order.persistence.OrderStatus;
import org.junit.jupiter.api.Test;

class OrderStateMachineTest {

    private final OrderStateMachine stateMachine = new OrderStateMachine();

    @Test
    void exposesExhaustiveV1TransitionMatrix() {
        Map<OrderStatus, Set<OrderStatus>> expectedTransitions = Map.of(
                OrderStatus.CREATED, Set.of(OrderStatus.PAYMENT_PENDING, OrderStatus.CANCELLED),
                OrderStatus.PAYMENT_PENDING, Set.of(
                        OrderStatus.PAID,
                        OrderStatus.PAYMENT_FAILED,
                        OrderStatus.CANCELLED),
                OrderStatus.PAYMENT_FAILED, Set.of(OrderStatus.PAYMENT_PENDING, OrderStatus.CANCELLED),
                OrderStatus.PAID, Set.of(),
                OrderStatus.CANCELLED, Set.of());

        for (OrderStatus currentStatus : OrderStatus.values()) {
            assertThat(stateMachine.allowedTransitionsFrom(currentStatus))
                    .containsExactlyInAnyOrderElementsOf(expectedTransitions.get(currentStatus));

            for (OrderStatus targetStatus : OrderStatus.values()) {
                assertThat(stateMachine.canTransition(currentStatus, targetStatus))
                        .as("%s -> %s", currentStatus, targetStatus)
                        .isEqualTo(expectedTransitions.get(currentStatus).contains(targetStatus));
            }
        }
    }

    @Test
    void rejectsNullStatuses() {
        assertThat(stateMachine.canTransition(null, OrderStatus.CREATED)).isFalse();
        assertThat(stateMachine.canTransition(OrderStatus.CREATED, null)).isFalse();
        assertThat(stateMachine.allowedTransitionsFrom(null)).isEmpty();
    }
}
