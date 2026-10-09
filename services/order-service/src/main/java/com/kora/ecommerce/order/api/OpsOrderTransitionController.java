package com.kora.ecommerce.order.api;

import java.util.Locale;
import java.util.UUID;

import com.kora.ecommerce.order.application.OrderTransitionCommand;
import com.kora.ecommerce.order.application.OrderTransitionException;
import com.kora.ecommerce.order.application.OrderTransitionService;
import com.kora.ecommerce.order.persistence.OrderStatus;
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
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders/ops/orders")
@Tag(name = "Ops orders", description = "Operations support APIs for order lookup and state transitions.")
@SecurityRequirement(name = "bearer-jwt")
public class OpsOrderTransitionController {

    private final OrderTransitionService orderTransitionService;

    OpsOrderTransitionController(OrderTransitionService orderTransitionService) {
        this.orderTransitionService = orderTransitionService;
    }

    @PostMapping("/{orderId}/transitions")
    @PreAuthorize("hasRole('OPS_ADMIN')")
    @Operation(
            operationId = "transitionOrderForOperations",
            summary = "Transition an order state",
            description = "Requires OPS_ADMIN. Allowed target statuses follow the service state machine.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order transition applied"),
            @ApiResponse(responseCode = "400", description = "Invalid transition request",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Bearer token lacks OPS_ADMIN",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Order not found",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    TransitionOrderResponse transitionOrder(
            @Parameter(description = "Order identifier.")
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
