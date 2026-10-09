package com.kora.ecommerce.order.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.flywaydb.core.Flyway;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class OrderPostgreSqlIT {

    private static final Instant NOW = Instant.parse("2026-10-03T00:00:00Z");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("order_it")
            .withUsername("order_it")
            .withPassword("order_it");

    @DynamicPropertySource
    static void postgresqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
        registry.add("order.payment-results.consumer-enabled", () -> "false");
    }

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ProcessedPaymentEventRepository processedPaymentEventRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void flywayBuildsOrderOwnedSchemaOnPostgreSql() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("2");

        assertThat(tableNames())
                .contains("orders", "order_items", "order_status_history", "outbox_events", "processed_events");
        assertThat(columnDataType("outbox_events", "payload")).isEqualTo("json");
    }

    @Test
    void persistsOrderAggregateStatusHistoryAndOutboxJson() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        OrderEntity order = validOrder(orderId);
        order.addItem(OrderItemEntity.snapshot(
                UUID.randomUUID(),
                1,
                productId,
                "SKU-PG-001",
                "PostgreSQL Snapshot Jacket",
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
                "trace-pg-1",
                "correlation-pg-1",
                """
                        {
                          "eventType": "OrderCreated",
                          "eventVersion": 1,
                          "aggregateId": "%s",
                          "data": {"customerId": "customer-postgres"}
                        }
                        """.formatted(orderId)));
        entityManager.clear();

        OrderEntity saved = orderRepository.findById(orderId).orElseThrow();

        assertThat(saved.getCustomerId()).isEqualTo("customer-postgres");
        assertThat(saved.getItems())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getProductId()).isEqualTo(productId);
                    assertThat(item.getProductSku()).isEqualTo("SKU-PG-001");
                    assertThat(item.getLineTotalAmount()).isEqualByComparingTo("25.0000");
                });
        assertThat(orderItemRepository.findForOrdersOrdered(Set.of(orderId)))
                .singleElement()
                .extracting(OrderItemEntity::getProductSku)
                .isEqualTo("SKU-PG-001");
        assertThat(orderStatusHistoryRepository.findByOrder_IdOrderByChangedAtAsc(orderId))
                .singleElement()
                .extracting(OrderStatusHistoryEntity::getStatus)
                .isEqualTo(OrderStatus.CREATED);
        assertThat(outboxEventRepository.findAll())
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getAggregateId()).isEqualTo(orderId);
                    assertThat(event.getEventType()).isEqualTo("OrderCreated");
                    assertThat(event.getPayload()).contains("\"customerId\":\"customer-postgres\"");
                });
    }

    @Test
    void postgresConstraintsRejectUnsupportedProcessedPaymentEventType() {
        ProcessedPaymentEventEntity invalidEvent = ProcessedPaymentEventEntity.record(
                "order-service.payment-results.v1",
                UUID.randomUUID(),
                "RefundIssued",
                UUID.randomUUID(),
                UUID.randomUUID(),
                NOW);

        assertConstraintViolation(() -> processedPaymentEventRepository.saveAndFlush(invalidEvent));
    }

    private static OrderEntity validOrder(UUID orderId) {
        return OrderEntity.create(
                orderId,
                "customer-postgres",
                new BigDecimal("25.0000"),
                new BigDecimal("25.0000"),
                "USD",
                NOW);
    }

    private Set<String> tableNames() {
        return new HashSet<>(jdbcTemplate.queryForList(
                """
                        select table_name
                        from information_schema.tables
                        where table_schema = 'public'
                        """,
                String.class));
    }

    private String columnDataType(String tableName, String columnName) {
        return jdbcTemplate.queryForObject(
                """
                        select data_type
                        from information_schema.columns
                        where table_schema = 'public'
                          and table_name = ?
                          and column_name = ?
                        """,
                String.class,
                tableName,
                columnName);
    }

    private static void assertConstraintViolation(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfAny(
                        DataIntegrityViolationException.class,
                        ConstraintViolationException.class,
                        PersistenceException.class);
    }
}
