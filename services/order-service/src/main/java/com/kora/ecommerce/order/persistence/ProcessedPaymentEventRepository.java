package com.kora.ecommerce.order.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedPaymentEventRepository
        extends JpaRepository<ProcessedPaymentEventEntity, ProcessedPaymentEventId> {
}
