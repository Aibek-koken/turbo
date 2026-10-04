package com.kora.ecommerce.order.application;

public enum OrderTransitionFailure {
    ORDER_ID_REQUIRED,
    ORDER_NOT_FOUND,
    TARGET_STATUS_REQUIRED,
    UNKNOWN_TARGET_STATUS,
    NO_OP_TRANSITION,
    INVALID_TRANSITION,
    REASON_TOO_LONG,
    CONCURRENT_TRANSITION
}
