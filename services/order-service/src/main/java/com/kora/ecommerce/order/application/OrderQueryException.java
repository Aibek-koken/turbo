package com.kora.ecommerce.order.application;

import java.util.UUID;

import org.springframework.http.HttpStatus;

public class OrderQueryException extends RuntimeException {

    private final OrderQueryFailure failure;
    private final HttpStatus apiStatus;
    private final UUID orderId;
    private final Integer maxAllowed;

    private OrderQueryException(
            OrderQueryFailure failure,
            HttpStatus apiStatus,
            String detail,
            UUID orderId,
            Integer maxAllowed) {
        super(detail);
        this.failure = failure;
        this.apiStatus = apiStatus;
        this.orderId = orderId;
        this.maxAllowed = maxAllowed;
    }

    public static OrderQueryException authenticatedCustomerRequired() {
        return new OrderQueryException(
                OrderQueryFailure.AUTHENTICATED_CUSTOMER_REQUIRED,
                HttpStatus.UNAUTHORIZED,
                "An authenticated customer subject is required.",
                null,
                null);
    }

    public static OrderQueryException orderIdRequired() {
        return new OrderQueryException(
                OrderQueryFailure.ORDER_ID_REQUIRED,
                HttpStatus.BAD_REQUEST,
                "Order ID is required.",
                null,
                null);
    }

    public static OrderQueryException orderNotFound(UUID orderId) {
        return new OrderQueryException(
                OrderQueryFailure.ORDER_NOT_FOUND,
                HttpStatus.NOT_FOUND,
                "Order was not found.",
                orderId,
                null);
    }

    public static OrderQueryException invalidPageNumber() {
        return new OrderQueryException(
                OrderQueryFailure.INVALID_PAGE_NUMBER,
                HttpStatus.BAD_REQUEST,
                "Page number must be zero or greater.",
                null,
                null);
    }

    public static OrderQueryException invalidPageSize() {
        return new OrderQueryException(
                OrderQueryFailure.INVALID_PAGE_SIZE,
                HttpStatus.BAD_REQUEST,
                "Page size must be positive.",
                null,
                null);
    }

    public static OrderQueryException pageSizeTooLarge(int maxAllowed) {
        return new OrderQueryException(
                OrderQueryFailure.PAGE_SIZE_TOO_LARGE,
                HttpStatus.BAD_REQUEST,
                "Page size exceeds the maximum.",
                null,
                maxAllowed);
    }

    public OrderQueryFailure failure() {
        return failure;
    }

    public HttpStatus apiStatus() {
        return apiStatus;
    }

    public UUID orderId() {
        return orderId;
    }

    public Integer maxAllowed() {
        return maxAllowed;
    }
}
