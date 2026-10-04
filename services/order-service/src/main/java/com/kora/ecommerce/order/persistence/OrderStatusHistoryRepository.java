package com.kora.ecommerce.order.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistoryEntity, UUID> {

    List<OrderStatusHistoryEntity> findByOrder_IdOrderByChangedAtAsc(UUID orderId);

    @Query("""
            select history
            from OrderStatusHistoryEntity history
            join fetch history.order orderEntity
            where orderEntity.id in :orderIds
            order by orderEntity.createdAt desc, orderEntity.id desc, history.changedAt asc, history.id asc
            """)
    List<OrderStatusHistoryEntity> findForOrdersOrdered(
            @Param("orderIds") Collection<UUID> orderIds);
}
