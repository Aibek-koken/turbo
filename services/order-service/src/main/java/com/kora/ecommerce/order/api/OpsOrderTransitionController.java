package com.kora.ecommerce.order.api;

import java.util.Locale;
import java.util.UUID;

import com.kora.ecommerce.order.application.OrderTransitionCommand;
import com.kora.ecommerce.order.application.OrderTransitionException;
import com.kora.ecommerce.order.application.OrderTransitionService;
import com.kora.ecommerce.order.persistence.OrderStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders/ops/orders")
public class OpsOrderTransitionController {

    private final OrderTransitionService orderTransitionService;

    OpsOrderTransitionController(OrderTransitionService orderTransitionService) {
        this.orderTransitionService = orderTransitionService;
    }

    @PostMapping("/{orderId}/transitions")
    @PreAuthorize("hasRole('OPS_ADMIN')")
    TransitionOrderResponse transitionOrder(
            @PathVariable UUID orderId,
            @RequestBody(required = false) TransitionOrderRequest request) {
        OrderStatus targetStatus = targetStatusFrom(orderId, request);
        return TransitionOrderResponse.from(orderTransitionService.transition(
                new OrderTransitionCommand(
                        orderId,
                        targetStatus,
                        request == null ? null : request.reason())));
    }

    private static OrderStatus targetStatusFrom(UUID orderId, TransitionOrderRequest request) {
        if (request == null || !StringUtils.hasText(request.targetStatus())) {
            throw OrderTransitionException.targetStatusRequired(orderId);
        }
        String normalizedStatus = request.targetStatus().trim().toUpperCase(Locale.ROOT);
        try {
            return OrderStatus.valueOf(normalizedStatus);
        } catch (IllegalArgumentException exception) {
            throw OrderTransitionException.unknownTargetStatus(orderId, request.targetStatus());
        }
    }
}
