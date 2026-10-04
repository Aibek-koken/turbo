package com.kora.ecommerce.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.kora.ecommerce.payment.order.OrderCreatedEvent;
import com.kora.ecommerce.payment.persistence.OutboxEventRepository;
import com.kora.ecommerce.payment.persistence.PaymentAttempt;
import com.kora.ecommerce.payment.persistence.PaymentAttemptOutcome;
import com.kora.ecommerce.payment.persistence.PaymentAttemptRepository;
import com.kora.ecommerce.payment.persistence.PaymentAttemptStatus;
import com.kora.ecommerce.payment.persistence.PaymentRepository;
import com.kora.ecommerce.payment.persistence.PaymentStatus;
import com.kora.ecommerce.payment.persistence.ProcessedEvent;
import com.kora.ecommerce.payment.persistence.ProcessedEventClaimer;
import com.kora.ecommerce.payment.persistence.ProcessedEventRepository;
import com.kora.ecommerce.payment.provider.PaymentProviderClient;
import com.kora.ecommerce.payment.provider.PaymentProviderRequest;
import com.kora.ecommerce.payment.provider.PaymentProviderResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
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

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        OrderCreatedPaymentService.class,
        ProcessedEventClaimer.class,
        OrderCreatedPaymentServiceTest.TestPaymentConfiguration.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrderCreatedPaymentServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-04T10:15:30Z");
    private static final String CONSUMER = "payment-service.order-created.v1";

    @Autowired
    private OrderCreatedPaymentService service;

    @Autowired
    private PaymentRepository payments;

    @Autowired
    private PaymentAttemptRepository attempts;

    @Autowired
    private ProcessedEventRepository processedEvents;

    @Autowired
    private OutboxEventRepository outboxEvents;

    @Autowired
    private FakePaymentProviderClient providerClient;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    @AfterEach
    void cleanup() {
        attempts.deleteAll();
        processedEvents.deleteAll();
        outboxEvents.deleteAll();
        payments.deleteAll();
        providerClient.reset();
    }

    @Test
    void approvedProviderAttemptMarksPaymentSucceededAndStoresProviderReference() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        OrderCreatedPaymentResult result = service.processOrderCreated(event(eventId, orderId));

        flushAndClear();

        assertThat(result.outcome()).isEqualTo(OrderCreatedPaymentResult.Outcome.PROVIDER_APPROVED);
        assertThat(result.paymentId()).isNotNull();
        assertThat(result.attemptId()).isNotNull();
        assertThat(result.attemptOutcome()).isEqualTo(PaymentAttemptOutcome.SUCCEEDED);
        assertThat(payments.findByOrderId(orderId))
                .get()
                .satisfies(payment -> {
                    assertThat(payment.getId()).isEqualTo(result.paymentId());
                    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
                    assertThat(payment.getOrderId()).isEqualTo(orderId);
                    assertThat(payment.getCustomerId()).isEqualTo("jwt-customer-123");
                    assertThat(payment.getAmount()).isEqualByComparingTo(new BigDecimal("42.9900"));
                    assertThat(payment.getCurrency()).isEqualTo("USD");
                    assertThat(payment.getProviderReference()).isEqualTo("mock-provider-ref-123");
                    assertThat(payment.getFailureReason()).isNull();
                    assertThat(payment.getSourceEventId()).isEqualTo(eventId);
                    assertThat(payment.getCreatedAt()).isEqualTo(NOW);
                    assertThat(payment.getUpdatedAt()).isEqualTo(NOW);
        });
        assertProcessedMarker(eventId, orderId);
        assertAttempt(
                result.paymentId(),
                PaymentAttemptStatus.SUCCEEDED,
                PaymentAttemptOutcome.SUCCEEDED,
                "mock-provider-ref-123",
                null);
        assertProviderRequest(result.paymentId(), orderId);
        assertPaymentResultOutboxEvent(
                orderId,
                PaymentResultOutboxEventFactory.PAYMENT_SUCCEEDED,
                result.paymentId(),
                result.attemptId(),
                PaymentAttemptOutcome.SUCCEEDED,
                PaymentStatus.SUCCEEDED,
                "mock-provider-ref-123",
                null,
                eventId);
    }

    @Test
    void declinedProviderAttemptMarksPaymentFailedWithSafeReason() {
        providerClient.respondWith(PaymentProviderResult.declined("mock-provider-decline-123"));
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        OrderCreatedPaymentResult result = service.processOrderCreated(event(eventId, orderId));

        assertThat(result.outcome()).isEqualTo(OrderCreatedPaymentResult.Outcome.PROVIDER_DECLINED);
        assertThat(payments.findByOrderId(orderId))
                .get()
                .satisfies(payment -> {
                    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
                    assertThat(payment.getProviderReference()).isNull();
                    assertThat(payment.getFailureReason()).isEqualTo("PAYMENT_DECLINED");
                });
        assertAttempt(
                result.paymentId(),
                PaymentAttemptStatus.FAILED,
                PaymentAttemptOutcome.DECLINED,
                "mock-provider-decline-123",
                "PAYMENT_DECLINED");
        assertPaymentResultOutboxEvent(
                orderId,
                PaymentResultOutboxEventFactory.PAYMENT_FAILED,
                result.paymentId(),
                result.attemptId(),
                PaymentAttemptOutcome.DECLINED,
                PaymentStatus.FAILED,
                "mock-provider-decline-123",
                "PAYMENT_DECLINED",
                eventId);
    }

    @Test
    void timeoutProviderAttemptRemainsPendingForLaterRetry() {
        providerClient.respondWith(PaymentProviderResult.timedOut());
        UUID orderId = UUID.randomUUID();

        OrderCreatedPaymentResult result = service.processOrderCreated(event(UUID.randomUUID(), orderId));

        assertThat(result.outcome()).isEqualTo(OrderCreatedPaymentResult.Outcome.PROVIDER_RETRYABLE_FAILURE);
        assertThat(result.attemptOutcome()).isEqualTo(PaymentAttemptOutcome.TIMED_OUT);
        assertThat(payments.findByOrderId(orderId))
                .get()
                .satisfies(payment -> {
                    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
                    assertThat(payment.getProviderReference()).isNull();
                    assertThat(payment.getFailureReason()).isNull();
                });
        assertAttempt(
                result.paymentId(),
                PaymentAttemptStatus.FAILED,
                PaymentAttemptOutcome.TIMED_OUT,
                null,
                "PROVIDER_TIMEOUT");
        assertThat(outboxEvents.findAll()).isEmpty();
        assertThat(processedEvents.findByConsumerNameAndEventId(CONSUMER, result.eventId())).isEmpty();
    }

    @Test
    void provider5xxAttemptRemainsPendingForLaterRetry() {
        providerClient.respondWith(PaymentProviderResult.provider5xx(503));
        UUID orderId = UUID.randomUUID();

        OrderCreatedPaymentResult result = service.processOrderCreated(event(UUID.randomUUID(), orderId));

        assertThat(result.outcome()).isEqualTo(OrderCreatedPaymentResult.Outcome.PROVIDER_RETRYABLE_FAILURE);
        assertThat(result.attemptOutcome()).isEqualTo(PaymentAttemptOutcome.PROVIDER_5XX);
        assertThat(payments.findByOrderId(orderId))
                .get()
                .satisfies(payment -> {
                    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
                    assertThat(payment.getProviderReference()).isNull();
                    assertThat(payment.getFailureReason()).isNull();
                });
        assertAttempt(
                result.paymentId(),
                PaymentAttemptStatus.FAILED,
                PaymentAttemptOutcome.PROVIDER_5XX,
                null,
                "PROVIDER_5XX_HTTP_503");
        assertThat(outboxEvents.findAll()).isEmpty();
        assertThat(processedEvents.findByConsumerNameAndEventId(CONSUMER, result.eventId())).isEmpty();
    }

    @Test
    void replayAfterRetryableProviderFailureReusesPaymentAndStopsAfterTerminalResult() {
        providerClient.respondWith(PaymentProviderResult.timedOut());
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        OrderCreatedEvent event = event(eventId, orderId);

        OrderCreatedPaymentResult retryable = service.processOrderCreated(event);

        assertThat(retryable.outcome()).isEqualTo(OrderCreatedPaymentResult.Outcome.PROVIDER_RETRYABLE_FAILURE);
        assertThat(payments.findAll()).hasSize(1);
        assertThat(attempts.findAll()).hasSize(1);
        assertThat(processedEvents.findByConsumerNameAndEventId(CONSUMER, eventId)).isEmpty();

        providerClient.respondWith(PaymentProviderResult.succeeded("mock-provider-ref-after-retry"));

        OrderCreatedPaymentResult replayed = service.processOrderCreated(event);

        assertThat(replayed.outcome()).isEqualTo(OrderCreatedPaymentResult.Outcome.PROVIDER_APPROVED);
        assertThat(replayed.paymentId()).isEqualTo(retryable.paymentId());
        assertThat(payments.findAll()).hasSize(1);
        assertThat(payments.findByOrderId(orderId))
                .get()
                .satisfies(payment -> {
                    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
                    assertThat(payment.getProviderReference()).isEqualTo("mock-provider-ref-after-retry");
                });
        assertThat(attempts.findByPaymentIdOrderByRequestedAtDescIdDesc(replayed.paymentId()))
                .extracting(PaymentAttempt::getAttemptNumber, PaymentAttempt::getOutcome)
                .containsExactlyInAnyOrder(
                        tuple(1, PaymentAttemptOutcome.TIMED_OUT),
                        tuple(2, PaymentAttemptOutcome.SUCCEEDED));
        assertThat(providerClient.requests()).hasSize(2);
        assertProcessedMarker(eventId, orderId);
        assertThat(outboxEvents.findAll()).hasSize(1);

        OrderCreatedPaymentResult duplicate = service.processOrderCreated(event);

        assertThat(duplicate.outcome()).isEqualTo(OrderCreatedPaymentResult.Outcome.DUPLICATE_EVENT);
        assertThat(payments.findAll()).hasSize(1);
        assertThat(attempts.findAll()).hasSize(2);
        assertThat(outboxEvents.findAll()).hasSize(1);
        assertThat(providerClient.requests()).hasSize(2);
    }

    @Test
    void malformedProviderResponseMarksPaymentFailedWithResultEvent() {
        providerClient.respondWith(PaymentProviderResult.malformedResponse());
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        OrderCreatedPaymentResult result = service.processOrderCreated(event(eventId, orderId));

        assertThat(result.outcome()).isEqualTo(OrderCreatedPaymentResult.Outcome.PROVIDER_MALFORMED_RESPONSE);
        assertThat(result.attemptOutcome()).isEqualTo(PaymentAttemptOutcome.MALFORMED_RESPONSE);
        assertThat(payments.findByOrderId(orderId))
                .get()
                .satisfies(payment -> {
                    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
                    assertThat(payment.getProviderReference()).isNull();
                    assertThat(payment.getFailureReason()).isEqualTo("PROVIDER_MALFORMED_RESPONSE");
                });
        assertAttempt(
                result.paymentId(),
                PaymentAttemptStatus.FAILED,
                PaymentAttemptOutcome.MALFORMED_RESPONSE,
                null,
                "PROVIDER_MALFORMED_RESPONSE");
        assertPaymentResultOutboxEvent(
                orderId,
                PaymentResultOutboxEventFactory.PAYMENT_FAILED,
                result.paymentId(),
                result.attemptId(),
                PaymentAttemptOutcome.MALFORMED_RESPONSE,
                PaymentStatus.FAILED,
                null,
                "PROVIDER_MALFORMED_RESPONSE",
                eventId);
    }

    @Test
    void skipsDuplicateDeliveryWithoutSecondPaymentOrProviderAttempt() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        OrderCreatedEvent event = event(eventId, orderId);

        OrderCreatedPaymentResult first = service.processOrderCreated(event);
        OrderCreatedPaymentResult duplicate = service.processOrderCreated(event);

        assertThat(first.outcome()).isEqualTo(OrderCreatedPaymentResult.Outcome.PROVIDER_APPROVED);
        assertThat(duplicate.outcome()).isEqualTo(OrderCreatedPaymentResult.Outcome.DUPLICATE_EVENT);
        assertThat(payments.findAll()).hasSize(1);
        assertThat(processedEvents.findAll()).hasSize(1);
        assertThat(attempts.findAll()).hasSize(1);
        assertThat(outboxEvents.findAll()).hasSize(1);
        assertThat(providerClient.requests()).hasSize(1);
    }

    @Test
    void concurrentDuplicateDeliveryCreatesOnePaymentAndOneProviderAttempt() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        OrderCreatedEvent event = event(eventId, orderId);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<OrderCreatedPaymentResult> call = () -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out waiting to start concurrent duplicate delivery");
            }
            return service.processOrderCreated(event);
        };

        try {
            Future<OrderCreatedPaymentResult> first = executor.submit(call);
            Future<OrderCreatedPaymentResult> second = executor.submit(call);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();

            start.countDown();

            List<OrderCreatedPaymentResult> results = List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS));

            assertThat(results)
                    .extracting(OrderCreatedPaymentResult::outcome)
                    .containsExactlyInAnyOrder(
                            OrderCreatedPaymentResult.Outcome.PROVIDER_APPROVED,
                            OrderCreatedPaymentResult.Outcome.DUPLICATE_EVENT);
            assertThat(results)
                    .filteredOn(result -> result.outcome() == OrderCreatedPaymentResult.Outcome.PROVIDER_APPROVED)
                    .singleElement()
                    .extracting(OrderCreatedPaymentResult::paymentId)
                    .isNotNull();
            assertThat(payments.findAll()).hasSize(1);
            assertThat(processedEvents.findAll())
                    .singleElement()
                    .extracting(
                            ProcessedEvent::getConsumerName,
                            ProcessedEvent::getEventId,
                            ProcessedEvent::getAggregateId)
                    .containsExactly(CONSUMER, eventId, orderId);
            assertThat(attempts.findAll()).hasSize(1);
            assertThat(outboxEvents.findAll()).hasSize(1);
            assertThat(providerClient.requests()).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rollsBackPaymentAttemptAndResultEventWithEnclosingTransaction() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            OrderCreatedPaymentResult result = service.processOrderCreated(event(eventId, orderId));

            assertThat(result.outcome()).isEqualTo(OrderCreatedPaymentResult.Outcome.PROVIDER_APPROVED);
            assertThat(payments.findAll()).hasSize(1);
            assertThat(attempts.findAll()).hasSize(1);
            assertThat(processedEvents.findAll()).hasSize(1);
            assertThat(outboxEvents.findAll()).hasSize(1);
            throw new IntentionalRollbackException();
        })).isInstanceOf(IntentionalRollbackException.class);

        assertThat(payments.findByOrderId(orderId)).isEmpty();
        assertThat(attempts.findAll()).isEmpty();
        assertThat(processedEvents.findAll()).isEmpty();
        assertThat(outboxEvents.findAll()).isEmpty();
        assertThat(providerClient.requests()).hasSize(1);
    }

    private void assertProcessedMarker(UUID eventId, UUID orderId) {
        assertThat(processedEvents.findByConsumerNameAndEventId(CONSUMER, eventId))
                .get()
                .satisfies(processedEvent -> {
                    assertThat(processedEvent.getEventType()).isEqualTo("OrderCreated");
                    assertThat(processedEvent.getEventVersion()).isEqualTo(1);
                    assertThat(processedEvent.getAggregateId()).isEqualTo(orderId);
                    assertThat(processedEvent.getTraceId()).isEqualTo("trace-123");
                    assertThat(processedEvent.getCorrelationId()).isEqualTo("corr-123");
                    assertThat(processedEvent.getProcessedAt()).isEqualTo(NOW);
                });
    }

    private void assertAttempt(
            UUID paymentId,
            PaymentAttemptStatus status,
            PaymentAttemptOutcome outcome,
            String providerReference,
            String failureReason) {
        assertThat(attempts.findByPaymentIdOrderByRequestedAtDescIdDesc(paymentId))
                .singleElement()
                .satisfies(attempt -> {
                    assertThat(attempt.getStatus()).isEqualTo(status);
                    assertThat(attempt.getOutcome()).isEqualTo(outcome);
                    assertThat(attempt.getAttemptNumber()).isEqualTo(1);
                    assertThat(attempt.getAmount()).isEqualByComparingTo(new BigDecimal("42.9900"));
                    assertThat(attempt.getCurrency()).isEqualTo("USD");
                    assertThat(attempt.getProviderRequestId()).isNotNull();
                    assertThat(attempt.getProviderReference()).isEqualTo(providerReference);
                    assertThat(attempt.getFailureReason()).isEqualTo(failureReason);
                    assertThat(attempt.getRequestedAt()).isEqualTo(NOW);
                    assertThat(attempt.getCompletedAt()).isEqualTo(NOW);
                });
    }

    private void assertProviderRequest(UUID paymentId, UUID orderId) {
        assertThat(providerClient.requests())
                .singleElement()
                .satisfies(request -> {
                    assertThat(request.paymentId()).isEqualTo(paymentId);
                    assertThat(request.orderId()).isEqualTo(orderId);
                    assertThat(request.customerId()).isEqualTo("jwt-customer-123");
                    assertThat(request.amount()).isEqualByComparingTo(new BigDecimal("42.9900"));
                    assertThat(request.currency()).isEqualTo("USD");
                    assertThat(request.providerRequestId()).isNotNull();
                });
    }

    private void assertPaymentResultOutboxEvent(
            UUID orderId,
            String eventType,
            UUID paymentId,
            UUID attemptId,
            PaymentAttemptOutcome attemptOutcome,
            PaymentStatus paymentStatus,
            String providerReference,
            String failureReason,
            UUID orderCreatedEventId) {
        assertThat(outboxEvents.findByAggregateIdOrderByOccurredAtAscIdAsc(orderId))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getId()).isNotNull();
                    assertThat(event.getAggregateId()).isEqualTo(orderId);
                    assertThat(event.getEventType()).isEqualTo(eventType);
                    assertThat(event.getEventVersion()).isEqualTo(PaymentResultOutboxEventFactory.EVENT_VERSION);
                    assertThat(event.getOccurredAt()).isEqualTo(NOW);
                    assertThat(event.getTraceId()).isEqualTo("trace-123");
                    assertThat(event.getCorrelationId()).isEqualTo("corr-123");

                    Map<String, Object> payload = event.getPayload();
                    assertThat(payload)
                            .containsEntry("eventId", event.getId().toString())
                            .containsEntry("eventType", eventType)
                            .containsEntry("eventVersion", PaymentResultOutboxEventFactory.EVENT_VERSION)
                            .containsEntry("aggregateId", orderId.toString())
                            .containsEntry("occurredAt", NOW.toString())
                            .containsEntry("traceId", "trace-123")
                            .containsEntry("correlationId", "corr-123");

                    assertThat(payload.get("data")).isInstanceOf(Map.class);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) payload.get("data");
                    assertThat(data)
                            .containsEntry("paymentId", paymentId.toString())
                            .containsEntry("orderId", orderId.toString())
                            .containsEntry("customerId", "jwt-customer-123")
                            .containsEntry("amount", "42.9900")
                            .containsEntry("currency", "USD")
                            .containsEntry("paymentStatus", paymentStatus.name())
                            .containsEntry("providerAttemptId", attemptId.toString())
                            .containsEntry("providerAttemptOutcome", attemptOutcome.name())
                            .containsEntry("providerReference", providerReference)
                            .containsEntry("failureReason", failureReason)
                            .containsEntry("orderCreatedEventId", orderCreatedEventId.toString());
                });
    }

    private void flushAndClear() {
        payments.flush();
        attempts.flush();
        processedEvents.flush();
        outboxEvents.flush();
        entityManager.clear();
    }

    private OrderCreatedEvent event(UUID eventId, UUID orderId) {
        return new OrderCreatedEvent(
                eventId,
                1,
                orderId,
                Instant.parse("2026-10-04T10:00:00Z"),
                "trace-123",
                "corr-123",
                orderId,
                "jwt-customer-123",
                new BigDecimal("42.9900"),
                "USD");
    }

    @TestConfiguration
    static class TestPaymentConfiguration {

        @Bean
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        FakePaymentProviderClient fakePaymentProviderClient() {
            return new FakePaymentProviderClient();
        }
    }

    static class FakePaymentProviderClient implements PaymentProviderClient {

        private final AtomicReference<PaymentProviderResult> result =
                new AtomicReference<>(PaymentProviderResult.succeeded("mock-provider-ref-123"));
        private final List<PaymentProviderRequest> requests = new CopyOnWriteArrayList<>();

        @Override
        public PaymentProviderResult authorize(PaymentProviderRequest request) {
            requests.add(request);
            return result.get();
        }

        void respondWith(PaymentProviderResult result) {
            this.result.set(result);
        }

        List<PaymentProviderRequest> requests() {
            return List.copyOf(requests);
        }

        void reset() {
            result.set(PaymentProviderResult.succeeded("mock-provider-ref-123"));
            requests.clear();
        }
    }

    private static final class IntentionalRollbackException extends RuntimeException {
    }
}
