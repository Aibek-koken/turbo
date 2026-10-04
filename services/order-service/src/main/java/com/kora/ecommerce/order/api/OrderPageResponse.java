package com.kora.ecommerce.order.api;

import java.util.List;

import com.kora.ecommerce.order.application.QueriedOrderPage;

public record OrderPageResponse(
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        List<OrderResponse> orders) {

    static OrderPageResponse from(QueriedOrderPage page) {
        return new OrderPageResponse(
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages(),
                page.hasNext(),
                page.orders().stream()
                        .map(OrderResponse::from)
                        .toList());
    }
}
