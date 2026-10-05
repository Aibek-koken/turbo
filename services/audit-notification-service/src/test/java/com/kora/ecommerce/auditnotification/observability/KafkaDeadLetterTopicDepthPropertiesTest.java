package com.kora.ecommerce.auditnotification.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class KafkaDeadLetterTopicDepthPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void providesBoundedDefaultsForKnownDeadLetterTopics() {
        contextRunner.run(context -> {
            KafkaDeadLetterTopicDepthProperties properties =
                    context.getBean(KafkaDeadLetterTopicDepthProperties.class);

            assertThat(properties.isEnabled()).isTrue();
            assertThat(properties.getRefreshInterval()).isEqualTo(Duration.ofSeconds(30));
            assertThat(properties.getTimeout()).isEqualTo(Duration.ofSeconds(2));
            assertThat(properties.getTopics())
                    .extracting(KafkaDeadLetterTopicDepthProperties.Topic::getTopic)
                    .containsExactly(
                            "ecommerce.order.events.DLT",
                            "ecommerce.payment.events.DLT",
                            "ecommerce.order.events.audit.DLT",
                            "ecommerce.payment.events.audit.DLT");
            assertThat(properties.getTopics())
                    .extracting(KafkaDeadLetterTopicDepthProperties.Topic::getService)
                    .containsExactly(
                            "payment-service",
                            "order-service",
                            "audit-notification-service",
                            "audit-notification-service");
        });
    }

    @Test
    void bindsEnvironmentDrivenDeadLetterDepthSettings() {
        contextRunner
                .withPropertyValues(
                        "audit-notification.observability.dlt-depth.enabled=false",
                        "audit-notification.observability.dlt-depth.refresh-interval=PT45S",
                        "audit-notification.observability.dlt-depth.timeout=PT0.5S",
                        "audit-notification.observability.dlt-depth.topics[0].service=payment-service",
                        "audit-notification.observability.dlt-depth.topics[0].topic=local.payment.dlt",
                        "audit-notification.observability.dlt-depth.topics[1].service=audit-notification-service",
                        "audit-notification.observability.dlt-depth.topics[1].topic=local.audit.dlt")
                .run(context -> {
                    KafkaDeadLetterTopicDepthProperties properties =
                            context.getBean(KafkaDeadLetterTopicDepthProperties.class);

                    assertThat(properties.isEnabled()).isFalse();
                    assertThat(properties.getRefreshInterval()).isEqualTo(Duration.ofSeconds(45));
                    assertThat(properties.getTimeout()).isEqualTo(Duration.ofMillis(500));
                    assertThat(properties.getTopics())
                            .extracting(KafkaDeadLetterTopicDepthProperties.Topic::getTopic)
                            .containsExactly("local.payment.dlt", "local.audit.dlt");
                });
    }

    @Test
    void rejectsUnboundedOrDuplicateTopicSettings() {
        contextRunner
                .withPropertyValues("audit-notification.observability.dlt-depth.refresh-interval=PT6M")
                .run(context -> assertThat(context).hasFailed());
        contextRunner
                .withPropertyValues("audit-notification.observability.dlt-depth.timeout=PT11S")
                .run(context -> assertThat(context).hasFailed());
        contextRunner
                .withPropertyValues(
                        "audit-notification.observability.dlt-depth.topics[0].service=payment-service",
                        "audit-notification.observability.dlt-depth.topics[0].topic=duplicate.dlt",
                        "audit-notification.observability.dlt-depth.topics[1].service=order-service",
                        "audit-notification.observability.dlt-depth.topics[1].topic=duplicate.dlt")
                .run(context -> assertThat(context).hasFailed());
    }

    @EnableConfigurationProperties(KafkaDeadLetterTopicDepthProperties.class)
    static class PropertiesConfiguration {
    }
}
