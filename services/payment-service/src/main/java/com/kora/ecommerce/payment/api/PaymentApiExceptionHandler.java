package com.kora.ecommerce.payment.api;

import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.kora.ecommerce.payment.api")
public class PaymentApiExceptionHandler {

    @ExceptionHandler(PaymentApiException.class)
    ResponseEntity<ProblemDetail> handlePaymentApi(PaymentApiException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.apiStatus(), exception.getMessage());
        problem.setTitle("Payment lookup failed");
        problem.setProperty("failure", exception.failure().name());
        if (exception.paymentId() != null) {
            problem.setProperty("paymentId", exception.paymentId());
        }
        if (exception.orderId() != null) {
            problem.setProperty("orderId", exception.orderId());
        }
        return ResponseEntity.status(exception.apiStatus()).body(problem);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> handleArgumentTypeMismatch(MethodArgumentTypeMismatchException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "Request parameter or path variable has an invalid value.");
        problem.setTitle("Invalid request parameter");
        if (exception.getName() != null) {
            problem.setProperty("parameter", exception.getName());
        }
        return ResponseEntity.badRequest().body(problem);
    }
}
