package com.kora.ecommerce.catalog.api.customer;

import java.util.UUID;

import com.kora.ecommerce.catalog.api.customer.CatalogBrowseDtos.ProductBrowsePageResponse;
import com.kora.ecommerce.catalog.api.customer.CatalogBrowseDtos.ProductDetailResponse;
import com.kora.ecommerce.catalog.domain.ProductStatus;
import com.kora.ecommerce.catalog.service.CatalogBrowseService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/catalog")
public class CatalogBrowseController {

    private final CatalogBrowseService catalogBrowseService;

    public CatalogBrowseController(CatalogBrowseService catalogBrowseService) {
        this.catalogBrowseService = catalogBrowseService;
    }

    @GetMapping("/products")
    public ProductBrowsePageResponse browseProducts(
            @RequestParam(required = false) @Size(max = 180) String category,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(name = "q", required = false) @Size(max = 200) String query,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return catalogBrowseService.browseProducts(category, status, query, page, size);
    }

    @GetMapping("/products/{productId}")
    public ProductDetailResponse getProduct(@PathVariable UUID productId) {
        return catalogBrowseService.getProduct(productId);
    }
}
