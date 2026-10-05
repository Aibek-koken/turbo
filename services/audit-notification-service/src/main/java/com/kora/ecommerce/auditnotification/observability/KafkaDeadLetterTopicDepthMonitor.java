package com.kora.ecommerce.auditnotification.observability;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaDeadLetterTopicDepthMonitor {

    public static final String KAFKA_DLT_DEPTH = "ecommerce.kafka.dead.letter.topic.depth";

    private static final Logger log = LoggerFactory.getLogger(KafkaDeadLetterTopicDepthMonitor.class);
    private static final String DESCRIPTION =
            "Retained Kafka records across partitions for fixed ecommerce dead-letter topics.";

    private final KafkaDeadLetterTopicDepthProperties properties;
    private final KafkaDeadLetterTopicDepthReader depthReader;
    private final Clock clock;
    private final List<String> topicNames;
    private final Map<String, Long> depths = new ConcurrentHashMap<>();
    private Instant nextRefresh = Instant.EPOCH;

    public KafkaDeadLetterTopicDepthMonitor(
            KafkaDeadLetterTopicDepthProperties properties,
            KafkaDeadLetterTopicDepthReader depthReader) {
        this(properties, depthReader, Clock.systemUTC());
    }

    KafkaDeadLetterTopicDepthMonitor(
            KafkaDeadLetterTopicDepthProperties properties,
            KafkaDeadLetterTopicDepthReader depthReader,
            Clock clock) {
        this.properties = properties;
        this.depthReader = depthReader;
        this.clock = clock;
        this.topicNames = properties.getTopics().stream()
                .map(KafkaDeadLetterTopicDepthProperties.Topic::getTopic)
                .toList();
        this.topicNames.forEach(topic -> depths.put(topic, 0L));
    }

    public void bindTo(MeterRegistry meterRegistry) {
        if (!properties.isEnabled()) {
            return;
        }
        for (KafkaDeadLetterTopicDepthProperties.Topic topic : properties.getTopics()) {
            Gauge.builder(KAFKA_DLT_DEPTH, topic.getTopic(), this::depthFor)
                    .description(DESCRIPTION)
                    .baseUnit("records")
                    .tag("service", topic.getService())
                    .tag("topic", topic.getTopic())
                    .register(meterRegistry);
        }
    }

    double depthFor(String topic) {
        if (!properties.isEnabled()) {
            return 0.0;
        }
        refreshIfNeeded();
        return depths.getOrDefault(topic, 0L).doubleValue();
    }

    private synchronized void refreshIfNeeded() {
        Instant now = clock.instant();
        if (now.isBefore(nextRefresh)) {
            return;
        }
        try {
            Map<String, Long> refreshedDepths = depthReader.retainedRecords(topicNames, properties.getTimeout());
            for (String topic : topicNames) {
                depths.put(topic, Math.max(0L, refreshedDepths.getOrDefault(topic, 0L)));
            }
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            topicNames.forEach(topic -> depths.put(topic, 0L));
            log.warn(
                    "kafka_dead_letter_depth_refresh_failed topics={} failure={}",
                    topicNames,
                    exception.toString());
        } finally {
            nextRefresh = now.plus(properties.getRefreshInterval());
        }
    }
}
