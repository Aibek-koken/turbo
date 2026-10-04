package com.kora.ecommerce.order.application;

import java.util.List;

public record QueriedOrderPage(
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        List<QueriedOrder> orders) {
}
