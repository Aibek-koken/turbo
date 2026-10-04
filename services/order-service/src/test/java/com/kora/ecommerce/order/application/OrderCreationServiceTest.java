package com.kora.ecommerce.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.kora.ecommerce.order.catalog.CatalogProductClient;
import com.kora.ecommerce.order.catalog.ProductSnapshot;
import com.kora.ecommerce.order.catalog.ProductSnapshotFailure;
import com.kora.ecommerce.order.catalog.ProductSnapshotResolutionException;
import com.kora.ecommerce.order.observability.CorrelationIdFilter;
import com.kora.ecommerce.order.persistence.OrderEntity;
import com.kora.ecommerce.order.persistence.OrderItemRepository;
import com.kora.ecommerce.order.persistence.OrderRepository;
import com.kora.ecommerce.order.persistence.OrderStatus;
import com.kora.ecommerce.order.persistence.OrderStatusHistoryRepository;
import com.kora.ecommerce.order.persistence.OutboxEventRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        OrderCreationService.class,
        OrderCreationPersistence.class,
        OrderCreatedOutboxEventFactory.class,
        OrderCreationServiceTest.OrderCreationServiceTestConfiguration.class
})
class OrderCreationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-03T13:00:00Z");
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Autowired
    private OrderCreationService orderCreationService;

    @Autowired
    private OrderCreationPersistence orderCreationPersistence;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private TestCatalogProductClient catalogProductClient;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAllInBatch();
        orderStatusHistoryRepository.deleteAllInBatch();
        orderItemRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        catalogProductClient.reset();
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void createsOrderWithCatalogSnapshotsTotalsAndJwtCustomerOwnership() {
        UUID firstProductId = UUID.randomUUID();
        UUID secondProductId = UUID.randomUUID();
        catalogProductClient.addSnapshot(snapshot(
                firstProductId,
                "SKU-001",
                "Snapshot Jacket",
                "12.50",
                "USD"));
        catalogProductClient.addSnapshot(snapshot(
                secondProductId,
                "SKU-002",
                "Snapshot Hat",
                "2.2500",
                "USD"));

        MDC.put("traceId", "trace-order-020");
        MDC.put(CorrelationIdFilter.CORRELATION_ID_MDC_KEY, "correlation-order-020");

        CreatedOrder created = orderCreationService.createOrder(new OrderCreationCommand(
                "jwt-customer-123",
                List.of(
                        new OrderCreationItemCommand(firstProductId, 2),
                        new OrderCreationItemCommand(secondProductId, 3))));

        assertThat(created.customerId()).isEqualTo("jwt-customer-123");
        assertThat(created.status()).isEqualTo(OrderStatus.CREATED);
        assertThat(created.subtotalAmount()).isEqualByComparingTo("31.7500");
        assertThat(created.totalAmount()).isEqualByComparingTo("31.7500");
        assertThat(created.currency()).isEqualTo("USD");
        assertThat(created.createdAt()).isEqualTo(NOW);
        assertThat(created.items()).hasSize(2);
        assertThat(created.items().get(0)).satisfies(item -> {
            assertThat(item.itemNumber()).isEqualTo(1);
            assertThat(item.productId()).isEqualTo(firstProductId);
            assertThat(item.productSku()).isEqualTo("SKU-001");
            assertThat(item.productName()).isEqualTo("Snapshot Jacket");
            assertThat(item.quantity()).isEqualTo(2);
            assertThat(item.unitPriceAmount()).isEqualByComparingTo("12.5000");
            assertThat(item.lineTotalAmount()).isEqualByComparingTo("25.0000");
        });
        assertThat(catalogProductClient.resolvedProductIds()).containsExactly(firstProductId, secondProductId);

        entityManager.clear();
        OrderEntity saved = orderRepository.findById(created.orderId()).orElseThrow();
        assertThat(saved.getCustomerId()).isEqualTo("jwt-customer-123");
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("31.7500");
        assertThat(orderItemRepository.findByOrder_IdOrderByItemNumberAsc(created.orderId())).hasSize(2);
        assertThat(orderItemRepository.findByOrder_IdOrderByItemNumberAsc(created.orderId()).get(0)).satisfies(item -> {
            assertThat(item.getProductSku()).isEqualTo("SKU-001");
            assertThat(item.getProductName()).isEqualTo("Snapshot Jacket");
            assertThat(item.getUnitPriceAmount()).isEqualByComparingTo("12.5000");
            assertThat(item.getLineTotalAmount()).isEqualByComparingTo("25.0000");
        });
        assertThat(orderStatusHistoryRepository.findByOrder_IdOrderByChangedAtAsc(created.orderId()))
                .singleElement()
                .satisfies(history -> {
            assertThat(history.getStatus()).isEqualTo(OrderStatus.CREATED);
            assertThat(history.getChangedAt()).isEqualTo(NOW);
            assertThat(history.getReason()).isEqualTo("order created");
        });
        assertThat(outboxEventRepository.findAll()).singleElement().satisfies(event -> {
            assertThat(event.getAggregateId()).isEqualTo(created.orderId());
            assertThat(event.getAggregateType()).isEqualTo("Order");
            assertThat(event.getEventType()).isEqualTo(OrderCreatedOutboxEventFactory.EVENT_TYPE);
            assertThat(event.getEventVersion()).isEqualTo(OrderCreatedOutboxEventFactory.EVENT_VERSION);
            assertThat(event.getOccurredAt()).isEqualTo(NOW);
            assertThat(event.getTraceId()).isEqualTo("trace-order-020");
            assertThat(event.getCorrelationId()).isEqualTo("correlation-order-020");

            JsonNode payload = readPayload(event.getPayload());
            assertThat(payload.path("eventId").asText()).isEqualTo(event.getId().toString());
            assertThat(payload.path("eventType").asText()).isEqualTo(OrderCreatedOutboxEventFactory.EVENT_TYPE);
            assertThat(payload.path("eventVersion").asInt()).isEqualTo(OrderCreatedOutboxEventFactory.EVENT_VERSION);
            assertThat(payload.path("aggregateId").asText()).isEqualTo(created.orderId().toString());
            assertThat(payload.path("occurredAt").asText()).isEqualTo("2026-10-03T13:00:00Z");
            assertThat(payload.path("traceId").asText()).isEqualTo("trace-order-020");
            assertThat(payload.path("correlationId").asText()).isEqualTo("correlation-order-020");

            JsonNode data = payload.path("data");
            assertThat(data.path("orderId").asText()).isEqualTo(created.orderId().toString());
            assertThat(data.path("customerId").asText()).isEqualTo("jwt-customer-123");
            assertThat(data.path("status").asText()).isEqualTo("CREATED");
            assertThat(data.path("subtotalAmount").decimalValue()).isEqualByComparingTo("31.7500");
            assertThat(data.path("totalAmount").decimalValue()).isEqualByComparingTo("31.7500");
            assertThat(data.path("currency").asText()).isEqualTo("USD");
            assertThat(data.path("createdAt").asText()).isEqualTo("2026-10-03T13:00:00Z");
            assertThat(data.path("items").size()).isEqualTo(2);
            assertThat(data.path("items").get(0).path("productSku").asText()).isEqualTo("SKU-001");
            assertThat(data.path("items").get(0).path("productName").asText()).isEqualTo("Snapshot Jacket");
            assertThat(data.path("items").get(0).path("quantity").asInt()).isEqualTo(2);
            assertThat(data.path("items").get(0).path("lineTotalAmount").decimalValue())
                    .isEqualByComparingTo("25.0000");
        });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rollsBackCreatedOrderAndOutboxEventTogetherWhenTransactionFails() {
        OrderCreationDraft draft = new OrderCreationDraft(
                UUID.randomUUID(),
                "jwt-customer-rollback",
                new BigDecimal("25.0000"),
                new BigDecimal("25.0000"),
                "USD",
                NOW,
                List.of(new OrderCreationDraftItem(
                        1,
                        UUID.randomUUID(),
                        "SKU-ROLLBACK",
                        "Rollback Snapshot",
                        2,
                        new BigDecimal("12.5000"),
                        "USD",
                        new BigDecimal("25.0000"))));
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            orderCreationPersistence.persistCreatedOrder(draft);
            assertThat(orderRepository.existsById(draft.orderId())).isTrue();
            assertThat(outboxEventRepository.count()).isEqualTo(1);
            throw new ForcedRollbackException();
        })).isInstanceOf(ForcedRollbackException.class);

        assertThat(orderRepository.findAll()).isEmpty();
        assertThat(outboxEventRepository.findAll()).isEmpty();
    }

    @Test
    void rejectsEmptyOrdersBeforeCallingCatalog() {
        assertCreationFailure(
                new OrderCreationCommand("jwt-customer-123", List.of()),
                OrderCreationFailure.EMPTY_ORDER);

        assertThat(catalogProductClient.resolvedProductIds()).isEmpty();
        assertThat(orderRepository.findAll()).isEmpty();
    }

    @Test
    void rejectsDuplicateProductsBeforeCallingCatalog() {
        UUID productId = UUID.randomUUID();

        assertCreationFailure(
                new OrderCreationCommand(
                        "jwt-customer-123",
                        List.of(
                                new OrderCreationItemCommand(productId, 1),
                                new OrderCreationItemCommand(productId, 2))),
                OrderCreationFailure.DUPLICATE_PRODUCT);

        assertThat(catalogProductClient.resolvedProductIds()).isEmpty();
        assertThat(orderRepository.findAll()).isEmpty();
    }

    @Test
    void rejectsMissingProductIdsBeforeCallingCatalog() {
        assertCreationFailure(
                new OrderCreationCommand(
                        "jwt-customer-123",
                        List.of(new OrderCreationItemCommand(null, 1))),
                OrderCreationFailure.MISSING_PRODUCT_ID);

        assertThat(catalogProductClient.resolvedProductIds()).isEmpty();
        assertThat(orderRepository.findAll()).isEmpty();
    }

    @Test
    void rejectsInvalidQuantitiesBeforeCallingCatalog() {
        UUID productId = UUID.randomUUID();

        assertCreationFailure(
                new OrderCreationCommand(
                        "jwt-customer-123",
                        List.of(new OrderCreationItemCommand(productId, 0))),
                OrderCreationFailure.INVALID_QUANTITY);
        assertCreationFailure(
                new OrderCreationCommand(
                        "jwt-customer-123",
                        List.of(new OrderCreationItemCommand(productId, OrderCreationService.MAX_ITEM_QUANTITY + 1))),
                OrderCreationFailure.INVALID_QUANTITY);

        assertThat(catalogProductClient.resolvedProductIds()).isEmpty();
        assertThat(orderRepository.findAll()).isEmpty();
    }

    @Test
    void rejectsMixedCurrenciesAfterCatalogResolutionWithoutPersistingOrder() {
        UUID firstProductId = UUID.randomUUID();
        UUID secondProductId = UUID.randomUUID();
        catalogProductClient.addSnapshot(snapshot(firstProductId, "SKU-001", "Snapshot Jacket", "12.5000", "USD"));
        catalogProductClient.addSnapshot(snapshot(secondProductId, "SKU-002", "Snapshot Hat", "9.0000", "EUR"));

        assertCreationFailure(
                new OrderCreationCommand(
                        "jwt-customer-123",
                        List.of(
                                new OrderCreationItemCommand(firstProductId, 1),
                                new OrderCreationItemCommand(secondProductId, 1))),
                OrderCreationFailure.MIXED_CURRENCIES);

        assertThat(catalogProductClient.resolvedProductIds()).containsExactly(firstProductId, secondProductId);
        assertThat(orderRepository.findAll()).isEmpty();
    }

    @Test
    void propagatesCatalogSnapshotFailuresWithoutPersistingOrder() {
        UUID productId = UUID.randomUUID();
        catalogProductClient.addFailure(productId, ProductSnapshotResolutionException.missing(productId));

        assertThatThrownBy(() -> orderCreationService.createOrder(new OrderCreationCommand(
                "jwt-customer-123",
                List.of(new OrderCreationItemCommand(productId, 1)))))
                .isInstanceOfSatisfying(ProductSnapshotResolutionException.class, exception ->
                        assertThat(exception.failure()).isEqualTo(ProductSnapshotFailure.MISSING));

        assertThat(orderRepository.findAll()).isEmpty();
    }

    @Test
    void rejectsMissingAuthenticatedCustomer() {
        assertCreationFailure(
                new OrderCreationCommand(" ", List.of(new OrderCreationItemCommand(UUID.randomUUID(), 1))),
                OrderCreationFailure.AUTHENTICATED_CUSTOMER_REQUIRED);
    }

    private void assertCreationFailure(OrderCreationCommand command, OrderCreationFailure failure) {
        assertThatThrownBy(() -> orderCreationService.createOrder(command))
                .isInstanceOfSatisfying(OrderCreationException.class, exception ->
                        assertThat(exception.failure()).isEqualTo(failure));
    }

    private static ProductSnapshot snapshot(
            UUID productId,
            String sku,
            String name,
            String unitPriceAmount,
            String currency) {
        return new ProductSnapshot(
                productId,
                sku,
                name,
                new BigDecimal(unitPriceAmount),
                currency);
    }

    private static JsonNode readPayload(String payload) {
        try {
            return JSON_MAPPER.readTree(payload);
        } catch (JsonProcessingException exception) {
            throw new AssertionError("Outbox payload should be valid JSON.", exception);
        }
    }

    private static final class ForcedRollbackException extends RuntimeException {
    }

    @TestConfiguration
    static class OrderCreationServiceTestConfiguration {

        @Bean
        TestCatalogProductClient testCatalogProductClient() {
            return new TestCatalogProductClient();
        }

        @Bean
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    static class TestCatalogProductClient implements CatalogProductClient {

        private final Map<UUID, ProductSnapshot> snapshots = new HashMap<>();
        private final Map<UUID, ProductSnapshotResolutionException> failures = new HashMap<>();
        private final List<UUID> resolvedProductIds = new java.util.ArrayList<>();

        @Override
        public ProductSnapshot resolveProductSnapshot(UUID productId) {
            resolvedProductIds.add(productId);
            if (failures.containsKey(productId)) {
                throw failures.get(productId);
            }
            return snapshots.get(productId);
        }

        void addSnapshot(ProductSnapshot snapshot) {
            snapshots.put(snapshot.productId(), snapshot);
        }

        void addFailure(UUID productId, ProductSnapshotResolutionException exception) {
            failures.put(productId, exception);
        }

        List<UUID> resolvedProductIds() {
            return List.copyOf(resolvedProductIds);
        }

        void reset() {
            snapshots.clear();
            failures.clear();
            resolvedProductIds.clear();
        }
    }
}
