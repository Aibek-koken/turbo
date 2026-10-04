package com.kora.ecommerce.payment.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PaymentPersistenceRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-10-04T10:00:00Z");
    private static final String CONSUMER = "payment-service.order-created.v1";

    @Autowired
    private PaymentRepository payments;

    @Autowired
    private PaymentAttemptRepository attempts;

    @Autowired
    private ProcessedEventRepository processedEvents;

    @Autowired
    private OutboxEventRepository outboxEvents;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void persistsPaymentAndProviderAttemptWithRepositoryLookups() {
        Payment payment = payments.save(payment());
        PaymentAttempt attempt = attempts.save(PaymentAttempt.pending(
                UUID.randomUUID(),
                payment.getId(),
                1,
                payment.getAmount(),
                payment.getCurrency(),
                UUID.randomUUID(),
                NOW.plusSeconds(1)));

        flushAndClear();

        assertThat(payments.findByOrderId(payment.getOrderId()))
                .get()
                .extracting(
                        Payment::getStatus,
                        Payment::getCustomerId,
                        Payment::getAmount,
                        Payment::getCurrency,
                        Payment::getSourceEventId)
                .containsExactly(
                        PaymentStatus.PENDING,
                        payment.getCustomerId(),
                        new BigDecimal("42.9900"),
                        "USD",
                        payment.getSourceEventId());
        assertThat(payments.existsByOrderId(payment.getOrderId())).isTrue();
        assertThat(attempts.findByPaymentIdOrderByRequestedAtDescIdDesc(payment.getId()))
                .singleElement()
                .extracting(
                        PaymentAttempt::getId,
                        PaymentAttempt::getStatus,
                        PaymentAttempt::getOutcome,
                        PaymentAttempt::getProviderRequestId)
                .containsExactly(
                        attempt.getId(),
                        PaymentAttemptStatus.PENDING,
                        PaymentAttemptOutcome.REQUESTED,
                        attempt.getProviderRequestId());
    }

    @Test
    void rejectsDuplicatePaymentForOneOrder() {
        UUID orderId = UUID.randomUUID();
        payments.save(paymentForOrder(orderId, UUID.randomUUID()));
        payments.save(paymentForOrder(orderId, UUID.randomUUID()));

        assertThatThrownBy(this::flushAndClear)
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsPaymentConstraintViolations() {
        assertThatThrownBy(() -> payments.save(paymentWithCurrency("usd")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currency");

        assertThatThrownBy(() -> payments.save(paymentWithAmount("0.0000")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }

    @Test
    void migrationRejectsInvalidPaymentRows() {
        assertThatThrownBy(() -> insertPaymentRow("PENDING", "0.0000", "USD"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertPaymentRow("AUTHORIZED", "42.9900", "USD"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertPaymentRow("PENDING", "42.9900", "usd"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateAttemptNumberForSamePayment() {
        Payment payment = payments.save(payment());
        attempts.save(PaymentAttempt.pending(
                UUID.randomUUID(),
                payment.getId(),
                1,
                payment.getAmount(),
                payment.getCurrency(),
                UUID.randomUUID(),
                NOW));
        attempts.save(PaymentAttempt.pending(
                UUID.randomUUID(),
                payment.getId(),
                1,
                payment.getAmount(),
                payment.getCurrency(),
                UUID.randomUUID(),
                NOW.plusSeconds(1)));

        assertThatThrownBy(this::flushAndClear)
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void enforcesProcessedEventIdempotencyKeyPerConsumer() {
        UUID eventId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        processedEvents.save(processedEvent(CONSUMER, eventId, aggregateId));

        flushAndClear();

        assertThat(processedEvents.existsByConsumerNameAndEventId(CONSUMER, eventId)).isTrue();
        assertThat(processedEvents.findByConsumerNameAndEventId(CONSUMER, eventId))
                .get()
                .extracting(ProcessedEvent::getEventType, ProcessedEvent::getEventVersion, ProcessedEvent::getAggregateId)
                .containsExactly("OrderCreated", 1, aggregateId);

        processedEvents.save(processedEvent(CONSUMER, eventId, aggregateId));
        assertThatThrownBy(this::flushAndClear)
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsSameSourceEventForDifferentConsumers() {
        UUID eventId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        processedEvents.save(processedEvent(CONSUMER, eventId, aggregateId));
        processedEvents.save(processedEvent("payment-service.audit-copy.v1", eventId, aggregateId));

        flushAndClear();

        assertThat(processedEvents.findAll()).hasSize(2);
    }

    @Test
    void persistsAppendOnlyOutboxEventPayload() {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID sourceEventId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("paymentId", paymentId.toString());
        data.put("orderId", orderId.toString());
        data.put("customerId", "customer-123");
        data.put("amount", "42.9900");
        data.put("currency", "USD");
        data.put("providerReference", "mock-provider-123");
        data.put("orderCreatedEventId", sourceEventId.toString());

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId.toString());
        envelope.put("eventType", "PaymentSucceeded");
        envelope.put("eventVersion", 1);
        envelope.put("aggregateId", orderId.toString());
        envelope.put("occurredAt", NOW.toString());
        envelope.put("traceId", "4bf92f3577b34da6a3ce929d0e0e4736");
        envelope.put("correlationId", "corr-123");
        envelope.put("data", data);

        outboxEvents.save(OutboxEvent.paymentResult(
                eventId,
                orderId,
                "PaymentSucceeded",
                1,
                envelope,
                NOW,
                "4bf92f3577b34da6a3ce929d0e0e4736",
                "corr-123"));

        flushAndClear();

        assertThat(outboxEvents.findByAggregateIdOrderByOccurredAtAscIdAsc(orderId))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getId()).isEqualTo(eventId);
                    assertThat(event.getEventType()).isEqualTo("PaymentSucceeded");
                    assertThat(event.getEventVersion()).isEqualTo(1);
                    assertThat(event.getPayload())
                            .containsEntry("eventId", eventId.toString())
                            .containsEntry("eventType", "PaymentSucceeded")
                            .containsEntry("aggregateId", orderId.toString());
                    assertThat(event.getPayload().get("data")).isInstanceOf(Map.class);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> persistedData = (Map<String, Object>) event.getPayload().get("data");
                    assertThat(persistedData)
                            .containsEntry("paymentId", paymentId.toString())
                            .containsEntry("orderCreatedEventId", sourceEventId.toString())
                            .containsEntry("currency", "USD");
                });
    }

    private Payment payment() {
        return paymentForOrder(UUID.randomUUID(), UUID.randomUUID());
    }

    private Payment paymentForOrder(UUID orderId, UUID sourceEventId) {
        return Payment.pending(
                UUID.randomUUID(),
                orderId,
                "customer-" + UUID.randomUUID(),
                new BigDecimal("42.9900"),
                "USD",
                sourceEventId,
                NOW);
    }

    private Payment paymentWithCurrency(String currency) {
        return Payment.pending(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "customer-123",
                new BigDecimal("42.9900"),
                currency,
                UUID.randomUUID(),
                NOW);
    }

    private Payment paymentWithAmount(String amount) {
        return Payment.pending(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "customer-123",
                new BigDecimal(amount),
                "USD",
                UUID.randomUUID(),
                NOW);
    }

    private ProcessedEvent processedEvent(String consumerName, UUID eventId, UUID aggregateId) {
        return ProcessedEvent.record(
                UUID.randomUUID(),
                consumerName,
                eventId,
                "OrderCreated",
                1,
                aggregateId,
                "4bf92f3577b34da6a3ce929d0e0e4736",
                "corr-123",
                NOW);
    }

    private void insertPaymentRow(String status, String amount, String currency) {
        jdbcTemplate.update(
                """
                        INSERT INTO payments (
                            id, order_id, customer_id, status, amount, currency,
                            source_event_id, created_at, updated_at, version
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "customer-123",
                status,
                new BigDecimal(amount),
                currency,
                UUID.randomUUID(),
                NOW,
                NOW);
    }

    private void flushAndClear() {
        payments.flush();
        entityManager.clear();
    }
}
