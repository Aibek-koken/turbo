package com.kora.ecommerce.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.kora.ecommerce.order.persistence.OrderEntity;
import com.kora.ecommerce.order.persistence.OrderItemEntity;
import com.kora.ecommerce.order.persistence.OrderItemRepository;
import com.kora.ecommerce.order.persistence.OrderRepository;
import com.kora.ecommerce.order.persistence.OrderStatus;
import com.kora.ecommerce.order.persistence.OrderStatusHistoryEntity;
import com.kora.ecommerce.order.persistence.OrderStatusHistoryRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.RollbackException;
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
        OrderTransitionService.class,
        OrderStateMachine.class,
        OrderTransitionServiceTest.OrderTransitionServiceTestConfiguration.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrderTransitionServiceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-03T12:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-03T15:00:00Z");

    @Autowired
    private OrderTransitionService orderTransitionService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void setUp() {
        orderStatusHistoryRepository.deleteAllInBatch();
        orderItemRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
    }

    @Test
    void transitionsOrderAndAppendsTimestampedHistoryInOneTransaction() {
        UUID orderId = persistCreatedOrder();

        TransitionedOrder transitioned = orderTransitionService.transition(new OrderTransitionCommand(
                orderId,
                OrderStatus.PAYMENT_PENDING,
                " send to payment "));

        assertThat(transitioned.orderId()).isEqualTo(orderId);
        assertThat(transitioned.previousStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(transitioned.currentStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(transitioned.changedAt()).isEqualTo(NOW);
        assertThat(transitioned.reason()).isEqualTo("send to payment");
        assertThat(transitioned.version()).isGreaterThan(0);

        OrderEntity saved = orderRepository.findById(orderId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(saved.getUpdatedAt()).isEqualTo(NOW);

        assertThat(statusHistory(orderId))
                .extracting(OrderStatusHistoryEntity::getStatus)
                .containsExactly(OrderStatus.CREATED, OrderStatus.PAYMENT_PENDING);
        assertThat(statusHistory(orderId).get(1)).satisfies(history -> {
            assertThat(history.getChangedAt()).isEqualTo(NOW);
            assertThat(history.getReason()).isEqualTo("send to payment");
        });
    }

    @Test
    void rejectsNoOpTransitionWithoutChangingOrderOrHistory() {
        UUID orderId = persistCreatedOrder();

        assertTransitionFailure(
                new OrderTransitionCommand(orderId, OrderStatus.CREATED, "noop"),
                OrderTransitionFailure.NO_OP_TRANSITION);

        assertOrderStateAndHistory(orderId, OrderStatus.CREATED, List.of(OrderStatus.CREATED));
    }

    @Test
    void rejectsInvalidTransitionWithoutChangingOrderOrHistory() {
        UUID orderId = persistCreatedOrder();

        assertTransitionFailure(
                new OrderTransitionCommand(orderId, OrderStatus.PAID, "skip payment"),
                OrderTransitionFailure.INVALID_TRANSITION);

        assertOrderStateAndHistory(orderId, OrderStatus.CREATED, List.of(OrderStatus.CREATED));
    }

    @Test
    void rejectsUnknownOrderWithoutPersistingHistory() {
        UUID missingOrderId = UUID.randomUUID();

        assertTransitionFailure(
                new OrderTransitionCommand(missingOrderId, OrderStatus.PAYMENT_PENDING, "missing"),
                OrderTransitionFailure.ORDER_NOT_FOUND);

        assertThat(orderStatusHistoryRepository.findAll()).isEmpty();
    }

    @Test
    void optimisticLockingPreventsConcurrentTransitionsFromSilentlyOverwritingEachOther() {
        UUID orderId = persistCreatedOrder();
        EntityManager firstEntityManager = entityManagerFactory.createEntityManager();
        EntityManager secondEntityManager = entityManagerFactory.createEntityManager();

        try {
            firstEntityManager.getTransaction().begin();
            secondEntityManager.getTransaction().begin();

            OrderEntity firstCopy = firstEntityManager.find(OrderEntity.class, orderId);
            OrderEntity secondCopy = secondEntityManager.find(OrderEntity.class, orderId);

            firstCopy.transitionTo(OrderStatus.PAYMENT_PENDING, NOW, "first transition");
            firstEntityManager.flush();
            firstEntityManager.getTransaction().commit();

            secondCopy.transitionTo(OrderStatus.CANCELLED, NOW.plusSeconds(1), "stale transition");
            assertThatThrownBy(() -> {
                secondEntityManager.flush();
                secondEntityManager.getTransaction().commit();
            }).isInstanceOfAny(
                    OptimisticLockException.class,
                    RollbackException.class,
                    PersistenceException.class);

            if (secondEntityManager.getTransaction().isActive()) {
                secondEntityManager.getTransaction().rollback();
            }
        } finally {
            if (firstEntityManager.getTransaction().isActive()) {
                firstEntityManager.getTransaction().rollback();
            }
            if (secondEntityManager.getTransaction().isActive()) {
                secondEntityManager.getTransaction().rollback();
            }
            firstEntityManager.close();
            secondEntityManager.close();
        }

        assertOrderStateAndHistory(
                orderId,
                OrderStatus.PAYMENT_PENDING,
                List.of(OrderStatus.CREATED, OrderStatus.PAYMENT_PENDING));
    }

    private UUID persistCreatedOrder() {
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
        return orderRepository.saveAndFlush(order).getId();
    }

    private void assertTransitionFailure(OrderTransitionCommand command, OrderTransitionFailure failure) {
        assertThatThrownBy(() -> orderTransitionService.transition(command))
                .isInstanceOfSatisfying(OrderTransitionException.class, exception ->
                        assertThat(exception.failure()).isEqualTo(failure));
    }

    private void assertOrderStateAndHistory(
            UUID orderId,
            OrderStatus expectedStatus,
            List<OrderStatus> expectedHistory) {
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(expectedStatus);
        assertThat(statusHistory(orderId))
                .extracting(OrderStatusHistoryEntity::getStatus)
                .containsExactlyElementsOf(expectedHistory);
    }

    private List<OrderStatusHistoryEntity> statusHistory(UUID orderId) {
        return orderStatusHistoryRepository.findByOrder_IdOrderByChangedAtAsc(orderId);
    }

    @TestConfiguration
    static class OrderTransitionServiceTestConfiguration {

        @Bean
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
