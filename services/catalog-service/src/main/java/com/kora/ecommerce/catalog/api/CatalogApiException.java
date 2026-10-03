package com.kora.ecommerce.catalog.api;

import org.springframework.http.HttpStatus;

public class CatalogApiException extends RuntimeException {

    private final HttpStatus status;

    private CatalogApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public static CatalogApiException badRequest(String message) {
        return new CatalogApiException(HttpStatus.BAD_REQUEST, message);
    }

    public static CatalogApiException notFound(String message) {
        return new CatalogApiException(HttpStatus.NOT_FOUND, message);
    }

    public HttpStatus status() {
        return status;
    }
}
