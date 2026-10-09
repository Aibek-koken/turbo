package com.kora.ecommerce.payment.api;

import java.util.UUID;

import com.kora.ecommerce.payment.api.PaymentDtos.PaymentResponse;
import com.kora.ecommerce.payment.persistence.Payment;
import com.kora.ecommerce.payment.persistence.PaymentAttemptRepository;
import com.kora.ecommerce.payment.persistence.PaymentRepository;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@Tag(name = "Payments", description = "Read-only payment status APIs for customers and operations.")
@SecurityRequirement(name = "bearer-jwt")
public class PaymentQueryController {

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;

    public PaymentQueryController(
            PaymentRepository paymentRepository,
            PaymentAttemptRepository paymentAttemptRepository) {
        this.paymentRepository = paymentRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
    }

    @GetMapping("/customer/payments/by-order/{orderId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'OPS_ADMIN')")
    @Operation(
            operationId = "getCustomerPaymentByOrder",
            summary = "Get the authenticated customer's payment by order",
            description = "Requires CUSTOMER or OPS_ADMIN. Results are scoped to the JWT subject so customers "
                    + "cannot inspect another customer's payment.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment status"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Bearer token lacks an allowed role",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "No payment found for this customer/order pair",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    PaymentResponse getCustomerPaymentByOrder(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Order identifier.")
            @PathVariable UUID orderId) {
        Payment payment = paymentRepository.findByOrderIdAndCustomerId(orderId, customerIdFrom(jwt))
                .orElseThrow(() -> PaymentApiException.paymentNotFoundForOrder(orderId));
        return response(payment);
    }

    @GetMapping("/ops/payments/{paymentId}")
    @PreAuthorize("hasRole('OPS_ADMIN')")
    @Operation(
            operationId = "getPaymentForOperations",
            summary = "Get a payment by id for operations support",
            description = "Requires OPS_ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment status"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Bearer token lacks OPS_ADMIN",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Payment not found",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    PaymentResponse getPaymentForOperations(
            @Parameter(description = "Payment identifier.")
            @PathVariable UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> PaymentApiException.paymentNotFound(paymentId));
        return response(payment);
    }

    @GetMapping("/ops/payments/by-order/{orderId}")
    @PreAuthorize("hasRole('OPS_ADMIN')")
    @Operation(
            operationId = "getPaymentByOrderForOperations",
            summary = "Get a payment by order for operations support",
            description = "Requires OPS_ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment status"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Bearer token lacks OPS_ADMIN",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Payment not found for order",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    PaymentResponse getPaymentByOrderForOperations(
            @Parameter(description = "Order identifier.")
            @PathVariable UUID orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> PaymentApiException.paymentNotFoundForOrder(orderId));
        return response(payment);
    }

    private PaymentResponse response(Payment payment) {
        return PaymentResponse.from(
                payment,
                paymentAttemptRepository.findByPaymentIdOrderByRequestedAtDescIdDesc(payment.getId()));
    }

    private static String customerIdFrom(Jwt jwt) {
        if (jwt == null || !StringUtils.hasText(jwt.getSubject())) {
            throw PaymentApiException.authenticatedCustomerRequired();
        }
        return jwt.getSubject().trim();
    }
}
