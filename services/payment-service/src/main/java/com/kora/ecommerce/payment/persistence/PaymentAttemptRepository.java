package com.kora.ecommerce.payment.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {

    List<PaymentAttempt> findByPaymentIdOrderByRequestedAtDescIdDesc(UUID paymentId);

    long countByPaymentId(UUID paymentId);
}
