package com.kora.ecommerce.payment.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {

    boolean existsByConsumerNameAndEventId(String consumerName, UUID eventId);

    Optional<ProcessedEvent> findByConsumerNameAndEventId(String consumerName, UUID eventId);

    @Modifying
    @Query("""
            delete from ProcessedEvent event
            where event.consumerName = :consumerName
                and event.eventId = :eventId
            """)
    int deleteByConsumerNameAndEventId(
            @Param("consumerName") String consumerName,
            @Param("eventId") UUID eventId);
}
