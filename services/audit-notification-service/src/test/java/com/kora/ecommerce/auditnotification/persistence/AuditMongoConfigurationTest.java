package com.kora.ecommerce.auditnotification.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.kora.ecommerce.auditnotification.notification.NotificationChannel;
import com.kora.ecommerce.auditnotification.notification.NotificationDeliveryRepository;
import com.kora.ecommerce.auditnotification.notification.NotificationDeliveryStatus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.mongo.MongoProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.mock.env.MockEnvironment;

class AuditMongoConfigurationTest {

    @Test
    void bindsAuditMongoConnectionProperties() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.data.mongodb.host", "localhost")
                .withProperty("spring.data.mongodb.port", "27017")
                .withProperty("spring.data.mongodb.database", "audit")
                .withProperty("spring.data.mongodb.username", "ecommerce")
                .withProperty("spring.data.mongodb.password", "change-me-local-mongo")
                .withProperty("spring.data.mongodb.authentication-database", "admin")
                .withProperty("spring.data.mongodb.auto-index-creation", "true");

        MongoProperties properties = Binder.get(environment)
                .bind("spring.data.mongodb", Bindable.of(MongoProperties.class))
                .orElseThrow(() -> new AssertionError("Mongo properties were not bound"));

        assertThat(properties.getHost()).isEqualTo("localhost");
        assertThat(properties.getPort()).isEqualTo(27017);
        assertThat(properties.getDatabase()).isEqualTo("audit");
        assertThat(properties.getUsername()).isEqualTo("ecommerce");
        assertThat(properties.getPassword()).containsExactly("change-me-local-mongo".toCharArray());
        assertThat(properties.getAuthenticationDatabase()).isEqualTo("admin");
        assertThat(properties.isAutoIndexCreation()).isTrue();
    }

    @Test
    void mongoConfigurationEnablesAuditRepositoryPackage() {
        EnableMongoRepositories annotation =
                AuditMongoConfiguration.class.getAnnotation(EnableMongoRepositories.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.basePackageClasses())
                .containsExactlyInAnyOrder(AuditEventRepository.class, NotificationDeliveryRepository.class);
    }

    @Test
    void repositoryDeclaresEventReplayAndSearchQueries() throws Exception {
        assertThat(MongoRepository.class).isAssignableFrom(AuditEventRepository.class);
        assertThat(AuditEventRepository.class.getMethod("findByEventId", String.class).getReturnType())
                .isEqualTo(Optional.class);
        assertThat(AuditEventRepository.class.getMethod("existsByEventId", String.class).getReturnType())
                .isEqualTo(boolean.class);
        assertThat(AuditEventRepository.class.getMethod("findByEventType", String.class).getReturnType())
                .isEqualTo(List.class);
        assertThat(AuditEventRepository.class.getMethod("findByAggregateId", String.class).getReturnType())
                .isEqualTo(List.class);
        assertThat(AuditEventRepository.class.getMethod("findByOrderId", String.class).getReturnType())
                .isEqualTo(List.class);
        assertThat(AuditEventRepository.class.getMethod("findByPaymentId", String.class).getReturnType())
                .isEqualTo(List.class);
        assertThat(AuditEventRepository.class.getMethod("findByCorrelationId", String.class).getReturnType())
                .isEqualTo(List.class);
        assertThat(AuditEventRepository.class.getMethod(
                        "findByOccurredAtBetween", Instant.class, Instant.class).getReturnType())
                .isEqualTo(List.class);
    }

    @Test
    void repositoryDeclaresNotificationDeliveryReplayAndSearchQueries() throws Exception {
        assertThat(MongoRepository.class).isAssignableFrom(NotificationDeliveryRepository.class);
        assertThat(NotificationDeliveryRepository.class.getMethod(
                        "findByEventIdAndChannel", String.class, NotificationChannel.class).getReturnType())
                .isEqualTo(Optional.class);
        assertThat(NotificationDeliveryRepository.class.getMethod("findByEventId", String.class).getReturnType())
                .isEqualTo(List.class);
        assertThat(NotificationDeliveryRepository.class.getMethod("findByOrderId", String.class).getReturnType())
                .isEqualTo(List.class);
        assertThat(NotificationDeliveryRepository.class.getMethod("findByCustomerId", String.class).getReturnType())
                .isEqualTo(List.class);
        assertThat(NotificationDeliveryRepository.class.getMethod(
                        "findByStatus", NotificationDeliveryStatus.class).getReturnType())
                .isEqualTo(List.class);
        assertThat(NotificationDeliveryRepository.class.getMethod(
                        "findByChannelAndStatus",
                        NotificationChannel.class,
                        NotificationDeliveryStatus.class).getReturnType())
                .isEqualTo(List.class);
    }
}
