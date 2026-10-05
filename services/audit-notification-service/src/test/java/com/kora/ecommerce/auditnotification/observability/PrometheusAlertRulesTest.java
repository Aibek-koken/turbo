package com.kora.ecommerce.auditnotification.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class PrometheusAlertRulesTest {

    @Test
    void prometheusLoadsLocalAlertRuleFile() throws IOException {
        String prometheusConfig = Files.readString(projectPath("infra/observability/prometheus.yml"));

        assertThat(prometheusConfig).contains("rule_files:");
        assertThat(prometheusConfig).contains("/etc/prometheus/rules/alert-rules.yml");
    }

    @Test
    void alertRulesParseAndCoverExpectedOperationalSignals() throws IOException {
        String alertRules = Files.readString(projectPath("infra/observability/alert-rules.yml"));
        Map<String, Object> parsedRules = new Yaml().load(alertRules);

        assertThat(parsedRules).containsKey("groups");
        assertThat(alertRules)
                .contains("alert: EcommerceServiceDown")
                .contains("alert: EcommerceSustainedHttpServerErrors")
                .contains("alert: EcommercePaymentFailures")
                .contains("alert: EcommerceNotificationFailures")
                .contains("alert: EcommerceKafkaConsumerFailures")
                .contains("alert: EcommerceDeadLetterTopicRetainedRecords")
                .contains("up{job=~\"gateway-service|catalog-service|order-service|payment-service|audit-notification-service\"} == 0")
                .contains("rate(http_server_requests_seconds_count{status=~\"5..\"}[5m])")
                .contains("ecommerce_payment_terminal_outcomes_total{service=\"payment-service\",event_type=\"PaymentFailed\",outcome=\"failed\"}")
                .contains("ecommerce_notification_deliveries_total{service=\"audit-notification-service\",outcome=~\"failed|exhausted\"}")
                .contains("ecommerce_kafka_consumer_events_total{outcome=~\"failure|rejected\"}")
                .contains("ecommerce_kafka_dead_letter_topic_depth");
    }

    private static Path projectPath(String relativePath) {
        Path fromExecutionRoot = Path.of(relativePath);
        if (Files.exists(fromExecutionRoot)) {
            return fromExecutionRoot;
        }
        return Path.of("../..").resolve(relativePath).normalize();
    }
}
