package com.kora.ecommerce.order.catalog;

import org.springframework.http.HttpStatus;

public enum ProductSnapshotFailure {
    MISSING(HttpStatus.UNPROCESSABLE_ENTITY, "Catalog product was not found."),
    INACTIVE(HttpStatus.UNPROCESSABLE_ENTITY, "Catalog product is not active."),
    MALFORMED(HttpStatus.BAD_GATEWAY, "Catalog product response could not be used."),
    UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Catalog product lookup is unavailable."),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "Bearer token is required for catalog product lookup.");

    private final HttpStatus apiStatus;
    private final String detail;

    ProductSnapshotFailure(HttpStatus apiStatus, String detail) {
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
