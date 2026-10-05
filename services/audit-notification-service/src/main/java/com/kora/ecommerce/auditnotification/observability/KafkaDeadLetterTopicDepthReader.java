package com.kora.ecommerce.auditnotification.observability;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;

interface KafkaDeadLetterTopicDepthReader {

    Map<String, Long> retainedRecords(Collection<String> topics, Duration timeout) throws Exception;
}
