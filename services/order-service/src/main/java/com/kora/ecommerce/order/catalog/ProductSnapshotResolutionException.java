package com.kora.ecommerce.order.catalog;

import java.util.Objects;
import java.util.UUID;

import org.springframework.http.HttpStatus;

public class ProductSnapshotResolutionException extends RuntimeException {

    private final UUID productId;
    private final ProductSnapshotFailure failure;

    private ProductSnapshotResolutionException(
            UUID productId,
            ProductSnapshotFailure failure,
            String message,
            Throwable cause) {
        super(message, cause);
        this.productId = Objects.requireNonNull(productId, "productId is required");
        this.failure = Objects.requireNonNull(failure, "failure is required");
    }

    public static ProductSnapshotResolutionException missing(UUID productId) {
        return failure(productId, ProductSnapshotFailure.MISSING, null);
    }

    public static ProductSnapshotResolutionException inactive(UUID productId) {
        return failure(productId, ProductSnapshotFailure.INACTIVE, null);
    }

    public static ProductSnapshotResolutionException malformed(UUID productId) {
        return failure(productId, ProductSnapshotFailure.MALFORMED, null);
    }

    public static ProductSnapshotResolutionException malformed(UUID productId, Throwable cause) {
        return failure(productId, ProductSnapshotFailure.MALFORMED, cause);
    }

    public static ProductSnapshotResolutionException unavailable(UUID productId) {
        return failure(productId, ProductSnapshotFailure.UNAVAILABLE, null);
    }

    public static ProductSnapshotResolutionException unavailable(UUID productId, Throwable cause) {
        return failure(productId, ProductSnapshotFailure.UNAVAILABLE, cause);
    }

    public static ProductSnapshotResolutionException authenticationRequired(UUID productId) {
        return failure(productId, ProductSnapshotFailure.AUTHENTICATION_REQUIRED, null);
    }

    private static ProductSnapshotResolutionException failure(
            UUID productId,
            ProductSnapshotFailure failure,
            Throwable cause) {
        return new ProductSnapshotResolutionException(
                productId,
                failure,
                failure.detail() + " productId=" + productId,
                cause);
    }

    public UUID productId() {
        return productId;
    }

    public ProductSnapshotFailure failure() {
        return failure;
    }

    public HttpStatus apiStatus() {
        return failure.apiStatus();
    }
}
