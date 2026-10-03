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
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
    public ResponseEntity<SupplierImportResponse> supplierImportStatus(@PathVariable long executionId) {
        return ResponseEntity.ok(SupplierImportResponse.from(supplierImportControlService.status(executionId)));
    }

    @PostMapping("/categories")
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        CategoryResponse response = catalogAdminService.createCategory(request);
        return ResponseEntity.created(URI.create("/api/catalog/admin/categories/" + response.id()))
                .body(response);
    }

    @PutMapping("/categories/{categoryId}")
    public ResponseEntity<CategoryResponse> updateCategory(
            @PathVariable UUID categoryId,
            @Valid @RequestBody UpdateCategoryRequest request) {
        return ResponseEntity.ok(catalogAdminService.updateCategory(categoryId, request));
    }

    @PatchMapping("/categories/{categoryId}/deactivate")
    public ResponseEntity<CategoryResponse> deactivateCategory(@PathVariable UUID categoryId) {
        return ResponseEntity.ok(catalogAdminService.deactivateCategory(categoryId));
    }

    @PostMapping("/products")
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody CreateProductRequest request) {
        ProductResponse response = catalogAdminService.createProduct(request);
        return ResponseEntity.created(URI.create("/api/catalog/admin/products/" + response.id()))
                .body(response);
    }

    @PutMapping("/products/{productId}")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable UUID productId,
            @Valid @RequestBody UpdateProductRequest request) {
        return ResponseEntity.ok(catalogAdminService.updateProduct(productId, request));
    }

    @PatchMapping("/products/{productId}/deactivate")
    public ResponseEntity<ProductResponse> deactivateProduct(@PathVariable UUID productId) {
        return ResponseEntity.ok(catalogAdminService.deactivateProduct(productId));
    }

    @PostMapping("/products/{productId}/attributes")
    public ResponseEntity<ProductAttributeResponse> createAttribute(
            @PathVariable UUID productId,
            @Valid @RequestBody CreateProductAttributeRequest request) {
        ProductAttributeResponse response = catalogAdminService.createAttribute(productId, request);
        return ResponseEntity.created(URI.create(
                        "/api/catalog/admin/products/" + productId + "/attributes/" + response.id()))
                .body(response);
    }

    @PutMapping("/products/{productId}/attributes/{attributeId}")
    public ResponseEntity<ProductAttributeResponse> updateAttribute(
            @PathVariable UUID productId,
            @PathVariable UUID attributeId,
            @Valid @RequestBody UpdateProductAttributeRequest request) {
        return ResponseEntity.ok(catalogAdminService.updateAttribute(productId, attributeId, request));
    }

    @PatchMapping("/products/{productId}/attributes/{attributeId}/deactivate")
    public ResponseEntity<ProductAttributeResponse> deactivateAttribute(
            @PathVariable UUID productId,
            @PathVariable UUID attributeId) {
        return ResponseEntity.ok(catalogAdminService.deactivateAttribute(productId, attributeId));
    }
}
