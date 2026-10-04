package com.kora.ecommerce.order.application;

import java.util.UUID;

import com.kora.ecommerce.order.persistence.OrderStatus;
import org.springframework.http.HttpStatus;

public class OrderTransitionException extends RuntimeException {

    private final OrderTransitionFailure failure;
    private final HttpStatus apiStatus;
    private final UUID orderId;
    private final OrderStatus currentStatus;
    private final OrderStatus targetStatus;
    private final String requestedStatus;
    private final Integer maxAllowed;

    private OrderTransitionException(
            OrderTransitionFailure failure,
            HttpStatus apiStatus,
            String detail,
            UUID orderId,
            OrderStatus currentStatus,
            OrderStatus targetStatus,
            String requestedStatus,
            Integer maxAllowed,
            Throwable cause) {
        super(detail, cause);
        this.failure = failure;
        this.apiStatus = apiStatus;
        this.orderId = orderId;
        this.currentStatus = currentStatus;
        this.targetStatus = targetStatus;
        this.requestedStatus = requestedStatus;
        this.maxAllowed = maxAllowed;
    }

    public static OrderTransitionException orderIdRequired() {
        return new OrderTransitionException(
                OrderTransitionFailure.ORDER_ID_REQUIRED,
                HttpStatus.BAD_REQUEST,
                "Order ID is required for a status transition.",
                null,
                null,
                null,
                null,
                null,
                null);
    }

    public static OrderTransitionException orderNotFound(UUID orderId) {
        return new OrderTransitionException(
                OrderTransitionFailure.ORDER_NOT_FOUND,
                HttpStatus.NOT_FOUND,
                "Order was not found.",
                orderId,
                null,
                null,
                null,
                null,
                null);
    }

    public static OrderTransitionException targetStatusRequired(UUID orderId) {
        return new OrderTransitionException(
                OrderTransitionFailure.TARGET_STATUS_REQUIRED,
                HttpStatus.BAD_REQUEST,
                "Target status is required for a status transition.",
                orderId,
                null,
                null,
                null,
                null,
                null);
    }

    public static OrderTransitionException unknownTargetStatus(UUID orderId, String requestedStatus) {
        return new OrderTransitionException(
                OrderTransitionFailure.UNKNOWN_TARGET_STATUS,
                HttpStatus.BAD_REQUEST,
                "Target status is not part of the V1 order state model.",
                orderId,
                null,
                null,
                requestedStatus,
                null,
                null);
    }

    public static OrderTransitionException noOp(UUID orderId, OrderStatus status) {
        return new OrderTransitionException(
                OrderTransitionFailure.NO_OP_TRANSITION,
                HttpStatus.CONFLICT,
                "Order is already in the requested status.",
                orderId,
                status,
                status,
                null,
                null,
                null);
    }

    public static OrderTransitionException invalid(
            UUID orderId,
            OrderStatus currentStatus,
            OrderStatus targetStatus) {
        return new OrderTransitionException(
                OrderTransitionFailure.INVALID_TRANSITION,
                HttpStatus.CONFLICT,
                "Requested order status transition is not allowed.",
                orderId,
                currentStatus,
                targetStatus,
                null,
                null,
                null);
    }

    public static OrderTransitionException reasonTooLong(UUID orderId, int maxAllowed) {
        return new OrderTransitionException(
                OrderTransitionFailure.REASON_TOO_LONG,
                HttpStatus.BAD_REQUEST,
                "Transition reason is too long.",
                orderId,
                null,
                null,
                null,
                maxAllowed,
                null);
    }

    public static OrderTransitionException concurrent(UUID orderId, Throwable cause) {
        return new OrderTransitionException(
                OrderTransitionFailure.CONCURRENT_TRANSITION,
                HttpStatus.CONFLICT,
                "Order was updated concurrently; reload it and retry the transition.",
                orderId,
                null,
                null,
                null,
                null,
                cause);
    }

    public OrderTransitionFailure failure() {
        return failure;
    }

    public HttpStatus apiStatus() {
        return apiStatus;
    }

    public UUID orderId() {
        return orderId;
    }

    public OrderStatus currentStatus() {
        return currentStatus;
    }

    public OrderStatus targetStatus() {
        return targetStatus;
    }

    public String requestedStatus() {
        return requestedStatus;
    }

    public Integer maxAllowed() {
        return maxAllowed;
    }
}
