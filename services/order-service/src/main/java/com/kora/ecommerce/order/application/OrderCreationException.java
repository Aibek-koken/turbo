package com.kora.ecommerce.order.application;

import java.util.Objects;
import java.util.UUID;

import org.springframework.http.HttpStatus;

public class OrderCreationException extends RuntimeException {

    private final OrderCreationFailure failure;
    private final UUID productId;
    private final Integer itemNumber;
    private final Integer maxAllowed;

    private OrderCreationException(
            OrderCreationFailure failure,
            UUID productId,
            Integer itemNumber,
            Integer maxAllowed) {
        super(failure.detail());
        this.failure = Objects.requireNonNull(failure, "failure is required");
        this.productId = productId;
        this.itemNumber = itemNumber;
        this.maxAllowed = maxAllowed;
    }

    public static OrderCreationException authenticatedCustomerRequired() {
        return new OrderCreationException(
                OrderCreationFailure.AUTHENTICATED_CUSTOMER_REQUIRED, null, null, null);
    }

    public static OrderCreationException emptyOrder() {
        return new OrderCreationException(OrderCreationFailure.EMPTY_ORDER, null, null, null);
    }

    public static OrderCreationException tooManyItems(int maxAllowed) {
        return new OrderCreationException(
                OrderCreationFailure.TOO_MANY_ITEMS, null, null, maxAllowed);
    }

    public static OrderCreationException missingProductId(int itemNumber) {
        return new OrderCreationException(
                OrderCreationFailure.MISSING_PRODUCT_ID, null, itemNumber, null);
    }

    public static OrderCreationException invalidQuantity(UUID productId, int quantity, int maxAllowed) {
        return new OrderCreationException(
                OrderCreationFailure.INVALID_QUANTITY, productId, null, maxAllowed);
    }

    public static OrderCreationException duplicateProduct(UUID productId) {
        return new OrderCreationException(
                OrderCreationFailure.DUPLICATE_PRODUCT, productId, null, null);
    }

    public static OrderCreationException mixedCurrencies(UUID productId) {
        return new OrderCreationException(
                OrderCreationFailure.MIXED_CURRENCIES, productId, null, null);
    }

    public OrderCreationFailure failure() {
        return failure;
    }

    public HttpStatus apiStatus() {
        return failure.apiStatus();
    }

    public String detail() {
        return failure.detail();
    }

    public UUID productId() {
        return productId;
    }

    public Integer itemNumber() {
        return itemNumber;
    }

    public Integer maxAllowed() {
        return maxAllowed;
    }
}
