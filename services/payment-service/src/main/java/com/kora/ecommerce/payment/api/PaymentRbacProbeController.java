package com.kora.ecommerce.payment.api;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentRbacProbeController {

    @GetMapping("/customer/rbac")
    Map<String, String> customerProbe() {
        return Map.of(
                "service", "payment-service",
                "boundary", "customer-payments");
    }

    @GetMapping("/ops/rbac")
    Map<String, String> opsProbe() {
        return Map.of(
                "service", "payment-service",
                "boundary", "ops-payments");
    }
}
