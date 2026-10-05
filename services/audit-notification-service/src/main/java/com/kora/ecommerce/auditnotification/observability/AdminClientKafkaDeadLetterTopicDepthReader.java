package com.kora.ecommerce.auditnotification.observability;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.common.TopicPartition;

final class AdminClientKafkaDeadLetterTopicDepthReader implements KafkaDeadLetterTopicDepthReader {

    private final Supplier<Map<String, Object>> configurationSupplier;

    AdminClientKafkaDeadLetterTopicDepthReader(Supplier<Map<String, Object>> configurationSupplier) {
        this.configurationSupplier = configurationSupplier;
    }

    @Override
    public Map<String, Long> retainedRecords(Collection<String> topics, Duration timeout) throws Exception {
        ArrayList<String> topicNames = new ArrayList<>(topics);
        if (topicNames.isEmpty()) {
            return Map.of();
        }

        AdminClient adminClient = AdminClient.create(new LinkedHashMap<>(configurationSupplier.get()));
        try {
            Map<String, TopicDescription> descriptions = adminClient.describeTopics(topicNames)
                    .allTopicNames()
                    .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            Map<TopicPartition, OffsetSpec> latestSpecs = new LinkedHashMap<>();
            Map<TopicPartition, OffsetSpec> beginningSpecs = new LinkedHashMap<>();
            Map<TopicPartition, String> topicByPartition = new LinkedHashMap<>();
            for (TopicDescription description : descriptions.values()) {
                description.partitions().forEach(partition -> {
                    TopicPartition topicPartition = new TopicPartition(description.name(), partition.partition());
                    latestSpecs.put(topicPartition, OffsetSpec.latest());
                    beginningSpecs.put(topicPartition, OffsetSpec.earliest());
                    topicByPartition.put(topicPartition, description.name());
                });
            }

            Map<String, Long> depths = zeroDepths(topicNames);
            if (latestSpecs.isEmpty()) {
                return depths;
            }

            Map<TopicPartition, ListOffsetsResultInfo> latestOffsets = adminClient.listOffsets(latestSpecs)
                    .all()
                    .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            Map<TopicPartition, ListOffsetsResultInfo> beginningOffsets = adminClient.listOffsets(beginningSpecs)
                    .all()
                    .get(timeout.toMillis(), TimeUnit.MILLISECONDS);

            for (Map.Entry<TopicPartition, ListOffsetsResultInfo> entry : latestOffsets.entrySet()) {
                TopicPartition topicPartition = entry.getKey();
                ListOffsetsResultInfo beginning = beginningOffsets.get(topicPartition);
                if (beginning == null) {
                    continue;
                }
                long retainedRecords = Math.max(0L, entry.getValue().offset() - beginning.offset());
                depths.merge(topicByPartition.get(topicPartition), retainedRecords, Long::sum);
            }
            return depths;
        } finally {
            adminClient.close(timeout);
        }
    }

    private static Map<String, Long> zeroDepths(Collection<String> topicNames) {
        Map<String, Long> depths = new LinkedHashMap<>();
        topicNames.forEach(topic -> depths.put(topic, 0L));
        return depths;
    }
}
