package com.kora.ecommerce.order.api;

import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(List<CreateOrderItemRequest> items) {

    public CreateOrderRequest {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public record CreateOrderItemRequest(UUID productId, int quantity) {
    }
}
