package com.kora.ecommerce.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.kora.ecommerce.order.persistence.OrderEntity;
import com.kora.ecommerce.order.persistence.OrderItemEntity;
import com.kora.ecommerce.order.persistence.OrderItemRepository;
import com.kora.ecommerce.order.persistence.OrderRepository;
import com.kora.ecommerce.order.persistence.OrderStatus;
import com.kora.ecommerce.order.persistence.OrderStatusHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(OrderQueryService.class)
class OrderQueryServiceTest {

    private static final Instant FIRST_CREATED_AT = Instant.parse("2026-10-03T12:00:00Z");
    private static final Instant SECOND_CREATED_AT = Instant.parse("2026-10-03T13:00:00Z");

    @Autowired
    private OrderQueryService orderQueryService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @BeforeEach
    void setUp() {
        orderStatusHistoryRepository.deleteAllInBatch();
        orderItemRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
    }

    @Test
    void listsOnlyOwnedCustomerOrdersWithBoundedPaginationAndHydratedHistory() {
        UUID olderOwnedOrderId = persistOrder(
                UUID.randomUUID(),
                "customer-123",
                FIRST_CREATED_AT,
                "10.0000",
                List.of(history(OrderStatus.CREATED, FIRST_CREATED_AT, "order created")));
        UUID newerOwnedOrderId = persistOrder(
                UUID.randomUUID(),
                "customer-123",
                SECOND_CREATED_AT,
                "20.0000",
                List.of(
                        history(OrderStatus.CREATED, SECOND_CREATED_AT, "order created"),
                        history(OrderStatus.PAYMENT_PENDING, SECOND_CREATED_AT.plusSeconds(30), "send to payment")));
        persistOrder(
                UUID.randomUUID(),
                "customer-999",
                SECOND_CREATED_AT.plusSeconds(60),
                "30.0000",
                List.of(history(OrderStatus.CREATED, SECOND_CREATED_AT.plusSeconds(60), "order created")));

        QueriedOrderPage page = orderQueryService.listCustomerOrders(" customer-123 ", 0, 1);

        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(1);
        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(page.hasNext()).isTrue();
        assertThat(page.orders())
                .extracting(QueriedOrder::orderId)
                .containsExactly(newerOwnedOrderId);
        assertThat(page.orders().getFirst().customerId()).isEqualTo("customer-123");
        assertThat(page.orders().getFirst().items())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.productSku()).startsWith("SKU-");
                    assertThat(item.lineTotalAmount()).isEqualByComparingTo("20.0000");
                });
        assertThat(page.orders().getFirst().statusHistory())
                .extracting(QueriedOrder.StatusHistory::status)
                .containsExactly(OrderStatus.CREATED, OrderStatus.PAYMENT_PENDING);
        assertThat(orderQueryService.listCustomerOrders("customer-123", 1, 1).orders())
                .extracting(QueriedOrder::orderId)
                .containsExactly(olderOwnedOrderId);
    }

    @Test
    void returnsCustomerDetailOnlyForOwnedOrder() {
        UUID ownedOrderId = persistOrder(
                UUID.randomUUID(),
                "customer-123",
                FIRST_CREATED_AT,
                "10.0000",
                List.of(history(OrderStatus.CREATED, FIRST_CREATED_AT, "order created")));
        UUID otherOrderId = persistOrder(
                UUID.randomUUID(),
                "customer-999",
                SECOND_CREATED_AT,
                "20.0000",
                List.of(history(OrderStatus.CREATED, SECOND_CREATED_AT, "order created")));

        QueriedOrder ownedOrder = orderQueryService.getCustomerOrder("customer-123", ownedOrderId);

        assertThat(ownedOrder.orderId()).isEqualTo(ownedOrderId);
        assertThat(ownedOrder.customerId()).isEqualTo("customer-123");
        assertQueryFailure(
                () -> orderQueryService.getCustomerOrder("customer-123", otherOrderId),
                OrderQueryFailure.ORDER_NOT_FOUND,
                HttpStatus.NOT_FOUND);
    }

    @Test
    void operationsLookupCanReadAnyOrderById() {
        UUID otherCustomerOrderId = persistOrder(
                UUID.randomUUID(),
                "customer-999",
                FIRST_CREATED_AT,
                "10.0000",
                List.of(history(OrderStatus.CREATED, FIRST_CREATED_AT, "order created")));

        QueriedOrder order = orderQueryService.getOrderForOperations(otherCustomerOrderId);

        assertThat(order.orderId()).isEqualTo(otherCustomerOrderId);
        assertThat(order.customerId()).isEqualTo("customer-999");
    }

    @Test
    void rejectsInvalidPaginationBounds() {
        assertQueryFailure(
                () -> orderQueryService.listCustomerOrders("customer-123", -1, 10),
                OrderQueryFailure.INVALID_PAGE_NUMBER,
                HttpStatus.BAD_REQUEST);
        assertQueryFailure(
                () -> orderQueryService.listCustomerOrders("customer-123", 0, 0),
                OrderQueryFailure.INVALID_PAGE_SIZE,
                HttpStatus.BAD_REQUEST);
        assertThatThrownBy(() -> orderQueryService.listCustomerOrders("customer-123", 0, 51))
                .isInstanceOfSatisfying(OrderQueryException.class, exception -> {
                    assertThat(exception.failure()).isEqualTo(OrderQueryFailure.PAGE_SIZE_TOO_LARGE);
                    assertThat(exception.apiStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.maxAllowed()).isEqualTo(50);
                });
    }

    private UUID persistOrder(
            UUID orderId,
            String customerId,
            Instant createdAt,
            String totalAmount,
            List<HistorySeed> historySeeds) {
        OrderEntity order = OrderEntity.create(
                orderId,
                customerId,
                new BigDecimal(totalAmount),
                new BigDecimal(totalAmount),
                "USD",
                createdAt);
        String suffix = orderId.toString().substring(0, 8);
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
        for (HistorySeed history : historySeeds) {
            if (history.status() == OrderStatus.CREATED) {
                order.appendStatusHistory(history.status(), history.changedAt(), history.reason());
            } else {
                order.transitionTo(history.status(), history.changedAt(), history.reason());
            }
        }
        return orderRepository.saveAndFlush(order).getId();
    }

    private static void assertQueryFailure(
            Runnable action,
            OrderQueryFailure failure,
            HttpStatus apiStatus) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(OrderQueryException.class, exception -> {
                    assertThat(exception.failure()).isEqualTo(failure);
                    assertThat(exception.apiStatus()).isEqualTo(apiStatus);
                });
    }

    private static HistorySeed history(OrderStatus status, Instant changedAt, String reason) {
        return new HistorySeed(status, changedAt, reason);
    }

    private record HistorySeed(OrderStatus status, Instant changedAt, String reason) {
    }
}
