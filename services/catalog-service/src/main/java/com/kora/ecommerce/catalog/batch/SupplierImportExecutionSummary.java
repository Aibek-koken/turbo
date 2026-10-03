package com.kora.ecommerce.catalog.batch;

public record SupplierImportExecutionSummary(
        long executionId,
        String status,
        String exitCode,
        long processedCount,
        long skippedCount,
        long failedCount,
        String errorReportLocation) {
}
