package com.kora.ecommerce.order.api;

import java.util.UUID;

import com.kora.ecommerce.order.application.OrderQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders/ops/orders")
@Tag(name = "Ops orders", description = "Operations support APIs for order lookup and state transitions.")
@SecurityRequirement(name = "bearer-jwt")
public class OpsOrderQueryController {

    private final OrderQueryService orderQueryService;

    OpsOrderQueryController(OrderQueryService orderQueryService) {
        this.orderQueryService = orderQueryService;
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasRole('OPS_ADMIN')")
    @Operation(
            operationId = "getOrderForOperations",
            summary = "Get any order for operations support",
            description = "Requires OPS_ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order detail"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Bearer token lacks OPS_ADMIN",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Order not found",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    OrderResponse getOrder(
            @Parameter(description = "Order identifier.")
            @PathVariable UUID orderId) {
        return OrderResponse.from(orderQueryService.getOrderForOperations(orderId));
    }
}
