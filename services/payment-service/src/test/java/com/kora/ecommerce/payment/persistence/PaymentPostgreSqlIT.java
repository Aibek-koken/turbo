package com.kora.ecommerce.payment.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ProcessedEventClaimer.class)
@Testcontainers
class PaymentPostgreSqlIT {

    private static final Instant NOW = Instant.parse("2026-10-04T10:00:00Z");
    private static final String CONSUMER = "payment-service.order-created.v1";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("payment_it")
            .withUsername("payment_it")
            .withPassword("payment_it");

    @DynamicPropertySource
    static void postgresqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:19092");
        registry.add("payment.kafka.order-created.enabled", () -> "false");
    }

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PaymentRepository payments;

    @Autowired
    private PaymentAttemptRepository attempts;

    @Autowired
    private ProcessedEventRepository processedEvents;

    @Autowired
    private OutboxEventRepository outboxEvents;

    @Autowired
    private ProcessedEventClaimer processedEventClaimer;

    @Autowired
    private EntityManager entityManager;

    @Test
    void flywayBuildsPaymentOwnedSchemaOnPostgreSql() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("2");

        assertThat(tableNames())
                .contains("payments", "payment_attempts", "processed_events", "outbox_events");
        assertThat(columnDataType("outbox_events", "payload")).isEqualTo("jsonb");
    }

    @Test
    void persistsPaymentAttemptProcessedMarkerAndJsonbOutboxPayload() {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID sourceEventId = UUID.randomUUID();
        Payment payment = payments.save(Payment.pending(
                paymentId,
                orderId,
                "customer-postgres",
                new BigDecimal("42.9900"),
                "USD",
                sourceEventId,
                NOW));
        PaymentAttempt attempt = PaymentAttempt.pending(
                UUID.randomUUID(),
                payment.getId(),
                1,
                payment.getAmount(),
                payment.getCurrency(),
                UUID.randomUUID(),
                NOW.plusSeconds(1));
        attempt.markSucceeded("mock-provider-postgres", NOW.plusSeconds(2));
        attempts.save(attempt);
        processedEvents.save(processedEvent(sourceEventId, orderId));
        outboxEvents.save(OutboxEvent.paymentResult(
                UUID.randomUUID(),
                orderId,
                "PaymentSucceeded",
                1,
                paymentSucceededPayload(orderId, paymentId, sourceEventId),
                NOW.plusSeconds(3),
                "trace-postgres",
                "corr-postgres"));
        flushAndClear();

        assertThat(payments.findByOrderId(orderId))
                .get()
                .extracting(Payment::getStatus, Payment::getCustomerId, Payment::getAmount, Payment::getCurrency)
                .containsExactly(PaymentStatus.PENDING, "customer-postgres", new BigDecimal("42.9900"), "USD");
        assertThat(attempts.findByPaymentIdOrderByRequestedAtDescIdDesc(paymentId))
                .singleElement()
                .satisfies(savedAttempt -> {
                    assertThat(savedAttempt.getStatus()).isEqualTo(PaymentAttemptStatus.SUCCEEDED);
                    assertThat(savedAttempt.getOutcome()).isEqualTo(PaymentAttemptOutcome.SUCCEEDED);
                    assertThat(savedAttempt.getProviderReference()).isEqualTo("mock-provider-postgres");
                });
        assertThat(processedEvents.findByConsumerNameAndEventId(CONSUMER, sourceEventId)).isPresent();
        assertThat(outboxEvents.findByAggregateIdOrderByOccurredAtAscIdAsc(orderId))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getEventType()).isEqualTo("PaymentSucceeded");
                    assertThat(event.getPayload())
                            .containsEntry("eventType", "PaymentSucceeded")
                            .containsEntry("aggregateId", orderId.toString());
                    assertThat(event.getPayload().get("data")).isInstanceOf(Map.class);
                });
    }

    @Test
    void processedEventClaimerUsesPostgresConflictHandling() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        boolean firstClaim = processedEventClaimer.claimIfAbsent(
                UUID.randomUUID(),
                CONSUMER,
                eventId,
                "OrderCreated",
                1,
                orderId,
                "trace-postgres",
                "corr-postgres",
                NOW);
        boolean duplicateClaim = processedEventClaimer.claimIfAbsent(
                UUID.randomUUID(),
                CONSUMER,
                eventId,
                "OrderCreated",
                1,
                orderId,
                "trace-postgres",
                "corr-postgres",
                NOW);

        assertThat(firstClaim).isTrue();
        assertThat(duplicateClaim).isFalse();
        assertThat(processedEvents.findAll())
                .singleElement()
                .extracting(ProcessedEvent::getConsumerName, ProcessedEvent::getEventId, ProcessedEvent::getAggregateId)
                .containsExactly(CONSUMER, eventId, orderId);
    }

    @Test
    void postgresConstraintsRejectInvalidPaymentRows() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                        insert into payments (
                            id, order_id, customer_id, status, amount, currency,
                            source_event_id, created_at, updated_at, version
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "customer-postgres",
                "PENDING",
                new BigDecimal("42.9900"),
                "usd",
                UUID.randomUUID(),
                OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC),
                OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private ProcessedEvent processedEvent(UUID eventId, UUID aggregateId) {
        return ProcessedEvent.record(
                UUID.randomUUID(),
                CONSUMER,
                eventId,
                "OrderCreated",
                1,
                aggregateId,
                "trace-postgres",
                "corr-postgres",
                NOW);
    }

    private Map<String, Object> paymentSucceededPayload(UUID orderId, UUID paymentId, UUID sourceEventId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("paymentId", paymentId.toString());
        data.put("orderId", orderId.toString());
        data.put("customerId", "customer-postgres");
        data.put("amount", "42.9900");
        data.put("currency", "USD");
        data.put("orderCreatedEventId", sourceEventId.toString());

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", UUID.randomUUID().toString());
        envelope.put("eventType", "PaymentSucceeded");
        envelope.put("eventVersion", 1);
        envelope.put("aggregateId", orderId.toString());
        envelope.put("occurredAt", NOW.plusSeconds(3).toString());
        envelope.put("traceId", "trace-postgres");
        envelope.put("correlationId", "corr-postgres");
        envelope.put("data", data);
        return envelope;
    }

    private void flushAndClear() {
        payments.flush();
        attempts.flush();
        processedEvents.flush();
        outboxEvents.flush();
        entityManager.clear();
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
}
