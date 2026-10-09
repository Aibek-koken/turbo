package com.kora.ecommerce.auditnotification.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.kora.ecommerce.auditnotification.notification.NotificationChannel;
import com.kora.ecommerce.auditnotification.notification.NotificationDeliveryDocument;
import com.kora.ecommerce.auditnotification.notification.NotificationDeliveryRepository;
import com.kora.ecommerce.auditnotification.notification.NotificationDeliveryStatus;
import com.kora.ecommerce.auditnotification.notification.NotificationRouteRequest;
import com.kora.ecommerce.auditnotification.notification.NotificationRoutingResult;
import com.kora.ecommerce.auditnotification.notification.NotificationRoutingService;
import com.kora.ecommerce.auditnotification.observability.AuditNotificationOperationalMetrics;
import com.kora.ecommerce.auditnotification.order.AuditOrderCreatedEvent;
import com.kora.ecommerce.auditnotification.payment.AuditPaymentResultEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@DataMongoTest(properties = "spring.data.mongodb.auto-index-creation=true")
@Testcontainers
class AuditNotificationMongoIT {

    private static final String DATABASE_NAME = "audit_mongo_it_"
            + UUID.randomUUID().toString().replace("-", "");
    private static final Instant ORDER_OCCURRED_AT = Instant.parse("2026-10-04T10:00:00Z");
    private static final Instant PAYMENT_OCCURRED_AT = Instant.parse("2026-10-04T10:15:30Z");
    private static final Instant RECEIVED_AT = Instant.parse("2026-10-04T10:15:31Z");
    private static final Instant DELIVERY_CREATED_AT = Instant.parse("2026-10-04T10:15:32Z");
    private static final Instant DELIVERY_UPDATED_AT = Instant.parse("2026-10-04T10:15:33Z");
    private static final Instant DELIVERY_ATTEMPTED_AT = Instant.parse("2026-10-04T10:16:00Z");
    private static final Instant DELIVERY_SENT_AT = Instant.parse("2026-10-04T10:16:05Z");
    private static final String CUSTOMER_ID = "customer-mongo-it";

    @Container
    private static final MongoDBContainer MONGO =
            new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> MONGO.getReplicaSetUrl(DATABASE_NAME));
        registry.add("spring.data.mongodb.auto-index-creation", () -> "true");
    }

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private AuditEventRepository auditEvents;

    @Autowired
    private NotificationDeliveryRepository notificationDeliveries;

    @BeforeEach
    void resetCollections() {
        auditEvents.deleteAll();
        notificationDeliveries.deleteAll();
    }

    @Test
    void createsAuditAndNotificationIndexesInRealMongo() {
        List<IndexInfo> auditIndexes = mongoTemplate.indexOps(AuditEventDocument.class).getIndexInfo();
        assertIndex(auditIndexes, "ux_audit_events_event_id", true);
        assertIndex(auditIndexes, "ix_audit_events_event_type", false);
        assertIndex(auditIndexes, "ix_audit_events_aggregate_id", false);
        assertIndex(auditIndexes, "ix_audit_events_order_id", false);
        assertIndex(auditIndexes, "ix_audit_events_customer_id", false);
        assertIndex(auditIndexes, "ix_audit_events_payment_id", false);
        assertIndex(auditIndexes, "ix_audit_events_correlation_id", false);
        assertIndex(auditIndexes, "ix_audit_events_occurred_at", false);

        List<IndexInfo> deliveryIndexes = mongoTemplate.indexOps(NotificationDeliveryDocument.class).getIndexInfo();
        IndexInfo uniqueEventChannel = findIndex(deliveryIndexes, "ux_notification_deliveries_event_channel");
        assertThat(uniqueEventChannel.isUnique()).isTrue();
        assertThat(uniqueEventChannel.getIndexFields())
                .extracting(field -> field.getKey())
                .containsExactly("event_id", "channel");
        assertIndex(deliveryIndexes, "ix_notification_deliveries_event_id", false);
        assertIndex(deliveryIndexes, "ix_notification_deliveries_channel", false);
        assertIndex(deliveryIndexes, "ix_notification_deliveries_event_type", false);
        assertIndex(deliveryIndexes, "ix_notification_deliveries_order_id", false);
        assertIndex(deliveryIndexes, "ix_notification_deliveries_customer_id", false);
        assertIndex(deliveryIndexes, "ix_notification_deliveries_payment_id", false);
        assertIndex(deliveryIndexes, "ix_notification_deliveries_status", false);
        assertIndex(deliveryIndexes, "ix_notification_deliveries_correlation_id", false);
    }

    @Test
    void persistsSearchableAuditEventsAndPayloadsThroughBsonRoundTrip() {
        UUID orderEventId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID orderId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID paymentEventId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID paymentId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        AuditEventPersistenceService service = persistenceService();

        AuditEventPersistenceResult first = service.persistOrderCreated(
                orderCreatedEvent(orderEventId, orderId),
                new AuditEventSource("ecommerce.order.events", 0, 7L));
        AuditEventPersistenceResult duplicate = service.persistOrderCreated(
                orderCreatedEvent(orderEventId, orderId),
                new AuditEventSource("ecommerce.order.events", 0, 8L));
        AuditEventPersistenceResult paymentResult = service.persistPaymentResult(
                paymentResultEvent(paymentEventId, orderId, paymentId),
                new AuditEventSource("ecommerce.payment.events", 1, 11L));

        assertThat(first.outcome()).isEqualTo(AuditEventPersistenceResult.Outcome.PERSISTED);
        assertThat(duplicate.outcome()).isEqualTo(AuditEventPersistenceResult.Outcome.DUPLICATE);
        assertThat(paymentResult.outcome()).isEqualTo(AuditEventPersistenceResult.Outcome.PERSISTED);
        assertThat(auditEvents.count()).isEqualTo(2);

        assertThat(auditEvents.findByEventId(orderEventId.toString())).get()
                .satisfies(document -> {
                    assertThat(document.getEventType()).isEqualTo("OrderCreated");
                    assertThat(document.getAggregateId()).isEqualTo(orderId.toString());
                    assertThat(document.getOrderId()).isEqualTo(orderId.toString());
                    assertThat(document.getCustomerId()).isEqualTo(CUSTOMER_ID);
                    assertThat(document.getCorrelationId()).isEqualTo("corr-mongo-order-it");
                    assertThat(document.getSourceOffset()).isEqualTo(7L);
                });
        assertThat(auditEvents.findByEventType("PaymentSucceeded"))
                .extracting(AuditEventDocument::getEventId)
                .containsExactly(paymentEventId.toString());
        assertThat(auditEvents.findByAggregateId(orderId.toString()))
                .extracting(AuditEventDocument::getEventId)
                .containsExactlyInAnyOrder(orderEventId.toString(), paymentEventId.toString());
        assertThat(auditEvents.findByOrderId(orderId.toString()))
                .hasSize(2);
        assertThat(auditEvents.findByPaymentId(paymentId.toString()))
                .extracting(AuditEventDocument::getEventId)
                .containsExactly(paymentEventId.toString());
        assertThat(auditEvents.findByCorrelationId("corr-mongo-payment-it"))
                .extracting(AuditEventDocument::getEventId)
                .containsExactly(paymentEventId.toString());
        assertThat(auditEvents.findByOccurredAtBetween(
                ORDER_OCCURRED_AT.minusSeconds(1),
                ORDER_OCCURRED_AT.plusSeconds(1)))
                .extracting(AuditEventDocument::getEventId)
                .containsExactly(orderEventId.toString());

        Document rawOrder = rawAuditDocument(orderEventId.toString());
        assertThat(rawOrder.getString("event_type")).isEqualTo("OrderCreated");
        assertThat(rawOrder.getLong("source_offset")).isEqualTo(7L);
        Document orderPayload = rawOrder.get("payload", Document.class);
        assertThat(orderPayload)
                .containsEntry("eventId", orderEventId.toString())
                .containsEntry("eventType", "OrderCreated")
                .containsEntry("eventVersion", 1)
                .containsEntry("aggregateId", orderId.toString())
                .containsEntry("traceId", "trace-mongo-order-it")
                .containsEntry("correlationId", "corr-mongo-order-it");
        Document orderData = orderPayload.get("data", Document.class);
        assertThat(orderData)
                .containsEntry("orderId", orderId.toString())
                .containsEntry("customerId", CUSTOMER_ID)
                .containsEntry("status", "CREATED")
                .containsEntry("totalAmount", "42.9900")
                .containsEntry("currency", "USD");
        assertThat(orderData.getList("items", Document.class))
                .singleElement()
                .satisfies(item -> assertThat(item)
                        .containsEntry("itemNumber", 1)
                        .containsEntry("productSku", "MONGO-ROUND-TRIP-1")
                        .containsEntry("quantity", 2)
                        .containsEntry("lineTotalAmount", "42.9900"));

        Document rawPayment = rawAuditDocument(paymentEventId.toString());
        Document paymentData = rawPayment.get("payload", Document.class).get("data", Document.class);
        assertThat(paymentData)
                .containsEntry("paymentId", paymentId.toString())
                .containsEntry("orderId", orderId.toString())
                .containsEntry("customerId", CUSTOMER_ID)
                .containsEntry("paymentStatus", "SUCCEEDED")
                .containsEntry("providerReference", "provider-reference-mongo-it");
    }

    @Test
    void enforcesNotificationDeliveryEventChannelUniquenessInRealMongo() {
        String eventId = "delivery-unique-mongo-it";

        notificationDeliveries.save(delivery(eventId, NotificationChannel.EMAIL, NotificationDeliveryStatus.PENDING));

        assertThatThrownBy(() -> notificationDeliveries.save(
                        delivery(eventId, NotificationChannel.EMAIL, NotificationDeliveryStatus.PENDING)))
                .isInstanceOf(DuplicateKeyException.class);

        notificationDeliveries.save(delivery(eventId, NotificationChannel.PUSH, NotificationDeliveryStatus.PENDING));

        assertThat(notificationDeliveries.findByEventId(eventId))
                .hasSize(2)
                .extracting(NotificationDeliveryDocument::getChannel)
                .containsExactlyInAnyOrder(NotificationChannel.EMAIL, NotificationChannel.PUSH);
    }

    @Test
    void routingReplayRecoversMissingFailedAndInterruptedDeliveriesWithoutDuplicatingSentRecords() {
        NotificationRoutingService routing = routingService();
        String completedEventId = "delivery-completed-replay-mongo-it";
        NotificationRouteRequest completedRequest = routeRequest(
                completedEventId,
                "PaymentSucceeded",
                "payment-completed-mongo-it");

        assertThat(routing.route(completedRequest).channels())
                .extracting(
                        NotificationRoutingResult.ChannelDeliveryResult::channel,
                        NotificationRoutingResult.ChannelDeliveryResult::outcome)
                .containsExactly(
                        tuple(NotificationChannel.EMAIL, NotificationRoutingResult.ChannelOutcome.CREATED),
                        tuple(NotificationChannel.PUSH, NotificationRoutingResult.ChannelOutcome.CREATED));
        notificationDeliveries.findByEventId(completedEventId).forEach(delivery -> {
            delivery.markInProgress(DELIVERY_ATTEMPTED_AT);
            delivery.markSent(DELIVERY_SENT_AT);
            notificationDeliveries.save(delivery);
        });

        assertThat(routing.route(completedRequest).channels())
                .extracting(
                        NotificationRoutingResult.ChannelDeliveryResult::channel,
                        NotificationRoutingResult.ChannelDeliveryResult::outcome)
                .containsExactly(
                        tuple(NotificationChannel.EMAIL, NotificationRoutingResult.ChannelOutcome.ALREADY_SENT),
                        tuple(NotificationChannel.PUSH, NotificationRoutingResult.ChannelOutcome.ALREADY_SENT));
        assertThat(notificationDeliveries.findByEventId(completedEventId))
                .hasSize(2)
                .allSatisfy(delivery -> {
                    assertThat(delivery.getStatus()).isEqualTo(NotificationDeliveryStatus.SENT);
                    assertThat(delivery.getAttemptCount()).isEqualTo(1);
                });

        String partialEventId = "delivery-partial-replay-mongo-it";
        notificationDeliveries.save(delivery(
                partialEventId,
                NotificationChannel.EMAIL,
                NotificationDeliveryStatus.SENT));

        assertThat(routing.route(routeRequest(partialEventId, "PaymentSucceeded", "payment-partial-mongo-it"))
                .channels())
                .extracting(
                        NotificationRoutingResult.ChannelDeliveryResult::channel,
                        NotificationRoutingResult.ChannelDeliveryResult::outcome)
                .containsExactly(
                        tuple(NotificationChannel.EMAIL, NotificationRoutingResult.ChannelOutcome.ALREADY_SENT),
                        tuple(NotificationChannel.PUSH, NotificationRoutingResult.ChannelOutcome.CREATED));
        assertThat(notificationDeliveries.findByEventId(partialEventId)).hasSize(2);
        assertThat(notificationDeliveries.findByEventIdAndChannel(partialEventId, NotificationChannel.EMAIL))
                .get()
                .extracting(NotificationDeliveryDocument::getStatus)
                .isEqualTo(NotificationDeliveryStatus.SENT);
        assertThat(notificationDeliveries.findByEventIdAndChannel(partialEventId, NotificationChannel.PUSH))
                .get()
                .extracting(NotificationDeliveryDocument::getStatus)
                .isEqualTo(NotificationDeliveryStatus.PENDING);

        String retryEventId = "delivery-retry-replay-mongo-it";
        notificationDeliveries.save(delivery(retryEventId, NotificationChannel.EMAIL, NotificationDeliveryStatus.FAILED));
        notificationDeliveries.save(delivery(
                retryEventId,
                NotificationChannel.PUSH,
                NotificationDeliveryStatus.IN_PROGRESS));

        assertThat(routing.route(routeRequest(retryEventId, "PaymentFailed", "payment-retry-mongo-it"))
                .channels())
                .extracting(
                        NotificationRoutingResult.ChannelDeliveryResult::channel,
                        NotificationRoutingResult.ChannelDeliveryResult::outcome)
                .containsExactly(
                        tuple(NotificationChannel.EMAIL, NotificationRoutingResult.ChannelOutcome.REQUEUED_FAILED),
                        tuple(NotificationChannel.PUSH, NotificationRoutingResult.ChannelOutcome.REQUEUED_IN_PROGRESS));
        assertThat(notificationDeliveries.findByEventId(retryEventId))
                .hasSize(2)
                .allSatisfy(delivery -> {
                    assertThat(delivery.getStatus()).isEqualTo(NotificationDeliveryStatus.PENDING);
                    assertThat(delivery.getAttemptCount()).isEqualTo(1);
                    assertThat(delivery.getUpdatedAt()).isEqualTo(DELIVERY_CREATED_AT);
                });
    }

    private AuditEventPersistenceService persistenceService() {
        return new AuditEventPersistenceService(
                auditEvents,
                Clock.fixed(RECEIVED_AT, ZoneOffset.UTC),
                new AuditNotificationOperationalMetrics(new SimpleMeterRegistry()));
    }

    private NotificationRoutingService routingService() {
        return new NotificationRoutingService(
                notificationDeliveries,
                Clock.fixed(DELIVERY_CREATED_AT, ZoneOffset.UTC));
    }

    private Document rawAuditDocument(String eventId) {
        Document document = mongoTemplate.getCollection("audit_events")
                .find(new Document("event_id", eventId))
                .first();
        assertThat(document).isNotNull();
        return document;
    }

    private static void assertIndex(List<IndexInfo> indexes, String name, boolean unique) {
        assertThat(findIndex(indexes, name).isUnique()).isEqualTo(unique);
    }

    private static IndexInfo findIndex(List<IndexInfo> indexes, String name) {
        return indexes.stream()
                .filter(index -> name.equals(index.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing Mongo index: " + name));
    }

    private static AuditOrderCreatedEvent orderCreatedEvent(UUID eventId, UUID orderId) {
        return new AuditOrderCreatedEvent(
                eventId,
                "OrderCreated",
                1,
                orderId,
                ORDER_OCCURRED_AT,
                "trace-mongo-order-it",
                "corr-mongo-order-it",
                orderId,
                CUSTOMER_ID,
                new Document()
                        .append("eventId", eventId.toString())
                        .append("eventType", "OrderCreated")
                        .append("eventVersion", 1)
                        .append("aggregateId", orderId.toString())
                        .append("occurredAt", ORDER_OCCURRED_AT.toString())
                        .append("traceId", "trace-mongo-order-it")
                        .append("correlationId", "corr-mongo-order-it")
                        .append("data", new Document()
                                .append("orderId", orderId.toString())
                                .append("customerId", CUSTOMER_ID)
                                .append("status", "CREATED")
                                .append("subtotalAmount", "42.9900")
                                .append("totalAmount", "42.9900")
                                .append("currency", "USD")
                                .append("items", List.of(new Document()
                                        .append("itemNumber", 1)
                                        .append("productId", "55555555-5555-5555-5555-555555555555")
                                        .append("productSku", "MONGO-ROUND-TRIP-1")
                                        .append("productName", "Mongo Round Trip Product")
                                        .append("quantity", 2)
                                        .append("unitPriceAmount", "21.4950")
                                        .append("currency", "USD")
                                        .append("lineTotalAmount", "42.9900")))));
    }

    private static AuditPaymentResultEvent paymentResultEvent(UUID eventId, UUID orderId, UUID paymentId) {
        return new AuditPaymentResultEvent(
                eventId,
                "PaymentSucceeded",
                1,
                orderId,
                PAYMENT_OCCURRED_AT,
                "trace-mongo-payment-it",
                "corr-mongo-payment-it",
                orderId,
                paymentId,
                CUSTOMER_ID,
                "SUCCEEDED",
                new Document()
                        .append("eventId", eventId.toString())
                        .append("eventType", "PaymentSucceeded")
                        .append("eventVersion", 1)
                        .append("aggregateId", orderId.toString())
                        .append("occurredAt", PAYMENT_OCCURRED_AT.toString())
                        .append("traceId", "trace-mongo-payment-it")
                        .append("correlationId", "corr-mongo-payment-it")
                        .append("data", new Document()
                                .append("paymentId", paymentId.toString())
                                .append("orderId", orderId.toString())
                                .append("customerId", CUSTOMER_ID)
                                .append("amount", "42.9900")
                                .append("currency", "USD")
                                .append("paymentStatus", "SUCCEEDED")
                                .append("providerAttemptId", "66666666-6666-6666-6666-666666666666")
                                .append("providerAttemptOutcome", "SUCCEEDED")
                                .append("providerReference", "provider-reference-mongo-it")
                                .append("failureReason", null)
                                .append("orderCreatedEventId", "77777777-7777-7777-7777-777777777777")));
    }

    private static NotificationRouteRequest routeRequest(
            String eventId,
            String eventType,
            String paymentId) {
        return new NotificationRouteRequest(
                eventId,
                eventType,
                1,
                "order-" + eventId,
                CUSTOMER_ID,
                paymentId,
                PAYMENT_OCCURRED_AT,
                "trace-delivery-mongo-it",
                "corr-delivery-mongo-it");
    }

    private static NotificationDeliveryDocument delivery(
            String eventId,
            NotificationChannel channel,
            NotificationDeliveryStatus status) {
        return new NotificationDeliveryDocument(
                eventId,
                channel,
                "PaymentSucceeded",
                1,
                "order-" + eventId,
                CUSTOMER_ID,
                "payment-" + eventId,
                status,
                status == NotificationDeliveryStatus.PENDING ? 0 : 1,
                status == NotificationDeliveryStatus.PENDING ? null : DELIVERY_ATTEMPTED_AT,
                status == NotificationDeliveryStatus.FAILED ? "adapter_unavailable" : null,
                PAYMENT_OCCURRED_AT,
                DELIVERY_CREATED_AT.minusSeconds(60),
                DELIVERY_UPDATED_AT,
                "trace-delivery-mongo-it",
                "corr-delivery-mongo-it");
    }
}
