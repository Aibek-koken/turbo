package com.kora.ecommerce.auditnotification.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface AuditEventRepository extends MongoRepository<AuditEventDocument, String> {

    Optional<AuditEventDocument> findByEventId(String eventId);

    boolean existsByEventId(String eventId);

    List<AuditEventDocument> findByEventType(String eventType);

    List<AuditEventDocument> findByAggregateId(String aggregateId);

    List<AuditEventDocument> findByOrderId(String orderId);

    List<AuditEventDocument> findByPaymentId(String paymentId);

    List<AuditEventDocument> findByCorrelationId(String correlationId);

    List<AuditEventDocument> findByOccurredAtBetween(Instant fromInclusive, Instant toExclusive);
}
