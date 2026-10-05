package com.kora.ecommerce.auditnotification.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class KafkaDeadLetterTopicDepthMonitorTest {

    @Test
    void registersFixedTopicGaugesAndRefreshesOnceWithinInterval() {
        KafkaDeadLetterTopicDepthProperties properties = properties(
                newTopic("payment-service", "ecommerce.order.events.DLT"),
                newTopic("audit-notification-service", "ecommerce.order.events.audit.DLT"));
        properties.setRefreshInterval(Duration.ofMinutes(1));
        FakeDepthReader depthReader = new FakeDepthReader();
        depthReader.enqueue(Map.of(
                "ecommerce.order.events.DLT", 4L,
                "ecommerce.order.events.audit.DLT", 2L));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        KafkaDeadLetterTopicDepthMonitor monitor =
                new KafkaDeadLetterTopicDepthMonitor(properties, depthReader);
        monitor.bindTo(registry);

        assertThat(gauge(registry, "payment-service", "ecommerce.order.events.DLT")).isEqualTo(4.0);
        assertThat(gauge(registry, "audit-notification-service", "ecommerce.order.events.audit.DLT"))
                .isEqualTo(2.0);
        assertThat(depthReader.calls).isEqualTo(1);
        assertThat(depthReader.lastTopics)
                .containsExactly("ecommerce.order.events.DLT", "ecommerce.order.events.audit.DLT");
        assertThat(depthReader.lastTimeout).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void returnsZeroDepthWhenKafkaReadFails() {
        KafkaDeadLetterTopicDepthProperties properties = properties(
                newTopic("order-service", "ecommerce.payment.events.DLT"));
        FakeDepthReader depthReader = new FakeDepthReader();
        depthReader.failure = new IllegalStateException("broker unavailable");
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        KafkaDeadLetterTopicDepthMonitor monitor =
                new KafkaDeadLetterTopicDepthMonitor(properties, depthReader);
        monitor.bindTo(registry);

        assertThat(gauge(registry, "order-service", "ecommerce.payment.events.DLT")).isZero();
        assertThat(depthReader.calls).isEqualTo(1);
    }

    private static KafkaDeadLetterTopicDepthProperties properties(
            KafkaDeadLetterTopicDepthProperties.Topic... topics) {
        KafkaDeadLetterTopicDepthProperties properties = new KafkaDeadLetterTopicDepthProperties();
        properties.setTopics(List.of(topics));
        return properties;
    }

    private static KafkaDeadLetterTopicDepthProperties.Topic newTopic(String service, String topic) {
        KafkaDeadLetterTopicDepthProperties.Topic configuredTopic =
                new KafkaDeadLetterTopicDepthProperties.Topic();
        configuredTopic.setService(service);
        configuredTopic.setTopic(topic);
        return configuredTopic;
    }

    private static double gauge(SimpleMeterRegistry registry, String service, String topic) {
        return registry.get(KafkaDeadLetterTopicDepthMonitor.KAFKA_DLT_DEPTH)
                .tag("service", service)
                .tag("topic", topic)
                .gauge()
                .value();
    }

    private static class FakeDepthReader implements KafkaDeadLetterTopicDepthReader {

        private final Queue<Map<String, Long>> responses = new ArrayDeque<>();
        private int calls;
        private List<String> lastTopics = List.of();
        private Duration lastTimeout = Duration.ZERO;
        private RuntimeException failure;

        void enqueue(Map<String, Long> depths) {
            responses.add(new LinkedHashMap<>(depths));
        }

        @Override
        public Map<String, Long> retainedRecords(Collection<String> topics, Duration timeout) {
            calls++;
            lastTopics = List.copyOf(topics);
            lastTimeout = timeout;
            if (failure != null) {
                throw failure;
            }
            return responses.isEmpty() ? Map.of() : responses.remove();
        }
    }
}
