package com.kora.ecommerce.payment.api;

import java.util.UUID;

import org.springframework.http.HttpStatus;

public class PaymentApiException extends RuntimeException {

    private final HttpStatus apiStatus;
    private final PaymentApiFailure failure;
    private final UUID paymentId;
    private final UUID orderId;

    private PaymentApiException(
            HttpStatus apiStatus,
            PaymentApiFailure failure,
            String message,
            UUID paymentId,
            UUID orderId) {
        super(message);
        this.apiStatus = apiStatus;
        this.failure = failure;
        this.paymentId = paymentId;
        this.orderId = orderId;
    }

    static PaymentApiException authenticatedCustomerRequired() {
        return new PaymentApiException(
                HttpStatus.UNAUTHORIZED,
                PaymentApiFailure.AUTHENTICATED_CUSTOMER_REQUIRED,
                "Authenticated customer subject is required.",
                null,
                null);
    }

    static PaymentApiException paymentNotFound(UUID paymentId) {
        return new PaymentApiException(
                HttpStatus.NOT_FOUND,
                PaymentApiFailure.PAYMENT_NOT_FOUND,
                "Payment was not found.",
                paymentId,
                null);
    }

    static PaymentApiException paymentNotFoundForOrder(UUID orderId) {
        return new PaymentApiException(
                HttpStatus.NOT_FOUND,
                PaymentApiFailure.PAYMENT_NOT_FOUND,
                "Payment was not found for the requested order.",
                null,
                orderId);
    }

    HttpStatus apiStatus() {
        return apiStatus;
    }

    PaymentApiFailure failure() {
        return failure;
    }

    UUID paymentId() {
        return paymentId;
    }

    UUID orderId() {
        return orderId;
    }

    enum PaymentApiFailure {
        AUTHENTICATED_CUSTOMER_REQUIRED,
        PAYMENT_NOT_FOUND
    }
}
