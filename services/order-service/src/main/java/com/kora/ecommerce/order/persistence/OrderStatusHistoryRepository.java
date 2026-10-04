package com.kora.ecommerce.order.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistoryEntity, UUID> {

    List<OrderStatusHistoryEntity> findByOrder_IdOrderByChangedAtAsc(UUID orderId);
}
