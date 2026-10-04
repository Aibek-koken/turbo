package com.kora.ecommerce.order.api;

public record TransitionOrderRequest(String targetStatus, String reason) {
}
