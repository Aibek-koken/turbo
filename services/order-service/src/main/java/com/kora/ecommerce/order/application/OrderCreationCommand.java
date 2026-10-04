package com.kora.ecommerce.order.application;

import java.util.List;

public record OrderCreationCommand(
        String customerId,
        List<OrderCreationItemCommand> items) {
}
