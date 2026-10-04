package com.kora.ecommerce.order.application.payment;

public enum PaymentResultHandlingStatus {
    PROCESSED,
    DUPLICATE,
    MISSING_ORDER,
    ALREADY_TERMINAL,
    INVALID_TRANSITION
}
