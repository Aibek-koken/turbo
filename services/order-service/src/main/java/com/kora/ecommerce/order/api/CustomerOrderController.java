package com.kora.ecommerce.order.api;

import com.kora.ecommerce.order.application.OrderCreationCommand;
import com.kora.ecommerce.order.application.OrderCreationException;
import com.kora.ecommerce.order.application.OrderCreationItemCommand;
import com.kora.ecommerce.order.application.OrderCreationService;
import com.kora.ecommerce.order.application.OrderQueryException;
import com.kora.ecommerce.order.application.OrderQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders/customer/orders")
public class CustomerOrderController {

    private final OrderCreationService orderCreationService;
    private final OrderQueryService orderQueryService;

    CustomerOrderController(
            OrderCreationService orderCreationService,
            OrderQueryService orderQueryService) {
        this.orderCreationService = orderCreationService;
        this.orderQueryService = orderQueryService;
    }

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    OrderPageResponse listOrders(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return OrderPageResponse.from(orderQueryService.listCustomerOrders(
                queryCustomerIdFrom(jwt),
                page,
                size));
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    OrderResponse getOrder(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID orderId) {
        return OrderResponse.from(orderQueryService.getCustomerOrder(
                queryCustomerIdFrom(jwt),
                orderId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CUSTOMER')")
    CreateOrderResponse createOrder(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) CreateOrderRequest request) {
        OrderCreationCommand command = new OrderCreationCommand(
                customerIdFrom(jwt),
                request == null
                        ? null
                        : request.items().stream()
                                .map(item -> item == null
                                        ? new OrderCreationItemCommand(null, 0)
                                        : new OrderCreationItemCommand(item.productId(), item.quantity()))
                                .toList());
        return CreateOrderResponse.from(orderCreationService.createOrder(command));
    }

    private static String customerIdFrom(Jwt jwt) {
        if (jwt == null || !StringUtils.hasText(jwt.getSubject())) {
            throw OrderCreationException.authenticatedCustomerRequired();
        }
        return jwt.getSubject().trim();
    }

    private static String queryCustomerIdFrom(Jwt jwt) {
        if (jwt == null || !StringUtils.hasText(jwt.getSubject())) {
            throw OrderQueryException.authenticatedCustomerRequired();
        }
        return jwt.getSubject().trim();
    }
}
