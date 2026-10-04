package com.kora.ecommerce.order.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {
}
