package com.kora.ecommerce.catalog.batch;

import java.nio.file.Path;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "catalog.supplier-import")
public record SupplierImportBatchProperties(
        @Min(1) @Max(1000) int chunkSize,
        Path importDirectory) {

    private static final int DEFAULT_CHUNK_SIZE = 50;
    private static final Path DEFAULT_IMPORT_DIRECTORY = Path.of("var/catalog/imports");

    public SupplierImportBatchProperties {
        if (chunkSize == 0) {
            chunkSize = DEFAULT_CHUNK_SIZE;
        }
        if (importDirectory == null) {
            importDirectory = DEFAULT_IMPORT_DIRECTORY;
        }
    }

    Path normalizedImportDirectory() {
        return importDirectory.toAbsolutePath().normalize();
    }
}
