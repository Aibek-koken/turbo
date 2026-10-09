package com.kora.ecommerce.auditnotification.api;

import java.util.UUID;

import org.springframework.http.HttpStatus;

public class AuditNotificationApiException extends RuntimeException {

    private final HttpStatus apiStatus;
    private final AuditNotificationApiFailure failure;
    private final UUID orderId;
    private final String eventId;
    private final Integer maxAllowed;

    private AuditNotificationApiException(
            HttpStatus apiStatus,
            AuditNotificationApiFailure failure,
            String message,
            UUID orderId,
            String eventId,
            Integer maxAllowed) {
        super(message);
        this.apiStatus = apiStatus;
        this.failure = failure;
        this.orderId = orderId;
        this.eventId = eventId;
        this.maxAllowed = maxAllowed;
    }

    static AuditNotificationApiException authenticatedCustomerRequired() {
        return new AuditNotificationApiException(
                HttpStatus.UNAUTHORIZED,
                AuditNotificationApiFailure.AUTHENTICATED_CUSTOMER_REQUIRED,
                "Authenticated customer subject is required.",
                null,
                null,
                null);
    }

    static AuditNotificationApiException auditEventNotFound(String eventId) {
        return new AuditNotificationApiException(
                HttpStatus.NOT_FOUND,
                AuditNotificationApiFailure.AUDIT_EVENT_NOT_FOUND,
                "Audit event was not found.",
                null,
                eventId,
                null);
    }

    static AuditNotificationApiException negativePage() {
        return new AuditNotificationApiException(
                HttpStatus.BAD_REQUEST,
                AuditNotificationApiFailure.INVALID_PAGE,
                "Page must not be negative.",
                null,
                null,
                null);
    }

    static AuditNotificationApiException invalidPageSize(int maxAllowed) {
        return new AuditNotificationApiException(
                HttpStatus.BAD_REQUEST,
                AuditNotificationApiFailure.INVALID_PAGE_SIZE,
                "Page size must be between 1 and " + maxAllowed + ".",
                null,
                null,
                maxAllowed);
    }

    HttpStatus apiStatus() {
        return apiStatus;
    }

    AuditNotificationApiFailure failure() {
        return failure;
    }

    UUID orderId() {
        return orderId;
    }

    String eventId() {
        return eventId;
    }

    Integer maxAllowed() {
        return maxAllowed;
    }

    enum AuditNotificationApiFailure {
        AUTHENTICATED_CUSTOMER_REQUIRED,
        AUDIT_EVENT_NOT_FOUND,
        INVALID_PAGE,
        INVALID_PAGE_SIZE
    }
}
