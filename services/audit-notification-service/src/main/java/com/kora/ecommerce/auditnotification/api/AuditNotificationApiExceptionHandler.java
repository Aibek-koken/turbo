package com.kora.ecommerce.auditnotification.api;

import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.kora.ecommerce.auditnotification.api")
public class AuditNotificationApiExceptionHandler {

    @ExceptionHandler(AuditNotificationApiException.class)
    ResponseEntity<ProblemDetail> handleAuditNotificationApi(AuditNotificationApiException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.apiStatus(), exception.getMessage());
        problem.setTitle("Audit notification lookup failed");
        problem.setProperty("failure", exception.failure().name());
        if (exception.orderId() != null) {
            problem.setProperty("orderId", exception.orderId());
        }
        if (exception.eventId() != null) {
            problem.setProperty("eventId", exception.eventId());
        }
        if (exception.maxAllowed() != null) {
            problem.setProperty("maxAllowed", exception.maxAllowed());
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
