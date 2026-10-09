package com.kora.ecommerce.auditnotification.notification;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface NotificationDeliveryRepository extends MongoRepository<NotificationDeliveryDocument, String> {

    Optional<NotificationDeliveryDocument> findByEventIdAndChannel(
            String eventId,
            NotificationChannel channel);

    List<NotificationDeliveryDocument> findByEventId(String eventId);

    List<NotificationDeliveryDocument> findByOrderId(String orderId);

    List<NotificationDeliveryDocument> findByOrderIdAndCustomerId(String orderId, String customerId);

    List<NotificationDeliveryDocument> findByCustomerId(String customerId);

    List<NotificationDeliveryDocument> findByStatus(NotificationDeliveryStatus status);

    List<NotificationDeliveryDocument> findByChannelAndStatus(
            NotificationChannel channel,
            NotificationDeliveryStatus status);

    List<NotificationDeliveryDocument> findByChannelAndStatus(
            NotificationChannel channel,
            NotificationDeliveryStatus status,
            Pageable pageable);
}
