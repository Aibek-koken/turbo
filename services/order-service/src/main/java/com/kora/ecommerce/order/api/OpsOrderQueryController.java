package com.kora.ecommerce.order.api;

import java.util.UUID;

import com.kora.ecommerce.order.application.OrderQueryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders/ops/orders")
public class OpsOrderQueryController {

    private final OrderQueryService orderQueryService;

    OpsOrderQueryController(OrderQueryService orderQueryService) {
        this.orderQueryService = orderQueryService;
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasRole('OPS_ADMIN')")
    OrderResponse getOrder(@PathVariable UUID orderId) {
        return OrderResponse.from(orderQueryService.getOrderForOperations(orderId));
    }
}
