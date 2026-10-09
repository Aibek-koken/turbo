package com.kora.ecommerce.order.api;

import java.util.Map;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@RequestMapping("/api/orders")
public class OrderRbacProbeController {

    @GetMapping("/customer/rbac")
    Map<String, String> customerProbe() {
        return Map.of(
                "service", "order-service",
                "boundary", "customer-orders");
    }

    @GetMapping("/ops/rbac")
    Map<String, String> opsProbe() {
        return Map.of(
                "service", "order-service",
                "boundary", "ops-orders");
    }
}
