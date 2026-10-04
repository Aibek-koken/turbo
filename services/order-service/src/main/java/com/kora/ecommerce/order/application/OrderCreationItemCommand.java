package com.kora.ecommerce.order.application;

import java.util.UUID;

public record OrderCreationItemCommand(
        UUID productId,
        int quantity) {
}
