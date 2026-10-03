package com.kora.ecommerce.catalog.api.admin;

import org.springframework.http.HttpStatus;

public class CatalogAdminException extends RuntimeException {

    private final HttpStatus status;

    private CatalogAdminException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public static CatalogAdminException badRequest(String message) {
        return new CatalogAdminException(HttpStatus.BAD_REQUEST, message);
    }

    public static CatalogAdminException notFound(String message) {
        return new CatalogAdminException(HttpStatus.NOT_FOUND, message);
    }

    public static CatalogAdminException conflict(String message) {
        return new CatalogAdminException(HttpStatus.CONFLICT, message);
    }

    public HttpStatus status() {
        return status;
    }
}
