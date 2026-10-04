package com.kora.ecommerce.order.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderItemRepository extends JpaRepository<OrderItemEntity, UUID> {

    List<OrderItemEntity> findByOrder_IdOrderByItemNumberAsc(UUID orderId);

    @Query("""
            select item
            from OrderItemEntity item
            join fetch item.order orderEntity
            where orderEntity.id in :orderIds
            order by orderEntity.createdAt desc, orderEntity.id desc, item.itemNumber asc
            """)
    List<OrderItemEntity> findForOrdersOrdered(
            @Param("orderIds") Collection<UUID> orderIds);
}
