package com.kora.ecommerce.order.application.payment;

public class PaymentResultEventException extends RuntimeException {

    private final PaymentResultEventFailure failure;

    private PaymentResultEventException(PaymentResultEventFailure failure, String detail, Throwable cause) {
        super(detail, cause);
        this.failure = failure;
    }

    static PaymentResultEventException of(PaymentResultEventFailure failure, String detail) {
        return new PaymentResultEventException(failure, detail, null);
    }

    static PaymentResultEventException of(PaymentResultEventFailure failure, String detail, Throwable cause) {
        return new PaymentResultEventException(failure, detail, cause);
    }

    public PaymentResultEventFailure failure() {
        return failure;
    }
}
