package com.kora.ecommerce.auditnotification.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.kora.ecommerce.auditnotification.notification.NotificationChannel;
import com.kora.ecommerce.auditnotification.notification.NotificationDeliveryDocument;
import com.kora.ecommerce.auditnotification.notification.NotificationDeliveryRepository;
import com.kora.ecommerce.auditnotification.notification.NotificationDeliveryStatus;
import com.kora.ecommerce.auditnotification.persistence.AuditEventRepository;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
class AuditKafkaIT {

    private static final String RUN_ID = UUID.randomUUID().toString().replace("-", "");
    private static final String ORDER_CREATED_TOPIC = "it.audit.order-created." + RUN_ID;
    private static final String ORDER_CREATED_DLT_TOPIC = ORDER_CREATED_TOPIC + ".DLT";
    private static final String PAYMENT_RESULTS_TOPIC = "it.audit.payment-results." + RUN_ID;
    private static final String PAYMENT_RESULTS_DLT_TOPIC = PAYMENT_RESULTS_TOPIC + ".DLT";
    private static final String ORDER_CREATED_GROUP_ID = "audit-order-kafka-it-" + RUN_ID;
    private static final String PAYMENT_RESULTS_GROUP_ID = "audit-payment-kafka-it-" + RUN_ID;

    @Container
    private static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));

    @Container
    private static final MongoDBContainer MONGO =
            new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

    @DynamicPropertySource
    static void integrationProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("spring.data.mongodb.auto-index-creation", () -> "true");
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.kafka.listener.missing-topics-fatal", () -> "false");
        registry.add("audit-notification.kafka.order-created.topic", () -> ORDER_CREATED_TOPIC);
        registry.add("audit-notification.kafka.order-created.group-id", () -> ORDER_CREATED_GROUP_ID);
        registry.add("audit-notification.kafka.order-created.enabled", () -> "true");
        registry.add("audit-notification.kafka.order-created.retry.max-attempts", () -> "1");
        registry.add("audit-notification.kafka.order-created.retry.backoff", () -> "PT0S");
        registry.add("audit-notification.kafka.order-created.retry.dead-letter-topic", () -> ORDER_CREATED_DLT_TOPIC);
        registry.add("audit-notification.kafka.payment-results.topic", () -> PAYMENT_RESULTS_TOPIC);
        registry.add("audit-notification.kafka.payment-results.group-id", () -> PAYMENT_RESULTS_GROUP_ID);
        registry.add("audit-notification.kafka.payment-results.enabled", () -> "true");
        registry.add("audit-notification.kafka.payment-results.retry.max-attempts", () -> "1");
        registry.add("audit-notification.kafka.payment-results.retry.backoff", () -> "PT0S");
        registry.add("audit-notification.kafka.payment-results.retry.dead-letter-topic", () -> PAYMENT_RESULTS_DLT_TOPIC);
        registry.add("audit-notification.notifications.email.enabled", () -> "false");
        registry.add("audit-notification.notifications.push.enabled", () -> "false");
        registry.add("audit-notification.observability.dlt-depth.enabled", () -> "false");
    }

    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Autowired
    private AuditEventRepository auditEvents;

    @Autowired
    private NotificationDeliveryRepository notificationDeliveries;

    @Test
    void listenerContainersPersistAuditEventsOnceAndRouteMalformedPayloadsToDlts() throws Exception {
        UUID orderEventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID paymentEventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        send(ORDER_CREATED_TOPIC, orderId.toString(), orderCreatedPayload(orderEventId, orderId));
        send(ORDER_CREATED_TOPIC, orderId.toString(), orderCreatedPayload(orderEventId, orderId));
        send(PAYMENT_RESULTS_TOPIC, orderId.toString(), paymentSucceededPayload(paymentEventId, orderId, paymentId));
        send(PAYMENT_RESULTS_TOPIC, orderId.toString(), paymentSucceededPayload(paymentEventId, orderId, paymentId));

        await().atMost(Duration.ofSeconds(25)).untilAsserted(() -> {
            assertThat(auditEvents.findByEventId(orderEventId.toString()))
                    .get()
                    .satisfies(document -> {
                        assertThat(document.getEventType()).isEqualTo("OrderCreated");
                        assertThat(document.getOrderId()).isEqualTo(orderId.toString());
                        assertThat(document.getSourceTopic()).isEqualTo(ORDER_CREATED_TOPIC);
                    });
            assertThat(auditEvents.findByEventId(paymentEventId.toString()))
                    .get()
                    .satisfies(document -> {
                        assertThat(document.getEventType()).isEqualTo("PaymentSucceeded");
                        assertThat(document.getOrderId()).isEqualTo(orderId.toString());
                        assertThat(document.getPaymentId()).isEqualTo(paymentId.toString());
                        assertThat(document.getSourceTopic()).isEqualTo(PAYMENT_RESULTS_TOPIC);
                    });
            assertThat(auditEvents.count()).isEqualTo(2);
            assertDeliveries(orderEventId, NotificationChannel.EMAIL, NotificationChannel.PUSH);
            assertDeliveries(paymentEventId, NotificationChannel.EMAIL, NotificationChannel.PUSH);
            assertThat(notificationDeliveries.count()).isEqualTo(4);
        });

        long auditEventCount = auditEvents.count();
        long deliveryCount = notificationDeliveries.count();

        send(ORDER_CREATED_TOPIC, "poison-order-audit", "{bad-json");
        send(PAYMENT_RESULTS_TOPIC, "poison-payment-audit", "{bad-json");

        ConsumerRecord<String, String> orderDeadLetterRecord = readDeadLetterRecord(ORDER_CREATED_DLT_TOPIC);
        ConsumerRecord<String, String> paymentDeadLetterRecord = readDeadLetterRecord(PAYMENT_RESULTS_DLT_TOPIC);
        assertThat(orderDeadLetterRecord.key()).isEqualTo("poison-order-audit");
        assertThat(orderDeadLetterRecord.value()).isEqualTo("{bad-json");
        assertThat(paymentDeadLetterRecord.key()).isEqualTo("poison-payment-audit");
        assertThat(paymentDeadLetterRecord.value()).isEqualTo("{bad-json");
        assertThat(auditEvents.count()).isEqualTo(auditEventCount);
        assertThat(notificationDeliveries.count()).isEqualTo(deliveryCount);
    }

    private void send(String topic, String key, String payload) throws Exception {
        kafkaTemplate.send(topic, key, payload).get(10, TimeUnit.SECONDS);
    }

    private void assertDeliveries(UUID eventId, NotificationChannel... expectedChannels) {
        assertThat(notificationDeliveries.findByEventId(eventId.toString()))
                .hasSize(expectedChannels.length)
                .extracting(NotificationDeliveryDocument::getChannel)
                .containsExactlyInAnyOrder(expectedChannels);
        assertThat(notificationDeliveries.findByEventId(eventId.toString()))
                .extracting(NotificationDeliveryDocument::getStatus)
                .containsOnly(NotificationDeliveryStatus.PENDING);
    }

    private static ConsumerRecord<String, String> readDeadLetterRecord(String topic) {
        List<ConsumerRecord<String, String>> records = new ArrayList<>();
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "audit-dlt-reader-" + RUN_ID + "-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
            while (records.isEmpty() && System.nanoTime() < deadline) {
                consumer.poll(Duration.ofMillis(250)).forEach(records::add);
            }
        }

        if (records.isEmpty()) {
            fail("Expected one dead-letter record on " + topic);
        }
        assertThat(records).hasSize(1);
        return records.getFirst();
    }

    private static String orderCreatedPayload(UUID eventId, UUID orderId) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "OrderCreated",
                  "eventVersion": 1,
                  "aggregateId": "%s",
                  "occurredAt": "2026-10-04T10:00:00Z",
                  "traceId": "trace-audit-order-it",
                  "correlationId": "corr-audit-order-it",
                  "data": {
                    "orderId": "%s",
                    "customerId": "customer-audit-it",
                    "status": "CREATED",
                    "subtotalAmount": 42.9900,
                    "totalAmount": 42.9900,
                    "currency": "USD",
                    "createdAt": "2026-10-04T10:00:00Z",
                    "items": [
                      {
                        "itemNumber": 1,
                        "productId": "44444444-4444-4444-4444-444444444444",
                        "productSku": "SKU-AUDIT-IT-1",
                        "productName": "Audit Kafka IT Product",
                        "quantity": 1,
                        "unitPriceAmount": 42.9900,
                        "currency": "USD",
                        "lineTotalAmount": 42.9900
                      }
                    ]
                  }
                }
                """.formatted(eventId, orderId, orderId);
    }

    private static String paymentSucceededPayload(UUID eventId, UUID orderId, UUID paymentId) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "PaymentSucceeded",
                  "eventVersion": 1,
                  "aggregateId": "%s",
                  "occurredAt": "2026-10-04T10:15:30Z",
                  "traceId": "trace-audit-payment-it",
                  "correlationId": "corr-audit-payment-it",
                  "data": {
                    "paymentId": "%s",
                    "orderId": "%s",
                    "customerId": "customer-audit-it",
                    "amount": "42.9900",
                    "currency": "USD",
                    "paymentStatus": "SUCCEEDED",
                    "providerAttemptId": "55555555-5555-5555-5555-555555555555",
                    "providerAttemptOutcome": "SUCCEEDED",
                    "providerReference": "provider-reference-it",
                    "failureReason": null,
                    "orderCreatedEventId": "66666666-6666-6666-6666-666666666666"
                  }
                }
                """.formatted(eventId, orderId, paymentId, orderId);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestKafkaConfiguration {

        @Bean
        NewTopic orderCreatedTopic() {
            return TopicBuilder.name(ORDER_CREATED_TOPIC).partitions(1).replicas(1).build();
        }

        @Bean
        NewTopic orderCreatedDeadLetterTopic() {
            return TopicBuilder.name(ORDER_CREATED_DLT_TOPIC).partitions(1).replicas(1).build();
        }

        @Bean
        NewTopic paymentResultsTopic() {
            return TopicBuilder.name(PAYMENT_RESULTS_TOPIC).partitions(1).replicas(1).build();
        }

        @Bean
        NewTopic paymentResultsDeadLetterTopic() {
            return TopicBuilder.name(PAYMENT_RESULTS_DLT_TOPIC).partitions(1).replicas(1).build();
        }

        @Bean
        JwtDecoder jwtDecoder() {
            Instant issuedAt = Instant.now();
            return token -> new Jwt(
                    token,
                    issuedAt,
                    issuedAt.plusSeconds(300),
                    Map.of("alg", "none"),
                    Map.of("sub", "kafka-it"));
        }
    }
}
