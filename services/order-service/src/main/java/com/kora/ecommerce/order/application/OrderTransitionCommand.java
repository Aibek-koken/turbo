package com.kora.ecommerce.order.application;

import java.util.UUID;

import com.kora.ecommerce.order.persistence.OrderStatus;

public record OrderTransitionCommand(UUID orderId, OrderStatus targetStatus, String reason) {
}
