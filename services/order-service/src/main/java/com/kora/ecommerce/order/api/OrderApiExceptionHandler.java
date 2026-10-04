package com.kora.ecommerce.order.api;

import com.kora.ecommerce.order.application.OrderCreationException;
import com.kora.ecommerce.order.application.OrderTransitionException;
import com.kora.ecommerce.order.catalog.ProductSnapshotResolutionException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.kora.ecommerce.order.api")
public class OrderApiExceptionHandler {

    @ExceptionHandler(OrderCreationException.class)
    public ResponseEntity<ProblemDetail> handleOrderCreation(OrderCreationException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.apiStatus(), exception.detail());
        problem.setTitle("Order request is invalid");
        problem.setProperty("failure", exception.failure().name());
        if (exception.productId() != null) {
            problem.setProperty("productId", exception.productId());
        }
        if (exception.itemNumber() != null) {
            problem.setProperty("itemNumber", exception.itemNumber());
        }
        if (exception.maxAllowed() != null) {
            problem.setProperty("maxAllowed", exception.maxAllowed());
        }
        return ResponseEntity.status(exception.apiStatus()).body(problem);
    }

    @ExceptionHandler(ProductSnapshotResolutionException.class)
    public ResponseEntity<ProblemDetail> handleProductSnapshotResolution(
            ProductSnapshotResolutionException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.apiStatus(), exception.getMessage());
        problem.setTitle("Product snapshot resolution failed");
        problem.setProperty("productId", exception.productId());
        problem.setProperty("failure", exception.failure().name());
        return ResponseEntity.status(exception.apiStatus()).body(problem);
    }

    @ExceptionHandler(OrderTransitionException.class)
    public ResponseEntity<ProblemDetail> handleOrderTransition(OrderTransitionException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.apiStatus(), exception.getMessage());
        problem.setTitle("Order transition is invalid");
        problem.setProperty("failure", exception.failure().name());
        if (exception.orderId() != null) {
            problem.setProperty("orderId", exception.orderId());
        }
        if (exception.currentStatus() != null) {
            problem.setProperty("currentStatus", exception.currentStatus().name());
        }
        if (exception.targetStatus() != null) {
            problem.setProperty("targetStatus", exception.targetStatus().name());
        }
        if (exception.requestedStatus() != null) {
            problem.setProperty("requestedStatus", exception.requestedStatus());
        }
        if (exception.maxAllowed() != null) {
            problem.setProperty("maxAllowed", exception.maxAllowed());
        }
        return ResponseEntity.status(exception.apiStatus()).body(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadableRequest(HttpMessageNotReadableException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "Request body must be valid JSON.");
        problem.setTitle("Malformed request body");
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handlePersistenceConstraint(DataIntegrityViolationException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                "Order could not be persisted.");
        problem.setTitle("Order persistence failed");
        return ResponseEntity.internalServerError().body(problem);
    }
}
