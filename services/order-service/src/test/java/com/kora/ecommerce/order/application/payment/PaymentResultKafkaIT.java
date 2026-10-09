package com.kora.ecommerce.order.application.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.fail;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.kora.ecommerce.order.persistence.OrderEntity;
import com.kora.ecommerce.order.persistence.OrderRepository;
import com.kora.ecommerce.order.persistence.OrderStatus;
import com.kora.ecommerce.order.persistence.OrderStatusHistoryEntity;
import com.kora.ecommerce.order.persistence.OrderStatusHistoryRepository;
import com.kora.ecommerce.order.persistence.ProcessedPaymentEventId;
import com.kora.ecommerce.order.persistence.ProcessedPaymentEventRepository;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
class PaymentResultKafkaIT {

    private static final String RUN_ID = UUID.randomUUID().toString().replace("-", "");
    private static final String PAYMENT_RESULTS_TOPIC = "it.order.payment-results." + RUN_ID;
    private static final String PAYMENT_RESULTS_DLT_TOPIC = PAYMENT_RESULTS_TOPIC + ".DLT";
    private static final String GROUP_ID = "order-kafka-it-" + RUN_ID;

    @Container
    private static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("order_kafka_it")
            .withUsername("order_kafka_it")
            .withPassword("order_kafka_it");

    @DynamicPropertySource
    static void integrationProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.kafka.consumer.group-id", () -> GROUP_ID);
        registry.add("spring.kafka.listener.missing-topics-fatal", () -> "false");
        registry.add("order.payment-results.topic", () -> PAYMENT_RESULTS_TOPIC);
        registry.add("order.payment-results.group-id", () -> GROUP_ID);
        registry.add("order.payment-results.consumer-enabled", () -> "true");
        registry.add("order.payment-results.retry.max-attempts", () -> "1");
        registry.add("order.payment-results.retry.backoff", () -> "PT0S");
        registry.add("order.payment-results.retry.dead-letter-topic", () -> PAYMENT_RESULTS_DLT_TOPIC);
    }

    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Autowired
    private OrderRepository orders;

    @Autowired
    private OrderStatusHistoryRepository orderStatusHistory;

    @Autowired
    private ProcessedPaymentEventRepository processedPaymentEvents;

    @Test
    void listenerContainerAppliesPaymentResultOnceAndRoutesMalformedPayloadToDlt() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        seedOrder(orderId);

        String payload = paymentSucceededPayload(eventId, orderId, paymentId);
        send(PAYMENT_RESULTS_TOPIC, orderId.toString(), payload);
        send(PAYMENT_RESULTS_TOPIC, orderId.toString(), payload);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(orders.findById(orderId))
                    .get()
                    .extracting(OrderEntity::getStatus)
                    .isEqualTo(OrderStatus.PAID);
            assertThat(processedPaymentEvents.findById(ProcessedPaymentEventId.of(GROUP_ID, eventId)))
                    .isPresent();
            assertThat(orderStatusHistory.findByOrder_IdOrderByChangedAtAsc(orderId))
                    .extracting(OrderStatusHistoryEntity::getStatus)
                    .containsExactly(
                            OrderStatus.CREATED,
                            OrderStatus.PAYMENT_PENDING,
                            OrderStatus.PAID);
        });

        long processedCount = processedPaymentEvents.count();
        long historyCount = orderStatusHistory.count();

        send(PAYMENT_RESULTS_TOPIC, "poison-payment-result", "{bad-json");

        ConsumerRecord<String, String> deadLetterRecord = readDeadLetterRecord(PAYMENT_RESULTS_DLT_TOPIC);
        assertThat(deadLetterRecord.key()).isEqualTo("poison-payment-result");
        assertThat(deadLetterRecord.value()).isEqualTo("{bad-json");
        assertThat(processedPaymentEvents.count()).isEqualTo(processedCount);
        assertThat(orderStatusHistory.count()).isEqualTo(historyCount);
    }

    private void seedOrder(UUID orderId) {
        OrderEntity order = OrderEntity.create(
                orderId,
                "customer-order-kafka-it",
                new BigDecimal("42.9900"),
                new BigDecimal("42.9900"),
                "USD",
                Instant.parse("2026-10-04T10:00:00Z"));
        order.appendStatusHistory(OrderStatus.CREATED, order.getCreatedAt(), "order created for kafka it");
        orders.saveAndFlush(order);
    }

    private void send(String topic, String key, String payload) throws Exception {
        kafkaTemplate.send(topic, key, payload).get(10, TimeUnit.SECONDS);
    }

    private static ConsumerRecord<String, String> readDeadLetterRecord(String topic) {
        List<ConsumerRecord<String, String>> records = new ArrayList<>();
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "order-dlt-reader-" + RUN_ID + "-" + UUID.randomUUID());
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

    private static String paymentSucceededPayload(UUID eventId, UUID orderId, UUID paymentId) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "PaymentSucceeded",
                  "eventVersion": 1,
                  "aggregateId": "%s",
                  "occurredAt": "2026-10-04T10:15:30Z",
                  "traceId": "trace-order-it",
                  "correlationId": "corr-order-it",
                  "data": {
                    "orderId": "%s",
                    "paymentId": "%s"
                  }
                }
                """.formatted(eventId, orderId, orderId, paymentId);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestKafkaConfiguration {

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
