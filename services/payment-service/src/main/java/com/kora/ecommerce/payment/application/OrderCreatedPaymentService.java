package com.kora.ecommerce.payment.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.kora.ecommerce.payment.order.OrderCreatedEvent;
import com.kora.ecommerce.payment.persistence.OutboxEventRepository;
import com.kora.ecommerce.payment.persistence.Payment;
import com.kora.ecommerce.payment.persistence.PaymentAttempt;
import com.kora.ecommerce.payment.persistence.PaymentAttemptOutcome;
import com.kora.ecommerce.payment.persistence.PaymentAttemptRepository;
import com.kora.ecommerce.payment.persistence.PaymentRepository;
import com.kora.ecommerce.payment.persistence.ProcessedEventClaimer;
import com.kora.ecommerce.payment.persistence.ProcessedEventRepository;
import com.kora.ecommerce.payment.persistence.PaymentStatus;
import com.kora.ecommerce.payment.provider.PaymentProviderClient;
import com.kora.ecommerce.payment.provider.PaymentProviderOutcome;
import com.kora.ecommerce.payment.provider.PaymentProviderRequest;
import com.kora.ecommerce.payment.provider.PaymentProviderResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderCreatedPaymentService {

    private static final String ORDER_CREATED_EVENT_TYPE = "OrderCreated";

    private final PaymentRepository payments;
    private final PaymentAttemptRepository attempts;
    private final OutboxEventRepository outboxEvents;
    private final ProcessedEventClaimer processedEvents;
    private final ProcessedEventRepository processedEventRepository;
    private final PaymentProviderClient providerClient;
    private final Clock clock;
    private final String consumerName;

    public OrderCreatedPaymentService(
            PaymentRepository payments,
            PaymentAttemptRepository attempts,
            OutboxEventRepository outboxEvents,
            ProcessedEventClaimer processedEvents,
            ProcessedEventRepository processedEventRepository,
            PaymentProviderClient providerClient,
            Clock clock,
            @Value("${payment.kafka.order-created.group-id:payment-service.order-created.v1}") String consumerName) {
        this.payments = payments;
        this.attempts = attempts;
        this.outboxEvents = outboxEvents;
        this.processedEvents = processedEvents;
        this.processedEventRepository = processedEventRepository;
        this.providerClient = providerClient;
        this.clock = clock;
        this.consumerName = requireText(consumerName, "consumerName");
    }

    @Transactional
    public OrderCreatedPaymentResult processOrderCreated(OrderCreatedEvent event) {
        Objects.requireNonNull(event, "event is required");
        boolean claimed = processedEvents.claimIfAbsent(
                UUID.randomUUID(),
                consumerName,
                event.eventId(),
                ORDER_CREATED_EVENT_TYPE,
                event.eventVersion(),
                event.aggregateId(),
                event.traceId(),
                event.correlationId(),
                clock.instant());
        if (!claimed) {
            return OrderCreatedPaymentResult.duplicateEvent(event);
        }

        return payments.findByOrderId(event.orderId())
                .map(existing -> handleExistingPayment(event, existing))
                .orElseGet(() -> createPendingPaymentAndAttemptProvider(event));
    }

    private OrderCreatedPaymentResult handleExistingPayment(OrderCreatedEvent event, Payment existing) {
        if (existing.getStatus() == PaymentStatus.PENDING && existing.getSourceEventId().equals(event.eventId())) {
            return attemptProviderForPendingPayment(event, existing);
        }
        return OrderCreatedPaymentResult.paymentAlreadyExists(event, existing.getId());
    }

    private OrderCreatedPaymentResult createPendingPaymentAndAttemptProvider(OrderCreatedEvent event) {
        Payment payment = Payment.pending(
                UUID.randomUUID(),
                event.orderId(),
                event.customerId(),
                event.totalAmount(),
                event.currency(),
                event.eventId(),
                clock.instant());
        Payment saved = payments.save(payment);
        return attemptProviderForPendingPayment(event, saved);
    }

    private OrderCreatedPaymentResult attemptProviderForPendingPayment(OrderCreatedEvent event, Payment payment) {
        UUID providerRequestId = UUID.randomUUID();
        PaymentAttempt attempt = attempts.save(PaymentAttempt.pending(
                UUID.randomUUID(),
                payment.getId(),
                nextAttemptNumber(payment.getId()),
                payment.getAmount(),
                payment.getCurrency(),
                providerRequestId,
                clock.instant()));

        PaymentProviderResult providerResult = providerClient.authorize(new PaymentProviderRequest(
                providerRequestId,
                payment.getId(),
                payment.getOrderId(),
                payment.getCustomerId(),
                payment.getAmount(),
                payment.getCurrency()));

        return applyProviderResult(event, payment, attempt, providerResult);
    }

    private int nextAttemptNumber(UUID paymentId) {
        long existingAttempts = attempts.countByPaymentId(paymentId);
        if (existingAttempts >= Integer.MAX_VALUE) {
            throw new IllegalStateException("payment attempt count exceeded supported range");
        }
        return Math.toIntExact(existingAttempts + 1);
    }

    private OrderCreatedPaymentResult applyProviderResult(
            OrderCreatedEvent event,
            Payment payment,
            PaymentAttempt attempt,
            PaymentProviderResult providerResult) {
        Objects.requireNonNull(providerResult, "providerResult is required");
        Instant completedAt = clock.instant();
        PaymentAttemptOutcome attemptOutcome = toAttemptOutcome(providerResult.outcome());
        switch (providerResult.outcome()) {
            case SUCCEEDED -> {
                attempt.markSucceeded(providerResult.providerReference(), completedAt);
                payment.markSucceeded(providerResult.providerReference(), completedAt);
                outboxEvents.save(PaymentResultOutboxEventFactory.paymentSucceeded(
                        UUID.randomUUID(),
                        event,
                        payment,
                        attempt,
                        completedAt));
                return OrderCreatedPaymentResult.providerApproved(event, payment.getId(), attempt.getId());
            }
            case DECLINED -> {
                attempt.markFailed(
                        attemptOutcome,
                        providerResult.failureReason(),
                        providerResult.providerReference(),
                        completedAt);
                payment.markFailed(providerResult.failureReason(), completedAt);
                outboxEvents.save(PaymentResultOutboxEventFactory.paymentFailed(
                        UUID.randomUUID(),
                        event,
                        payment,
                        attempt,
                        completedAt));
                return OrderCreatedPaymentResult.providerDeclined(event, payment.getId(), attempt.getId());
            }
            case TIMED_OUT, PROVIDER_5XX -> {
                attempt.markFailed(
                        attemptOutcome,
                        providerResult.failureReason(),
                        providerResult.providerReference(),
                        completedAt);
                processedEventRepository.deleteByConsumerNameAndEventId(consumerName, event.eventId());
                return OrderCreatedPaymentResult.providerRetryableFailure(
                        event,
                        payment.getId(),
                        attempt.getId(),
                        attemptOutcome);
            }
            case MALFORMED_RESPONSE -> {
                attempt.markFailed(
                        attemptOutcome,
                        providerResult.failureReason(),
                        providerResult.providerReference(),
                        completedAt);
                payment.markFailed(providerResult.failureReason(), completedAt);
                outboxEvents.save(PaymentResultOutboxEventFactory.paymentFailed(
                        UUID.randomUUID(),
                        event,
                        payment,
                        attempt,
                        completedAt));
                return OrderCreatedPaymentResult.providerMalformedResponse(event, payment.getId(), attempt.getId());
            }
        }
        throw new IllegalStateException("unhandled provider outcome " + providerResult.outcome());
    }

    private PaymentAttemptOutcome toAttemptOutcome(PaymentProviderOutcome providerOutcome) {
        return switch (providerOutcome) {
            case SUCCEEDED -> PaymentAttemptOutcome.SUCCEEDED;
            case DECLINED -> PaymentAttemptOutcome.DECLINED;
            case TIMED_OUT -> PaymentAttemptOutcome.TIMED_OUT;
            case PROVIDER_5XX -> PaymentAttemptOutcome.PROVIDER_5XX;
            case MALFORMED_RESPONSE -> PaymentAttemptOutcome.MALFORMED_RESPONSE;
        };
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}
