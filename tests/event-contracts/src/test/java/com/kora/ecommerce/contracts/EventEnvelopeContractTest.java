package com.kora.ecommerce.contracts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kora.ecommerce.auditnotification.order.AuditOrderCreatedEvent;
import com.kora.ecommerce.auditnotification.order.AuditOrderCreatedEventParser;
import com.kora.ecommerce.auditnotification.payment.AuditPaymentResultEvent;
import com.kora.ecommerce.auditnotification.payment.AuditPaymentResultEventParser;
import com.kora.ecommerce.order.application.payment.PaymentFailedEnvelope;
import com.kora.ecommerce.order.application.payment.PaymentResultEnvelope;
import com.kora.ecommerce.order.application.payment.PaymentResultEventException;
import com.kora.ecommerce.order.application.payment.PaymentResultEventFailure;
import com.kora.ecommerce.order.application.payment.PaymentResultEventParser;
import com.kora.ecommerce.order.application.payment.PaymentSucceededEnvelope;
import com.kora.ecommerce.order.observability.CorrelationIdFilter;
import com.kora.ecommerce.order.persistence.OrderStatus;
import com.kora.ecommerce.payment.order.InvalidOrderCreatedEventException;
import com.kora.ecommerce.payment.order.OrderCreatedEvent;
import com.kora.ecommerce.payment.order.OrderCreatedEventParser;
import com.kora.ecommerce.payment.persistence.OutboxEvent;
import com.kora.ecommerce.payment.persistence.Payment;
import com.kora.ecommerce.payment.persistence.PaymentAttempt;
import com.kora.ecommerce.payment.persistence.PaymentAttemptOutcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class EventEnvelopeContractTest {

    private static final UUID ORDER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PRODUCT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID ORDER_CREATED_EVENT_ID =
            UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID PAYMENT_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID PAYMENT_ATTEMPT_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID PAYMENT_SUCCEEDED_EVENT_ID =
            UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final UUID PAYMENT_FAILED_EVENT_ID =
            UUID.fromString("88888888-8888-8888-8888-888888888888");
    private static final UUID PROVIDER_REQUEST_ID =
            UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final Instant ORDER_CREATED_AT = Instant.parse("2026-10-04T10:00:00Z");
    private static final Instant PAYMENT_CREATED_AT = Instant.parse("2026-10-04T10:01:00Z");
    private static final Instant PAYMENT_OCCURRED_AT = Instant.parse("2026-10-04T10:02:00Z");
    private static final BigDecimal AMOUNT = new BigDecimal("42.9900");
    private static final String CUSTOMER_ID = "jwt-customer-123";
    private static final String CURRENCY = "USD";
    private static final String TRACE_ID = "trace-contract-123";
    private static final String CORRELATION_ID = "corr-contract-123";
    private static final String PROVIDER_REFERENCE = "provider-ref-contract-123";

    private static final ObjectMapper JSON = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    private final OrderCreatedEventParser paymentOrderCreatedParser =
            new OrderCreatedEventParser(JSON);
    private final AuditOrderCreatedEventParser auditOrderCreatedParser =
            new AuditOrderCreatedEventParser(JSON);
    private final PaymentResultEventParser orderPaymentResultParser =
            new PaymentResultEventParser();
    private final AuditPaymentResultEventParser auditPaymentResultParser =
            new AuditPaymentResultEventParser(JSON);

    @AfterEach
    void clearMappedDiagnosticContext() {
        MDC.clear();
    }

    @Test
    void orderCreatedProducerOutputIsAcceptedByPaymentAndAuditConsumers() {
        String payload = orderCreatedPayloadFromProducer();
        JsonNode root = readTree(payload);

        assertEnvelope(root, "OrderCreated", ORDER_ID, ORDER_CREATED_AT);
        JsonNode data = root.required("data");
        assertThat(data.required("orderId").asText()).isEqualTo(ORDER_ID.toString());
        assertThat(data.required("customerId").asText()).isEqualTo(CUSTOMER_ID);
        assertThat(data.required("status").asText()).isEqualTo(OrderStatus.CREATED.name());
        assertMoney(data.required("subtotalAmount"), AMOUNT);
        assertMoney(data.required("totalAmount"), AMOUNT);
        assertThat(data.required("currency").asText()).isEqualTo(CURRENCY);
        assertThat(data.required("createdAt").asText()).isEqualTo(ORDER_CREATED_AT.toString());
        assertOrderItem(data.required("items").required(0));

        OrderCreatedEvent paymentEvent = paymentOrderCreatedParser.parse(payload);
        assertThat(paymentEvent.eventId()).isEqualTo(UUID.fromString(root.required("eventId").asText()));
        assertThat(paymentEvent.eventVersion()).isEqualTo(1);
        assertThat(paymentEvent.aggregateId()).isEqualTo(ORDER_ID);
        assertThat(paymentEvent.orderId()).isEqualTo(ORDER_ID);
        assertThat(paymentEvent.customerId()).isEqualTo(CUSTOMER_ID);
        assertThat(paymentEvent.totalAmount()).isEqualByComparingTo(AMOUNT);
        assertThat(paymentEvent.totalAmount().scale()).isEqualTo(4);
        assertThat(paymentEvent.currency()).isEqualTo(CURRENCY);
        assertThat(paymentEvent.traceId()).isEqualTo(TRACE_ID);
        assertThat(paymentEvent.correlationId()).isEqualTo(CORRELATION_ID);

        AuditOrderCreatedEvent auditEvent = auditOrderCreatedParser.parse(payload);
        assertThat(auditEvent.eventType()).isEqualTo("OrderCreated");
        assertThat(auditEvent.eventVersion()).isEqualTo(1);
        assertThat(auditEvent.aggregateId()).isEqualTo(ORDER_ID);
        assertThat(auditEvent.orderId()).isEqualTo(ORDER_ID);
        assertThat(auditEvent.customerId()).isEqualTo(CUSTOMER_ID);
        assertThat(auditEvent.traceId()).isEqualTo(TRACE_ID);
        assertThat(auditEvent.correlationId()).isEqualTo(CORRELATION_ID);
    }

    @Test
    void paymentSucceededProducerOutputIsAcceptedByOrderAndAuditConsumers() {
        String payload = paymentSucceededPayloadFromProducer();
        JsonNode root = readTree(payload);

        assertPaymentEnvelope(root, PAYMENT_SUCCEEDED_EVENT_ID, "PaymentSucceeded");
        JsonNode data = root.required("data");
        assertPaymentData(data, "SUCCEEDED", "SUCCEEDED");
        assertThat(data.required("providerReference").asText()).isEqualTo(PROVIDER_REFERENCE);
        assertThat(data.required("failureReason").isNull()).isTrue();

        PaymentResultEnvelope orderEvent = orderPaymentResultParser.parse(payload);
        assertThat(orderEvent).isInstanceOf(PaymentSucceededEnvelope.class);
        assertThat(orderEvent.eventId()).isEqualTo(PAYMENT_SUCCEEDED_EVENT_ID);
        assertThat(orderEvent.eventVersion()).isEqualTo(1);
        assertThat(orderEvent.aggregateId()).isEqualTo(ORDER_ID);
        assertThat(orderEvent.orderId()).isEqualTo(ORDER_ID);
        assertThat(orderEvent.paymentId()).isEqualTo(PAYMENT_ID);
        assertThat(orderEvent.traceId()).isEqualTo(TRACE_ID);
        assertThat(orderEvent.correlationId()).isEqualTo(CORRELATION_ID);
        assertThat(orderEvent.targetStatus()).isEqualTo(OrderStatus.PAID);

        AuditPaymentResultEvent auditEvent = auditPaymentResultParser.parse(payload);
        assertThat(auditEvent.eventType()).isEqualTo("PaymentSucceeded");
        assertThat(auditEvent.eventVersion()).isEqualTo(1);
        assertThat(auditEvent.orderId()).isEqualTo(ORDER_ID);
        assertThat(auditEvent.paymentId()).isEqualTo(PAYMENT_ID);
        assertThat(auditEvent.customerId()).isEqualTo(CUSTOMER_ID);
        assertThat(auditEvent.paymentStatus()).isEqualTo("SUCCEEDED");
        assertThat(auditEvent.traceId()).isEqualTo(TRACE_ID);
        assertThat(auditEvent.correlationId()).isEqualTo(CORRELATION_ID);
    }

    @Test
    void paymentFailedProducerOutputIsAcceptedByOrderAndAuditConsumers() {
        String payload = paymentFailedPayloadFromProducer();
        JsonNode root = readTree(payload);

        assertPaymentEnvelope(root, PAYMENT_FAILED_EVENT_ID, "PaymentFailed");
        JsonNode data = root.required("data");
        assertPaymentData(data, "FAILED", "DECLINED");
        assertThat(data.required("providerReference").isNull()).isTrue();
        assertThat(data.required("failureReason").asText()).isEqualTo("DECLINED");

        PaymentResultEnvelope orderEvent = orderPaymentResultParser.parse(payload);
        assertThat(orderEvent).isInstanceOf(PaymentFailedEnvelope.class);
        assertThat(orderEvent.eventId()).isEqualTo(PAYMENT_FAILED_EVENT_ID);
        assertThat(orderEvent.eventVersion()).isEqualTo(1);
        assertThat(orderEvent.aggregateId()).isEqualTo(ORDER_ID);
        assertThat(orderEvent.orderId()).isEqualTo(ORDER_ID);
        assertThat(orderEvent.paymentId()).isEqualTo(PAYMENT_ID);
        assertThat(orderEvent.targetStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);

        AuditPaymentResultEvent auditEvent = auditPaymentResultParser.parse(payload);
        assertThat(auditEvent.eventType()).isEqualTo("PaymentFailed");
        assertThat(auditEvent.eventVersion()).isEqualTo(1);
        assertThat(auditEvent.orderId()).isEqualTo(ORDER_ID);
        assertThat(auditEvent.paymentId()).isEqualTo(PAYMENT_ID);
        assertThat(auditEvent.customerId()).isEqualTo(CUSTOMER_ID);
        assertThat(auditEvent.paymentStatus()).isEqualTo("FAILED");
    }

    @Test
    void consumersRejectUnsupportedEventVersions() {
        String orderCreatedV2 = withRootInteger(orderCreatedPayloadFromProducer(), "eventVersion", 2);
        assertThatThrownBy(() -> paymentOrderCreatedParser.parse(orderCreatedV2))
                .isInstanceOfSatisfying(InvalidOrderCreatedEventException.class,
                        exception -> assertThat(exception.reason()).isEqualTo("event_version_unsupported"));
        assertThatThrownBy(() -> auditOrderCreatedParser.parse(orderCreatedV2))
                .isInstanceOfSatisfying(
                        com.kora.ecommerce.auditnotification.order.InvalidOrderCreatedEventException.class,
                        exception -> assertThat(exception.reason()).isEqualTo("event_version_unsupported"));

        String paymentSucceededV2 = withRootInteger(paymentSucceededPayloadFromProducer(), "eventVersion", 2);
        assertThatThrownBy(() -> orderPaymentResultParser.parse(paymentSucceededV2))
                .isInstanceOfSatisfying(PaymentResultEventException.class,
                        exception -> assertThat(exception.failure())
                                .isEqualTo(PaymentResultEventFailure.UNSUPPORTED_EVENT_VERSION));
        assertThatThrownBy(() -> auditPaymentResultParser.parse(paymentSucceededV2))
                .isInstanceOfSatisfying(
                        com.kora.ecommerce.auditnotification.payment.InvalidPaymentResultEventException.class,
                        exception -> assertThat(exception.reason()).isEqualTo("event_version_unsupported"));
    }

    @Test
    void consumersRejectMissingRequiredEnvelopeFields() {
        String orderCreatedMissingEventId = withoutRootField(orderCreatedPayloadFromProducer(), "eventId");
        assertThatThrownBy(() -> paymentOrderCreatedParser.parse(orderCreatedMissingEventId))
                .isInstanceOfSatisfying(InvalidOrderCreatedEventException.class,
                        exception -> assertThat(exception.reason()).isEqualTo("event_id_required"));
        assertThatThrownBy(() -> auditOrderCreatedParser.parse(orderCreatedMissingEventId))
                .isInstanceOfSatisfying(
                        com.kora.ecommerce.auditnotification.order.InvalidOrderCreatedEventException.class,
                        exception -> assertThat(exception.reason()).isEqualTo("event_id_required"));

        String orderCreatedMissingTrace = withoutRootField(orderCreatedPayloadFromProducer(), "traceId");
        assertThatThrownBy(() -> auditOrderCreatedParser.parse(orderCreatedMissingTrace))
                .isInstanceOfSatisfying(
                        com.kora.ecommerce.auditnotification.order.InvalidOrderCreatedEventException.class,
                        exception -> assertThat(exception.reason()).isEqualTo("trace_id_required"));

        String paymentSucceededMissingAggregateId =
                withoutRootField(paymentSucceededPayloadFromProducer(), "aggregateId");
        assertThatThrownBy(() -> orderPaymentResultParser.parse(paymentSucceededMissingAggregateId))
                .isInstanceOfSatisfying(PaymentResultEventException.class,
                        exception -> assertThat(exception.failure())
                                .isEqualTo(PaymentResultEventFailure.MISSING_AGGREGATE_ID));
        assertThatThrownBy(() -> auditPaymentResultParser.parse(paymentSucceededMissingAggregateId))
                .isInstanceOfSatisfying(
                        com.kora.ecommerce.auditnotification.payment.InvalidPaymentResultEventException.class,
                        exception -> assertThat(exception.reason()).isEqualTo("aggregate_id_required"));

        String paymentSucceededMissingCorrelationId =
                withoutRootField(paymentSucceededPayloadFromProducer(), "correlationId");
        assertThatThrownBy(() -> auditPaymentResultParser.parse(paymentSucceededMissingCorrelationId))
                .isInstanceOfSatisfying(
                        com.kora.ecommerce.auditnotification.payment.InvalidPaymentResultEventException.class,
                        exception -> assertThat(exception.reason()).isEqualTo("correlation_id_required"));
    }

    private static void assertEnvelope(JsonNode root, String eventType, UUID aggregateId, Instant occurredAt) {
        assertThat(UUID.fromString(root.required("eventId").asText())).isNotNull();
        assertThat(root.required("eventType").asText()).isEqualTo(eventType);
        assertThat(root.required("eventVersion").intValue()).isEqualTo(1);
        assertThat(root.required("aggregateId").asText()).isEqualTo(aggregateId.toString());
        assertThat(root.required("occurredAt").asText()).isEqualTo(occurredAt.toString());
        assertThat(root.required("traceId").asText()).isEqualTo(TRACE_ID);
        assertThat(root.required("correlationId").asText()).isEqualTo(CORRELATION_ID);
        assertThat(root.required("data").isObject()).isTrue();
    }

    private static void assertPaymentEnvelope(JsonNode root, UUID eventId, String eventType) {
        assertEnvelope(root, eventType, ORDER_ID, PAYMENT_OCCURRED_AT);
        assertThat(root.required("eventId").asText()).isEqualTo(eventId.toString());
    }

    private static void assertOrderItem(JsonNode item) {
        assertThat(item.required("itemNumber").intValue()).isEqualTo(1);
        assertThat(item.required("productId").asText()).isEqualTo(PRODUCT_ID.toString());
        assertThat(item.required("productSku").asText()).isEqualTo("SKU-CONTRACT-1");
        assertThat(item.required("productName").asText()).isEqualTo("Contract Product");
        assertThat(item.required("quantity").intValue()).isEqualTo(1);
        assertMoney(item.required("unitPriceAmount"), AMOUNT);
        assertThat(item.required("currency").asText()).isEqualTo(CURRENCY);
        assertMoney(item.required("lineTotalAmount"), AMOUNT);
    }

    private static void assertPaymentData(JsonNode data, String paymentStatus, String attemptOutcome) {
        assertThat(data.required("paymentId").asText()).isEqualTo(PAYMENT_ID.toString());
        assertThat(data.required("orderId").asText()).isEqualTo(ORDER_ID.toString());
        assertThat(data.required("customerId").asText()).isEqualTo(CUSTOMER_ID);
        assertThat(data.required("amount").isTextual()).isTrue();
        assertThat(data.required("amount").asText()).isEqualTo(AMOUNT.toPlainString());
        assertThat(data.required("currency").asText()).isEqualTo(CURRENCY);
        assertThat(data.required("paymentStatus").asText()).isEqualTo(paymentStatus);
        assertThat(data.required("providerAttemptId").asText()).isEqualTo(PAYMENT_ATTEMPT_ID.toString());
        assertThat(data.required("providerAttemptOutcome").asText()).isEqualTo(attemptOutcome);
        assertThat(data.required("orderCreatedEventId").asText()).isEqualTo(ORDER_CREATED_EVENT_ID.toString());
    }

    private static void assertMoney(JsonNode node, BigDecimal expected) {
        assertThat(node.isNumber()).isTrue();
        assertThat(node.decimalValue()).isEqualByComparingTo(expected);
        assertThat(node.decimalValue().scale()).isLessThanOrEqualTo(4);
    }

    private static String orderCreatedPayloadFromProducer() {
        MDC.put("traceId", TRACE_ID);
        MDC.put(CorrelationIdFilter.CORRELATION_ID_MDC_KEY, CORRELATION_ID);
        try {
            Class<?> itemClass = Class.forName("com.kora.ecommerce.order.application.OrderCreationDraftItem");
            Object item = construct(
                    itemClass,
                    new Class<?>[] {
                            int.class,
                            UUID.class,
                            String.class,
                            String.class,
                            int.class,
                            BigDecimal.class,
                            String.class,
                            BigDecimal.class
                    },
                    1,
                    PRODUCT_ID,
                    "SKU-CONTRACT-1",
                    "Contract Product",
                    1,
                    AMOUNT,
                    CURRENCY,
                    AMOUNT);

            Class<?> draftClass = Class.forName("com.kora.ecommerce.order.application.OrderCreationDraft");
            Object draft = construct(
                    draftClass,
                    new Class<?>[] {
                            UUID.class,
                            String.class,
                            BigDecimal.class,
                            BigDecimal.class,
                            String.class,
                            Instant.class,
                            List.class
                    },
                    ORDER_ID,
                    CUSTOMER_ID,
                    AMOUNT,
                    AMOUNT,
                    CURRENCY,
                    ORDER_CREATED_AT,
                    List.of(item));

            Class<?> factoryClass = Class.forName(
                    "com.kora.ecommerce.order.application.OrderCreatedOutboxEventFactory");
            Object factory = construct(factoryClass, new Class<?>[] {});
            Object outbox = invoke(factory, "createEvent", new Class<?>[] {draftClass}, draft);
            return (String) outbox.getClass().getMethod("getPayload").invoke(outbox);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to produce OrderCreated payload through Order Service factory.", exception);
        }
    }

    private static String paymentSucceededPayloadFromProducer() {
        Payment payment = pendingPayment();
        payment.markSucceeded(PROVIDER_REFERENCE, PAYMENT_OCCURRED_AT);
        PaymentAttempt attempt = pendingAttempt();
        attempt.markSucceeded(PROVIDER_REFERENCE, PAYMENT_OCCURRED_AT);

        return paymentPayloadFromProducer("paymentSucceeded", PAYMENT_SUCCEEDED_EVENT_ID, payment, attempt);
    }

    private static String paymentFailedPayloadFromProducer() {
        Payment payment = pendingPayment();
        payment.markFailed("DECLINED", PAYMENT_OCCURRED_AT);
        PaymentAttempt attempt = pendingAttempt();
        attempt.markFailed(PaymentAttemptOutcome.DECLINED, "DECLINED", null, PAYMENT_OCCURRED_AT);

        return paymentPayloadFromProducer("paymentFailed", PAYMENT_FAILED_EVENT_ID, payment, attempt);
    }

    private static Payment pendingPayment() {
        return Payment.pending(
                PAYMENT_ID,
                ORDER_ID,
                CUSTOMER_ID,
                AMOUNT,
                CURRENCY,
                ORDER_CREATED_EVENT_ID,
                PAYMENT_CREATED_AT);
    }

    private static PaymentAttempt pendingAttempt() {
        return PaymentAttempt.pending(
                PAYMENT_ATTEMPT_ID,
                PAYMENT_ID,
                1,
                AMOUNT,
                CURRENCY,
                PROVIDER_REQUEST_ID,
                PAYMENT_CREATED_AT);
    }

    private static String paymentPayloadFromProducer(
            String methodName,
            UUID eventId,
            Payment payment,
            PaymentAttempt attempt) {
        try {
            Class<?> factoryClass = Class.forName(
                    "com.kora.ecommerce.payment.application.PaymentResultOutboxEventFactory");
            Method method = factoryClass.getDeclaredMethod(
                    methodName,
                    UUID.class,
                    OrderCreatedEvent.class,
                    Payment.class,
                    PaymentAttempt.class,
                    Instant.class);
            method.setAccessible(true);
            OutboxEvent outbox = (OutboxEvent) method.invoke(
                    null,
                    eventId,
                    sourceOrderCreatedEvent(),
                    payment,
                    attempt,
                    PAYMENT_OCCURRED_AT);
            return JSON.writeValueAsString(outbox.getPayload());
        } catch (ReflectiveOperationException | JsonProcessingException exception) {
            throw new AssertionError(
                    "Unable to produce payment result payload through Payment Service factory.",
                    unwrapInvocationTarget(exception));
        }
    }

    private static OrderCreatedEvent sourceOrderCreatedEvent() {
        return new OrderCreatedEvent(
                ORDER_CREATED_EVENT_ID,
                1,
                ORDER_ID,
                ORDER_CREATED_AT,
                TRACE_ID,
                CORRELATION_ID,
                ORDER_ID,
                CUSTOMER_ID,
                AMOUNT,
                CURRENCY);
    }

    private static Object construct(Class<?> type, Class<?>[] parameterTypes, Object... args)
            throws ReflectiveOperationException {
        Constructor<?> constructor = type.getDeclaredConstructor(parameterTypes);
        constructor.setAccessible(true);
        return constructor.newInstance(args);
    }

    private static Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args)
            throws ReflectiveOperationException {
        Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static JsonNode readTree(String payload) {
        try {
            return JSON.readTree(payload);
        } catch (JsonProcessingException exception) {
            throw new AssertionError("Producer payload must be valid JSON.", exception);
        }
    }

    private static String withRootInteger(String payload, String field, int value) {
        ObjectNode root = objectRoot(payload);
        root.put(field, value);
        return write(root);
    }

    private static String withoutRootField(String payload, String field) {
        ObjectNode root = objectRoot(payload);
        root.remove(field);
        return write(root);
    }

    private static ObjectNode objectRoot(String payload) {
        JsonNode root = readTree(payload);
        assertThat(root.isObject()).isTrue();
        return (ObjectNode) root;
    }

    private static String write(JsonNode node) {
        try {
            return JSON.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            throw new AssertionError("Mutated contract payload must be serializable.", exception);
        }
    }

    private static Throwable unwrapInvocationTarget(Exception exception) {
        if (exception instanceof InvocationTargetException invocationTargetException
                && invocationTargetException.getTargetException() != null) {
            return invocationTargetException.getTargetException();
        }
        return exception;
    }
}
