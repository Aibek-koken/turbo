package com.kora.ecommerce.order.application;

public enum OrderQueryFailure {
    AUTHENTICATED_CUSTOMER_REQUIRED,
    ORDER_ID_REQUIRED,
    ORDER_NOT_FOUND,
    INVALID_PAGE_NUMBER,
    INVALID_PAGE_SIZE,
    PAGE_SIZE_TOO_LARGE
}
