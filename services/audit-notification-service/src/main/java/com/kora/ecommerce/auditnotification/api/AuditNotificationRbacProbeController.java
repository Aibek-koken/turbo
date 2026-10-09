package com.kora.ecommerce.auditnotification.api;

import java.util.Map;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@RequestMapping("/api/audit-notifications")
public class AuditNotificationRbacProbeController {

    @GetMapping("/customer/rbac")
    Map<String, String> customerProbe() {
        return Map.of(
                "service", "audit-notification-service",
                "boundary", "customer-notifications");
    }

    @GetMapping("/ops/rbac")
    Map<String, String> opsProbe() {
        return Map.of(
                "service", "audit-notification-service",
                "boundary", "ops-audit-notifications");
    }
}
