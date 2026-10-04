package com.kora.ecommerce.order.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {

    Page<OrderEntity> findByCustomerIdOrderByCreatedAtDescIdDesc(String customerId, Pageable pageable);

    Optional<OrderEntity> findByIdAndCustomerId(UUID id, String customerId);
}
