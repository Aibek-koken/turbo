package com.kora.ecommerce.catalog.api.admin;

import java.util.LinkedHashMap;
import java.util.Map;

import com.kora.ecommerce.catalog.api.CatalogApiException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(basePackages = "com.kora.ecommerce.catalog.api")
public class CatalogAdminExceptionHandler {

    @ExceptionHandler(CatalogAdminException.class)
    public ResponseEntity<ProblemDetail> handleCatalogAdminException(CatalogAdminException exception) {
        ProblemDetail problem = problem(exception.status(), exception.getMessage());
        return ResponseEntity.status(exception.status()).body(problem);
    }

    @ExceptionHandler(CatalogApiException.class)
    public ResponseEntity<ProblemDetail> handleCatalogApiException(CatalogApiException exception) {
        ProblemDetail problem = problem(exception.status(), exception.getMessage());
        return ResponseEntity.status(exception.status()).body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        exception.getBindingResult().getGlobalErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .forEach(message -> errors.putIfAbsent("request", message));

        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Request validation failed.");
        problem.setTitle("Invalid request body");
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException exception) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Request validation failed.");
        problem.setTitle("Invalid request");
        problem.setProperty("errors", exception.getConstraintViolations().stream()
                .collect(
                        LinkedHashMap::new,
                        (errors, violation) -> errors.put(
                                violation.getPropertyPath().toString(),
                                violation.getMessage()),
                        LinkedHashMap::putAll));
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadableMessage(HttpMessageNotReadableException exception) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Request body could not be parsed.");
        problem.setTitle("Malformed request body");
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Path or query parameter has an invalid format.");
        problem.setTitle("Invalid request parameter");
        problem.setProperty("parameter", exception.getName());
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException exception) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "Catalog write conflicts with an existing record.");
        problem.setTitle("Catalog conflict");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    private static ProblemDetail problem(HttpStatus status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        return problem;
    }
}
