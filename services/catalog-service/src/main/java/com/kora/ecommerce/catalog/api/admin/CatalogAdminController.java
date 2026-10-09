package com.kora.ecommerce.catalog.api.admin;

import java.net.URI;
import java.util.UUID;

import com.kora.ecommerce.catalog.api.admin.CatalogAdminDtos.CategoryResponse;
import com.kora.ecommerce.catalog.api.admin.CatalogAdminDtos.CreateCategoryRequest;
import com.kora.ecommerce.catalog.api.admin.CatalogAdminDtos.CreateProductAttributeRequest;
import com.kora.ecommerce.catalog.api.admin.CatalogAdminDtos.CreateProductRequest;
import com.kora.ecommerce.catalog.api.admin.CatalogAdminDtos.ProductAttributeResponse;
import com.kora.ecommerce.catalog.api.admin.CatalogAdminDtos.ProductResponse;
import com.kora.ecommerce.catalog.api.admin.CatalogAdminDtos.SupplierImportLaunchRequest;
import com.kora.ecommerce.catalog.api.admin.CatalogAdminDtos.SupplierImportResponse;
import com.kora.ecommerce.catalog.api.admin.CatalogAdminDtos.UpdateCategoryRequest;
import com.kora.ecommerce.catalog.api.admin.CatalogAdminDtos.UpdateProductAttributeRequest;
import com.kora.ecommerce.catalog.api.admin.CatalogAdminDtos.UpdateProductRequest;
import com.kora.ecommerce.catalog.batch.SupplierImportControlService;
import com.kora.ecommerce.catalog.service.CatalogAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog/admin")
@PreAuthorize("hasRole('CATALOG_ADMIN')")
@Tag(name = "Catalog admin", description = "Catalog administration APIs for categories, products and supplier imports.")
@SecurityRequirement(name = "bearer-jwt")
public class CatalogAdminController {

    private final CatalogAdminService catalogAdminService;
    private final SupplierImportControlService supplierImportControlService;

    public CatalogAdminController(
            CatalogAdminService catalogAdminService,
            SupplierImportControlService supplierImportControlService) {
        this.catalogAdminService = catalogAdminService;
        this.supplierImportControlService = supplierImportControlService;
    }

    @PostMapping("/supplier-imports")
    @Operation(
            operationId = "launchCatalogSupplierImport",
            summary = "Launch a supplier CSV import",
            description = "Requires CATALOG_ADMIN. The request references a local import file under the configured "
                    + "supplier import directory.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Supplier import accepted"),
            @ApiResponse(responseCode = "400", description = "Invalid import request",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Bearer token lacks CATALOG_ADMIN",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<SupplierImportResponse> launchSupplierImport(
            @Valid @RequestBody SupplierImportLaunchRequest request) {
        SupplierImportResponse response = SupplierImportResponse.from(
                supplierImportControlService.launch(request.importPath()));
        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .location(URI.create("/api/catalog/admin/supplier-imports/" + response.executionId()))
                .body(response);
    }

    @GetMapping("/supplier-imports/{executionId}")
    @Operation(
            operationId = "getCatalogSupplierImportStatus",
            summary = "Get supplier import status",
            description = "Requires CATALOG_ADMIN.")
    public ResponseEntity<SupplierImportResponse> supplierImportStatus(
            @Parameter(description = "Spring Batch execution identifier.")
            @PathVariable long executionId) {
        return ResponseEntity.ok(SupplierImportResponse.from(supplierImportControlService.status(executionId)));
    }

    @PostMapping("/categories")
    @Operation(
            operationId = "createCatalogCategory",
            summary = "Create a catalog category",
            description = "Requires CATALOG_ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Category created"),
            @ApiResponse(responseCode = "400", description = "Invalid category request",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Bearer token lacks CATALOG_ADMIN",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        CategoryResponse response = catalogAdminService.createCategory(request);
        return ResponseEntity.created(URI.create("/api/catalog/admin/categories/" + response.id()))
                .body(response);
    }

    @PutMapping("/categories/{categoryId}")
    @Operation(
            operationId = "updateCatalogCategory",
            summary = "Update a catalog category",
            description = "Requires CATALOG_ADMIN.")
    public ResponseEntity<CategoryResponse> updateCategory(
            @Parameter(description = "Catalog category identifier.")
            @PathVariable UUID categoryId,
            @Valid @RequestBody UpdateCategoryRequest request) {
        return ResponseEntity.ok(catalogAdminService.updateCategory(categoryId, request));
    }

    @PatchMapping("/categories/{categoryId}/deactivate")
    @Operation(
            operationId = "deactivateCatalogCategory",
            summary = "Deactivate a catalog category",
            description = "Requires CATALOG_ADMIN.")
    public ResponseEntity<CategoryResponse> deactivateCategory(
            @Parameter(description = "Catalog category identifier.")
            @PathVariable UUID categoryId) {
        return ResponseEntity.ok(catalogAdminService.deactivateCategory(categoryId));
    }

    @PostMapping("/products")
    @Operation(
            operationId = "createCatalogProduct",
            summary = "Create a catalog product",
            description = "Requires CATALOG_ADMIN. Optional initial attributes may be supplied inline.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Product created"),
            @ApiResponse(responseCode = "400", description = "Invalid product request",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Bearer token lacks CATALOG_ADMIN",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody CreateProductRequest request) {
        ProductResponse response = catalogAdminService.createProduct(request);
        return ResponseEntity.created(URI.create("/api/catalog/admin/products/" + response.id()))
                .body(response);
    }

    @PutMapping("/products/{productId}")
    @Operation(
            operationId = "updateCatalogProduct",
            summary = "Update a catalog product",
            description = "Requires CATALOG_ADMIN.")
    public ResponseEntity<ProductResponse> updateProduct(
            @Parameter(description = "Catalog product identifier.")
            @PathVariable UUID productId,
            @Valid @RequestBody UpdateProductRequest request) {
        return ResponseEntity.ok(catalogAdminService.updateProduct(productId, request));
    }

    @PatchMapping("/products/{productId}/deactivate")
    @Operation(
            operationId = "deactivateCatalogProduct",
            summary = "Deactivate a catalog product",
            description = "Requires CATALOG_ADMIN.")
    public ResponseEntity<ProductResponse> deactivateProduct(
            @Parameter(description = "Catalog product identifier.")
            @PathVariable UUID productId) {
        return ResponseEntity.ok(catalogAdminService.deactivateProduct(productId));
    }

    @PostMapping("/products/{productId}/attributes")
    @Operation(
            operationId = "createCatalogProductAttribute",
            summary = "Create a product attribute",
            description = "Requires CATALOG_ADMIN.")
    public ResponseEntity<ProductAttributeResponse> createAttribute(
            @Parameter(description = "Catalog product identifier.")
            @PathVariable UUID productId,
            @Valid @RequestBody CreateProductAttributeRequest request) {
        ProductAttributeResponse response = catalogAdminService.createAttribute(productId, request);
        return ResponseEntity.created(URI.create(
                        "/api/catalog/admin/products/" + productId + "/attributes/" + response.id()))
                .body(response);
    }

    @PutMapping("/products/{productId}/attributes/{attributeId}")
    @Operation(
            operationId = "updateCatalogProductAttribute",
            summary = "Update a product attribute",
            description = "Requires CATALOG_ADMIN.")
    public ResponseEntity<ProductAttributeResponse> updateAttribute(
            @Parameter(description = "Catalog product identifier.")
            @PathVariable UUID productId,
            @Parameter(description = "Catalog product attribute identifier.")
            @PathVariable UUID attributeId,
            @Valid @RequestBody UpdateProductAttributeRequest request) {
        return ResponseEntity.ok(catalogAdminService.updateAttribute(productId, attributeId, request));
    }

    @PatchMapping("/products/{productId}/attributes/{attributeId}/deactivate")
    @Operation(
            operationId = "deactivateCatalogProductAttribute",
            summary = "Deactivate a product attribute",
            description = "Requires CATALOG_ADMIN.")
    public ResponseEntity<ProductAttributeResponse> deactivateAttribute(
            @Parameter(description = "Catalog product identifier.")
            @PathVariable UUID productId,
            @Parameter(description = "Catalog product attribute identifier.")
            @PathVariable UUID attributeId) {
        return ResponseEntity.ok(catalogAdminService.deactivateAttribute(productId, attributeId));
    }
}
