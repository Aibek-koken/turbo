package com.kora.ecommerce.payment.messaging;

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

import com.kora.ecommerce.payment.persistence.OutboxEventRepository;
import com.kora.ecommerce.payment.persistence.PaymentAttemptRepository;
import com.kora.ecommerce.payment.persistence.PaymentRepository;
import com.kora.ecommerce.payment.persistence.PaymentStatus;
import com.kora.ecommerce.payment.persistence.ProcessedEventRepository;
import com.kora.ecommerce.payment.provider.PaymentProviderClient;
import com.kora.ecommerce.payment.provider.PaymentProviderResult;
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
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
class OrderCreatedKafkaIT {

    private static final String RUN_ID = UUID.randomUUID().toString().replace("-", "");
    private static final String ORDER_CREATED_TOPIC = "it.payment.order-created." + RUN_ID;
    private static final String ORDER_CREATED_DLT_TOPIC = ORDER_CREATED_TOPIC + ".DLT";
    private static final String GROUP_ID = "payment-kafka-it-" + RUN_ID;

    @Container
    private static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("payment_kafka_it")
            .withUsername("payment_kafka_it")
            .withPassword("payment_kafka_it");

    @DynamicPropertySource
    static void integrationProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.kafka.listener.missing-topics-fatal", () -> "false");
        registry.add("payment.kafka.order-created.topic", () -> ORDER_CREATED_TOPIC);
        registry.add("payment.kafka.order-created.group-id", () -> GROUP_ID);
        registry.add("payment.kafka.order-created.enabled", () -> "true");
        registry.add("payment.kafka.order-created.retry.max-attempts", () -> "1");
        registry.add("payment.kafka.order-created.retry.backoff", () -> "PT0S");
        registry.add("payment.kafka.order-created.retry.dead-letter-topic", () -> ORDER_CREATED_DLT_TOPIC);
    }

    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Autowired
    private PaymentRepository payments;

    @Autowired
    private PaymentAttemptRepository attempts;

    @Autowired
    private OutboxEventRepository outboxEvents;

    @Autowired
    private ProcessedEventRepository processedEvents;

    @Test
    void listenerContainerPersistsValidDeliveryOnceAndRoutesMalformedPayloadToDlt() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String payload = orderCreatedPayload(eventId, orderId);

        send(ORDER_CREATED_TOPIC, orderId.toString(), payload);
        send(ORDER_CREATED_TOPIC, orderId.toString(), payload);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            var paymentOptional = payments.findByOrderId(orderId);
            assertThat(paymentOptional).isPresent();
            var payment = paymentOptional.get();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
            assertThat(payment.getSourceEventId()).isEqualTo(eventId);
            assertThat(attempts.countByPaymentId(payment.getId())).isEqualTo(1);
            assertThat(processedEvents.findByConsumerNameAndEventId(GROUP_ID, eventId)).isPresent();
            assertThat(outboxEvents.findByAggregateIdOrderByOccurredAtAscIdAsc(orderId))
                    .singleElement()
                    .satisfies(outboxEvent -> {
                        assertThat(outboxEvent.getEventType()).isEqualTo("PaymentSucceeded");
                        assertThat(outboxEvent.getPayload()).containsEntry("eventVersion", 1);
                    });
        });

        long paymentCount = payments.count();
        long attemptCount = attempts.count();
        long outboxCount = outboxEvents.count();
        long processedCount = processedEvents.count();

        send(ORDER_CREATED_TOPIC, "poison-order", "{bad-json");

        ConsumerRecord<String, String> deadLetterRecord = readDeadLetterRecord(ORDER_CREATED_DLT_TOPIC);
        assertThat(deadLetterRecord.key()).isEqualTo("poison-order");
        assertThat(deadLetterRecord.value()).isEqualTo("{bad-json");
        assertThat(payments.count()).isEqualTo(paymentCount);
        assertThat(attempts.count()).isEqualTo(attemptCount);
        assertThat(outboxEvents.count()).isEqualTo(outboxCount);
        assertThat(processedEvents.count()).isEqualTo(processedCount);
    }

    private void send(String topic, String key, String payload) throws Exception {
        kafkaTemplate.send(topic, key, payload).get(10, TimeUnit.SECONDS);
    }

    private static ConsumerRecord<String, String> readDeadLetterRecord(String topic) {
        List<ConsumerRecord<String, String>> records = new ArrayList<>();
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "payment-dlt-reader-" + RUN_ID + "-" + UUID.randomUUID());
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
                  "traceId": "trace-payment-it",
                  "correlationId": "corr-payment-it",
                  "data": {
                    "orderId": "%s",
                    "customerId": "customer-payment-it",
                    "status": "CREATED",
                    "subtotalAmount": 42.9900,
                    "totalAmount": 42.9900,
                    "currency": "USD",
                    "createdAt": "2026-10-04T10:00:00Z",
                    "items": [
                      {
                        "itemNumber": 1,
                        "productId": "44444444-4444-4444-4444-444444444444",
                        "productSku": "SKU-IT-1",
                        "productName": "Kafka IT Product",
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
        @Primary
        PaymentProviderClient testPaymentProviderClient() {
            return request -> PaymentProviderResult.succeeded("provider-it-" + request.providerRequestId());
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
