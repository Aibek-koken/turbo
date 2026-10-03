package com.kora.ecommerce.catalog.cache;

public class ProductDetailCacheLockException extends RuntimeException {

    ProductDetailCacheLockException(String message) {
        super(message);
    }

    ProductDetailCacheLockException(String message, Throwable cause) {
        super(message, cause);
    }
}
