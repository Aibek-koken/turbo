package com.kora.ecommerce.catalog.api;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog/rbac")
public class CatalogRbacProbeController {

    @GetMapping("/customer")
    Map<String, String> customerProbe() {
        return Map.of(
                "service", "catalog-service",
                "boundary", "customer-catalog-read");
    }

    @GetMapping("/admin")
    Map<String, String> adminProbe() {
        return Map.of(
                "service", "catalog-service",
                "boundary", "catalog-admin");
    }
}
