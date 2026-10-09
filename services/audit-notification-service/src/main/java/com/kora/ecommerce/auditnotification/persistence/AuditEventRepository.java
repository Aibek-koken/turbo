package com.kora.ecommerce.auditnotification.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AuditEventRepository extends MongoRepository<AuditEventDocument, String> {

    Optional<AuditEventDocument> findByEventId(String eventId);

    boolean existsByEventId(String eventId);

    List<AuditEventDocument> findByEventType(String eventType);

    List<AuditEventDocument> findByAggregateId(String aggregateId);

    List<AuditEventDocument> findByOrderId(String orderId);

    Page<AuditEventDocument> findByOrderId(String orderId, Pageable pageable);

    List<AuditEventDocument> findByPaymentId(String paymentId);

    List<AuditEventDocument> findByCorrelationId(String correlationId);

    List<AuditEventDocument> findByOccurredAtBetween(Instant fromInclusive, Instant toExclusive);
}
