package com.kora.ecommerce.auditnotification.persistence;

import com.kora.ecommerce.auditnotification.notification.NotificationDeliveryRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "audit-notification.mongodb.repositories",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@EnableMongoRepositories(basePackageClasses = {
        AuditEventRepository.class,
        NotificationDeliveryRepository.class
})
public class AuditMongoConfiguration {
}
