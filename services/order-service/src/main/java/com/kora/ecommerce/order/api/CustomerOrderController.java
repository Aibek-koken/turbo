package com.kora.ecommerce.order.api;

import com.kora.ecommerce.order.application.OrderCreationCommand;
import com.kora.ecommerce.order.application.OrderCreationException;
import com.kora.ecommerce.order.application.OrderCreationItemCommand;
import com.kora.ecommerce.order.application.OrderCreationService;
import com.kora.ecommerce.order.application.OrderQueryException;
import com.kora.ecommerce.order.application.OrderQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
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
@Tag(name = "Customer orders", description = "Customer-owned order creation and query APIs.")
@SecurityRequirement(name = "bearer-jwt")
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
    @Operation(
            operationId = "listCustomerOrders",
            summary = "List the authenticated customer's orders",
            description = "Requires CUSTOMER. Results are scoped to the JWT subject and support zero-based "
                    + "page/size pagination.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paged customer orders"),
            @ApiResponse(responseCode = "400", description = "Invalid pagination",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Bearer token lacks CUSTOMER",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    OrderPageResponse listOrders(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Zero-based result page. Defaults to 0.")
            @RequestParam(required = false) Integer page,
            @Parameter(description = "Page size. Defaults to 20 and is capped by the service.")
            @RequestParam(required = false) Integer size) {
        return OrderPageResponse.from(orderQueryService.listCustomerOrders(
                queryCustomerIdFrom(jwt),
                page,
                size));
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(
            operationId = "getCustomerOrder",
            summary = "Get one authenticated customer order",
            description = "Requires CUSTOMER. The order must belong to the JWT subject.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Customer order detail"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Bearer token lacks CUSTOMER",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Order not found for this customer",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    OrderResponse getOrder(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Order identifier.")
            @PathVariable UUID orderId) {
        return OrderResponse.from(orderQueryService.getCustomerOrder(
                queryCustomerIdFrom(jwt),
                orderId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(
            operationId = "createCustomerOrder",
            summary = "Create an order for the authenticated customer",
            description = "Requires CUSTOMER. The customer id is taken from the JWT subject; product snapshots "
                    + "and prices come from Catalog Service, not from client-owned fields.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Order created"),
            @ApiResponse(responseCode = "400", description = "Invalid order request",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Bearer token lacks CUSTOMER",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422", description = "Product snapshot cannot be used for an order",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
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
