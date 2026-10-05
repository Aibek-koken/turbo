package com.kora.ecommerce.auditnotification.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaAdmin;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(KafkaDeadLetterTopicDepthProperties.class)
class KafkaDeadLetterTopicDepthConfiguration {

    @Bean
    KafkaDeadLetterTopicDepthReader kafkaDeadLetterTopicDepthReader(KafkaAdmin kafkaAdmin) {
        return new AdminClientKafkaDeadLetterTopicDepthReader(kafkaAdmin::getConfigurationProperties);
    }

    @Bean
    KafkaDeadLetterTopicDepthMonitor kafkaDeadLetterTopicDepthMonitor(
            KafkaDeadLetterTopicDepthProperties properties,
            KafkaDeadLetterTopicDepthReader depthReader,
            MeterRegistry meterRegistry) {
        KafkaDeadLetterTopicDepthMonitor monitor = new KafkaDeadLetterTopicDepthMonitor(properties, depthReader);
        monitor.bindTo(meterRegistry);
        return monitor;
    }
}
