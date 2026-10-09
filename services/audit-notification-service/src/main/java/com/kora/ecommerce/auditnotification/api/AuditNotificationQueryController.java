package com.kora.ecommerce.auditnotification.api;

import java.util.List;
import java.util.UUID;

import com.kora.ecommerce.auditnotification.api.AuditNotificationDtos.AuditEventPageResponse;
import com.kora.ecommerce.auditnotification.api.AuditNotificationDtos.AuditEventResponse;
import com.kora.ecommerce.auditnotification.api.AuditNotificationDtos.NotificationDeliveryResponse;
import com.kora.ecommerce.auditnotification.notification.NotificationDeliveryRepository;
import com.kora.ecommerce.auditnotification.persistence.AuditEventRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit-notifications")
@Tag(name = "Audit notifications", description = "Customer notification status and ops audit event APIs.")
@SecurityRequirement(name = "bearer-jwt")
public class AuditNotificationQueryController {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final AuditEventRepository auditEventRepository;
    private final NotificationDeliveryRepository notificationDeliveryRepository;

    public AuditNotificationQueryController(
            AuditEventRepository auditEventRepository,
            NotificationDeliveryRepository notificationDeliveryRepository) {
        this.auditEventRepository = auditEventRepository;
        this.notificationDeliveryRepository = notificationDeliveryRepository;
    }

    @GetMapping("/customer/orders/{orderId}/notifications")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'OPS_ADMIN')")
    @Operation(
            operationId = "listCustomerOrderNotifications",
            summary = "List notification deliveries for the authenticated customer's order",
            description = "Requires CUSTOMER or OPS_ADMIN. Results are scoped to the JWT subject so customers "
                    + "cannot inspect another customer's notification ledger.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notification deliveries",
                    content = @Content(array = @ArraySchema(schema =
                    @Schema(implementation = NotificationDeliveryResponse.class)))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Bearer token lacks an allowed role",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    List<NotificationDeliveryResponse> listCustomerOrderNotifications(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Order identifier.")
            @PathVariable UUID orderId) {
        return notificationDeliveryRepository
                .findByOrderIdAndCustomerId(orderId.toString(), customerIdFrom(jwt))
                .stream()
                .map(NotificationDeliveryResponse::from)
                .toList();
    }

    @GetMapping("/ops/audit-events/{eventId}")
    @PreAuthorize("hasRole('OPS_ADMIN')")
    @Operation(
            operationId = "getAuditEventForOperations",
            summary = "Get one audit event by event id",
            description = "Requires OPS_ADMIN. The response omits Kafka topic, partition and offset internals.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Audit event"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Bearer token lacks OPS_ADMIN",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Audit event not found",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    AuditEventResponse getAuditEventForOperations(
            @Parameter(description = "Business event identifier.")
            @PathVariable String eventId) {
        return auditEventRepository.findByEventId(eventId)
                .map(AuditEventResponse::from)
                .orElseThrow(() -> AuditNotificationApiException.auditEventNotFound(eventId));
    }

    @GetMapping("/ops/orders/{orderId}/audit-events")
    @PreAuthorize("hasRole('OPS_ADMIN')")
    @Operation(
            operationId = "listOrderAuditEventsForOperations",
            summary = "List audit events for an order",
            description = "Requires OPS_ADMIN. Supports zero-based page/size pagination and omits Kafka internals.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paged audit events"),
            @ApiResponse(responseCode = "400", description = "Invalid pagination",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Bearer token lacks OPS_ADMIN",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    AuditEventPageResponse listOrderAuditEventsForOperations(
            @Parameter(description = "Order identifier.")
            @PathVariable UUID orderId,
            @Parameter(description = "Zero-based result page. Defaults to 0.")
            @RequestParam(required = false) Integer page,
            @Parameter(description = "Page size from 1 to 100. Defaults to 20.")
            @RequestParam(required = false) Integer size) {
        Pageable pageable = pageable(page, size);
        return AuditEventPageResponse.from(auditEventRepository.findByOrderId(orderId.toString(), pageable));
    }

    private static Pageable pageable(Integer page, Integer size) {
        int resolvedPage = page == null ? DEFAULT_PAGE : page;
        int resolvedSize = size == null ? DEFAULT_SIZE : size;
        if (resolvedPage < 0) {
            throw AuditNotificationApiException.negativePage();
        }
        if (resolvedSize < 1 || resolvedSize > MAX_SIZE) {
            throw AuditNotificationApiException.invalidPageSize(MAX_SIZE);
        }
        return PageRequest.of(resolvedPage, resolvedSize);
    }

    private static String customerIdFrom(Jwt jwt) {
        if (jwt == null || !StringUtils.hasText(jwt.getSubject())) {
            throw AuditNotificationApiException.authenticatedCustomerRequired();
        }
        return jwt.getSubject().trim();
    }
}
