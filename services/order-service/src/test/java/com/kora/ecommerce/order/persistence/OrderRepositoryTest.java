package com.kora.ecommerce.order.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-10-03T00:00:00Z");

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsOrderAggregateSnapshotsHistoryAndOutboxEvent() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        OrderEntity order = validOrder(orderId);
        order.addItem(OrderItemEntity.snapshot(
                UUID.randomUUID(),
                1,
                productId,
                "SKU-001",
                "Snapshot Jacket",
                2,
                new BigDecimal("12.5000"),
                "USD",
                new BigDecimal("25.0000")));
        order.appendStatusHistory(OrderStatus.CREATED, NOW, "order created");

        orderRepository.saveAndFlush(order);
        outboxEventRepository.saveAndFlush(OutboxEventEntity.orderEvent(
                UUID.randomUUID(),
                orderId,
                "OrderCreated",
                1,
                NOW,
                "trace-1",
                "correlation-1",
                """
                        {
                          "eventId": "11111111-1111-1111-1111-111111111111",
                          "eventType": "OrderCreated",
                          "eventVersion": 1,
                          "aggregateId": "%s",
                          "occurredAt": "2026-10-03T00:00:00Z",
                          "traceId": "trace-1",
                          "correlationId": "correlation-1",
                          "data": {"customerId": "customer-123"}
                        }
                        """.formatted(orderId)));
        entityManager.clear();

        OrderEntity saved = orderRepository.findById(orderId).orElseThrow();

        assertThat(saved.getCustomerId()).isEqualTo("customer-123");
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("25.0000");
        assertThat(saved.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getProductId()).isEqualTo(productId);
            assertThat(item.getProductSku()).isEqualTo("SKU-001");
            assertThat(item.getProductName()).isEqualTo("Snapshot Jacket");
            assertThat(item.getQuantity()).isEqualTo(2);
            assertThat(item.getLineTotalAmount()).isEqualByComparingTo("25.0000");
        });
        assertThat(saved.getStatusHistory()).singleElement().satisfies(history -> {
            assertThat(history.getStatus()).isEqualTo(OrderStatus.CREATED);
            assertThat(history.getChangedAt()).isEqualTo(NOW);
        });
        assertThat(outboxEventRepository.findAll()).singleElement().satisfies(event -> {
            assertThat(event.getAggregateId()).isEqualTo(orderId);
            assertThat(event.getEventType()).isEqualTo("OrderCreated");
            assertThat(event.getEventVersion()).isEqualTo(1);
            assertThat(event.getTraceId()).isEqualTo("trace-1");
            assertThat(event.getCorrelationId()).isEqualTo("correlation-1");
        });
    }

    @Test
    void findsCustomerOrdersWithDeterministicPagingAndBulkChildren() {
        UUID firstOrderId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondOrderId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID otherCustomerOrderId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        persistOrder(firstOrderId, "customer-123", NOW, "10.0000");
        persistOrder(secondOrderId, "customer-123", NOW, "20.0000");
        persistOrder(otherCustomerOrderId, "customer-999", NOW.plusSeconds(60), "30.0000");
        entityManager.clear();

        Page<OrderEntity> page = orderRepository.findByCustomerIdOrderByCreatedAtDescIdDesc(
                "customer-123",
                PageRequest.of(0, 1));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent())
                .extracting(OrderEntity::getId)
                .containsExactly(secondOrderId);

        assertThat(orderItemRepository.findForOrdersOrdered(page.getContent().stream()
                .map(OrderEntity::getId)
                .toList()))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getOrder().getId()).isEqualTo(secondOrderId);
                    assertThat(item.getItemNumber()).isEqualTo(1);
                    assertThat(item.getProductSku()).isEqualTo("SKU-0002");
                });
        assertThat(orderStatusHistoryRepository.findForOrdersOrdered(page.getContent().stream()
                .map(OrderEntity::getId)
                .toList()))
                .extracting(OrderStatusHistoryEntity::getStatus)
                .containsExactly(OrderStatus.CREATED);
    }

    @Test
    void rejectsDuplicateProductSnapshotsWithinOneOrder() {
        UUID productId = UUID.randomUUID();
        OrderEntity order = validOrder(UUID.randomUUID());
        order.addItem(validItem(1, productId));
        order.addItem(validItem(2, productId));

        assertConstraintViolation(() -> orderRepository.saveAndFlush(order));
    }

    @Test
    void rejectsOrderItemWithoutExistingOrder() {
        OrderEntity missingOrder = entityManager.getReference(OrderEntity.class, UUID.randomUUID());
        OrderItemEntity item = validItem(1, UUID.randomUUID());
        item.attachTo(missingOrder);

        assertConstraintViolation(() -> orderItemRepository.saveAndFlush(item));
    }

    @Test
    void rejectsNegativeOrderMoney() {
        OrderEntity order = OrderEntity.create(
                UUID.randomUUID(),
                "customer-123",
                new BigDecimal("-0.0100"),
                new BigDecimal("-0.0100"),
                "USD",
                NOW);

        assertConstraintViolation(() -> orderRepository.saveAndFlush(order));
    }

    @Test
    void rejectsLowercaseCurrency() {
        OrderEntity order = OrderEntity.create(
                UUID.randomUUID(),
                "customer-123",
                new BigDecimal("25.0000"),
                new BigDecimal("25.0000"),
                "usd",
                NOW);

        assertConstraintViolation(() -> orderRepository.saveAndFlush(order));
    }

    private static OrderEntity validOrder(UUID orderId) {
        return OrderEntity.create(
                orderId,
                "customer-123",
                new BigDecimal("25.0000"),
                new BigDecimal("25.0000"),
                "USD",
                NOW);
    }

    private void persistOrder(UUID orderId, String customerId, Instant createdAt, String totalAmount) {
        OrderEntity order = OrderEntity.create(
                orderId,
                customerId,
                new BigDecimal(totalAmount),
                new BigDecimal(totalAmount),
                "USD",
                createdAt);
        String suffix = orderId.toString().substring(orderId.toString().length() - 4);
        order.addItem(OrderItemEntity.snapshot(
                UUID.randomUUID(),
                1,
                UUID.randomUUID(),
                "SKU-" + suffix,
                "Snapshot Product " + suffix,
                1,
                new BigDecimal(totalAmount),
                "USD",
                new BigDecimal(totalAmount)));
        order.appendStatusHistory(OrderStatus.CREATED, createdAt, "order created");
        orderRepository.saveAndFlush(order);
    }

    private static OrderItemEntity validItem(int itemNumber, UUID productId) {
        return OrderItemEntity.snapshot(
                UUID.randomUUID(),
                itemNumber,
                productId,
                "SKU-" + itemNumber,
                "Snapshot Product " + itemNumber,
                1,
                new BigDecimal("25.0000"),
                "USD",
                new BigDecimal("25.0000"));
    }

    private static void assertConstraintViolation(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfAny(
                        DataIntegrityViolationException.class,
                        ConstraintViolationException.class,
                        PersistenceException.class);
    }
}
