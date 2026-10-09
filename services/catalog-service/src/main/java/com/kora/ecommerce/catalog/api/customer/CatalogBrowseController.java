package com.kora.ecommerce.catalog.api.customer;

import java.util.UUID;

import com.kora.ecommerce.catalog.api.customer.CatalogBrowseDtos.ProductBrowsePageResponse;
import com.kora.ecommerce.catalog.api.customer.CatalogBrowseDtos.ProductDetailResponse;
import com.kora.ecommerce.catalog.domain.ProductStatus;
import com.kora.ecommerce.catalog.service.CatalogBrowseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/catalog")
@Tag(name = "Catalog browse", description = "Customer-visible product browse and detail APIs.")
@SecurityRequirement(name = "bearer-jwt")
public class CatalogBrowseController {

    private final CatalogBrowseService catalogBrowseService;

    public CatalogBrowseController(CatalogBrowseService catalogBrowseService) {
        this.catalogBrowseService = catalogBrowseService;
    }

    @GetMapping("/products")
    @Operation(
            operationId = "browseCatalogProducts",
            summary = "Browse customer-visible products",
            description = "Requires CUSTOMER, CATALOG_ADMIN or OPS_ADMIN. Supports optional category, status, "
                    + "text query and zero-based page/size pagination.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paged product summaries"),
            @ApiResponse(responseCode = "400", description = "Invalid filters or pagination",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Bearer token lacks an allowed role",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ProductBrowsePageResponse browseProducts(
            @Parameter(description = "Active category slug to filter by.")
            @RequestParam(required = false) @Size(max = 180) String category,
            @Parameter(description = "Customer browse supports ACTIVE when supplied.")
            @RequestParam(required = false) ProductStatus status,
            @Parameter(description = "Case-insensitive product name, description or SKU search text.")
            @RequestParam(name = "q", required = false) @Size(max = 200) String query,
            @Parameter(description = "Zero-based result page.")
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @Parameter(description = "Page size from 1 to 100.")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return catalogBrowseService.browseProducts(category, status, query, page, size);
    }

    @GetMapping("/products/{productId}")
    @Operation(
            operationId = "getCatalogProduct",
            summary = "Get a customer-visible product detail",
            description = "Requires CUSTOMER, CATALOG_ADMIN or OPS_ADMIN. Draft or inactive products are not exposed.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product detail"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Bearer token lacks an allowed role",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Product is missing or not customer-visible",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ProductDetailResponse getProduct(
            @Parameter(description = "Catalog product identifier.")
            @PathVariable UUID productId) {
        return catalogBrowseService.getProduct(productId);
    }
}
