package com.kora.ecommerce.order.persistence;

public enum OrderStatus {
    CREATED,
    PAYMENT_PENDING,
    PAID,
    PAYMENT_FAILED,
    CANCELLED
}
