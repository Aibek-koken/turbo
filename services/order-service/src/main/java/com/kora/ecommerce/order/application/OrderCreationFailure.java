package com.kora.ecommerce.order.application;

import org.springframework.http.HttpStatus;

public enum OrderCreationFailure {
    AUTHENTICATED_CUSTOMER_REQUIRED(
            HttpStatus.UNAUTHORIZED,
            "An authenticated customer subject is required."),
    EMPTY_ORDER(
            HttpStatus.BAD_REQUEST,
            "Order must contain at least one item."),
    TOO_MANY_ITEMS(
            HttpStatus.BAD_REQUEST,
            "Order item count exceeds the maximum."),
    MISSING_PRODUCT_ID(
            HttpStatus.BAD_REQUEST,
            "Each order item must include a product ID."),
    INVALID_QUANTITY(
            HttpStatus.BAD_REQUEST,
            "Order item quantity must be positive and within the allowed limit."),
    DUPLICATE_PRODUCT(
            HttpStatus.BAD_REQUEST,
            "Order cannot contain the same product more than once."),
    MIXED_CURRENCIES(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "Order items must use a single currency.");

    private final HttpStatus apiStatus;
    private final String detail;

    OrderCreationFailure(HttpStatus apiStatus, String detail) {
        this.apiStatus = apiStatus;
        this.detail = detail;
    }

    public HttpStatus apiStatus() {
        return apiStatus;
    }

    public String detail() {
        return detail;
    }
}
