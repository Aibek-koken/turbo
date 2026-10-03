package com.kora.ecommerce.catalog.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import com.kora.ecommerce.catalog.api.admin.CatalogAdminException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;

class SupplierImportControlServiceTest {

    @TempDir
    private Path tempDir;

    @Test
    void defaultsImportDirectoryWhenNotConfigured() {
        SupplierImportBatchProperties properties = new SupplierImportBatchProperties(0, null);

        assertThat(properties.chunkSize()).isEqualTo(50);
        assertThat(properties.importDirectory()).isEqualTo(Path.of("var/catalog/imports"));
    }

    @Test
    void rejectsDuplicateLaunchForRunningSupplierFile() throws Exception {
        Path inputFile = tempDir.resolve("supplier-products.csv").toAbsolutePath().normalize();
        Files.writeString(inputFile, "sku,product_name\n");

        JobLauncher jobLauncher = mock(JobLauncher.class);
        Job supplierImportJob = mock(Job.class);
        JobExplorer jobExplorer = mock(JobExplorer.class);
        SupplierImportBatchProperties properties = new SupplierImportBatchProperties(10, tempDir);
        SupplierImportControlService service = new SupplierImportControlService(
                jobLauncher,
                supplierImportJob,
                jobExplorer,
                properties);

        JobExecution runningExecution = mock(JobExecution.class);
        JobParameters runningParameters = new JobParametersBuilder()
                .addString(SupplierImportBatchConfiguration.INPUT_FILE_PARAMETER, inputFile.toString(), true)
                .toJobParameters();
        when(runningExecution.getJobParameters()).thenReturn(runningParameters);
        when(jobExplorer.findRunningJobExecutions(SupplierImportBatchConfiguration.SUPPLIER_IMPORT_JOB_NAME))
                .thenReturn(Set.of(runningExecution));

        assertThatThrownBy(() -> service.launch("supplier-products.csv"))
                .isInstanceOf(CatalogAdminException.class)
                .hasMessage("Supplier import is already running for this file.");

        verify(jobLauncher, never()).run(any(), any());
    }
}
