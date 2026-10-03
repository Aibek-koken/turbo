package com.kora.ecommerce.catalog.batch;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import com.kora.ecommerce.catalog.api.admin.CatalogAdminException;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionException;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SupplierImportControlService {

    private static final Pattern URI_SCHEME = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*:.*");

    private final JobLauncher jobLauncher;
    private final Job supplierImportJob;
    private final JobExplorer jobExplorer;
    private final SupplierImportBatchProperties properties;
    private final Set<String> localLaunches = ConcurrentHashMap.newKeySet();

    SupplierImportControlService(
            JobLauncher jobLauncher,
            @Qualifier(SupplierImportBatchConfiguration.SUPPLIER_IMPORT_JOB_NAME) Job supplierImportJob,
            JobExplorer jobExplorer,
            SupplierImportBatchProperties properties) {
        this.jobLauncher = jobLauncher;
        this.supplierImportJob = supplierImportJob;
        this.jobExplorer = jobExplorer;
        this.properties = properties;
    }

    public SupplierImportExecutionSummary launch(String requestedImportPath) {
        Path inputFile = resolveImportPath(requestedImportPath);
        String inputKey = inputFile.toString();
        rejectIfAlreadyRunning(inputKey);
        if (!localLaunches.add(inputKey)) {
            throw CatalogAdminException.conflict("Supplier import is already running for this file.");
        }

        try {
            JobExecution execution = jobLauncher.run(supplierImportJob, jobParameters(inputFile));
            return summarize(execution);
        } catch (JobExecutionException ex) {
            throw CatalogAdminException.conflict("Supplier import could not be launched in its current state.");
        } finally {
            localLaunches.remove(inputKey);
        }
    }

    public SupplierImportExecutionSummary status(long executionId) {
        JobExecution execution = jobExplorer.getJobExecution(executionId);
        if (execution == null) {
            throw CatalogAdminException.notFound("Supplier import execution not found.");
        }
        return summarize(execution);
    }

    private void rejectIfAlreadyRunning(String inputKey) {
        boolean sameFileRunning = jobExplorer
                .findRunningJobExecutions(SupplierImportBatchConfiguration.SUPPLIER_IMPORT_JOB_NAME)
                .stream()
                .map(JobExecution::getJobParameters)
                .map(parameters -> parameters.getString(SupplierImportBatchConfiguration.INPUT_FILE_PARAMETER))
                .filter(StringUtils::hasText)
                .map(this::normalizeExistingInputParameter)
                .anyMatch(inputKey::equals);
        if (sameFileRunning) {
            throw CatalogAdminException.conflict("Supplier import is already running for this file.");
        }
    }

    private JobParameters jobParameters(Path inputFile) {
        Path errorReportFile = inputFile.resolveSibling(inputFile.getFileName() + ".errors.csv");
        return new JobParametersBuilder()
                .addString(SupplierImportBatchConfiguration.INPUT_FILE_PARAMETER, inputFile.toString(), true)
                .addString(
                        SupplierImportBatchConfiguration.ERROR_REPORT_FILE_PARAMETER,
                        errorReportFile.toString(),
                        false)
                .toJobParameters();
    }

    private Path resolveImportPath(String requestedImportPath) {
        if (!StringUtils.hasText(requestedImportPath)) {
            throw CatalogAdminException.badRequest("Supplier import path is required.");
        }

        String trimmedPath = requestedImportPath.trim();
        if (trimmedPath.indexOf('\0') >= 0
                || trimmedPath.contains("://")
                || URI_SCHEME.matcher(trimmedPath).matches()) {
            throw CatalogAdminException.badRequest("Supplier import path must be a relative local CSV file.");
        }

        Path relativePath;
        try {
            relativePath = Path.of(trimmedPath);
        } catch (InvalidPathException ex) {
            throw CatalogAdminException.badRequest("Supplier import path has an invalid format.");
        }

        if (relativePath.isAbsolute()) {
            throw CatalogAdminException.badRequest("Supplier import path must be relative to the import directory.");
        }

        Path normalizedRelativePath = relativePath.normalize();
        if (normalizedRelativePath.getFileName() == null
                || normalizedRelativePath.startsWith("..")
                || !normalizedRelativePath.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw CatalogAdminException.badRequest("Supplier import path must stay inside the import directory and end in .csv.");
        }

        Path importDirectory = properties.normalizedImportDirectory();
        Path resolvedPath = importDirectory.resolve(normalizedRelativePath).normalize();
        if (!resolvedPath.startsWith(importDirectory)) {
            throw CatalogAdminException.badRequest("Supplier import path must stay inside the import directory.");
        }

        if (!Files.isRegularFile(resolvedPath, LinkOption.NOFOLLOW_LINKS)) {
            throw CatalogAdminException.notFound("Supplier import file was not found in the import directory.");
        }

        rejectEscapingRealPath(importDirectory, resolvedPath);
        return resolvedPath;
    }

    private void rejectEscapingRealPath(Path importDirectory, Path resolvedPath) {
        try {
            Path realDirectory = importDirectory.toRealPath();
            Path realInputFile = resolvedPath.toRealPath();
            if (!realInputFile.startsWith(realDirectory)) {
                throw CatalogAdminException.badRequest("Supplier import path must stay inside the import directory.");
            }
        } catch (CatalogAdminException ex) {
            throw ex;
        } catch (Exception ex) {
            throw CatalogAdminException.notFound("Supplier import file was not found in the import directory.");
        }
    }

    private SupplierImportExecutionSummary summarize(JobExecution execution) {
        long processedCount = 0;
        long skippedCount = 0;
        for (StepExecution stepExecution : execution.getStepExecutions()) {
            processedCount += stepExecution.getWriteCount();
            skippedCount += stepExecution.getReadSkipCount()
                    + stepExecution.getProcessSkipCount()
                    + stepExecution.getWriteSkipCount();
        }

        return new SupplierImportExecutionSummary(
                execution.getId(),
                execution.getStatus().name(),
                execution.getExitStatus().getExitCode(),
                processedCount,
                skippedCount,
                execution.getAllFailureExceptions().size(),
                sanitizeErrorReportLocation(errorReportPath(execution)));
    }

    private String errorReportPath(JobExecution execution) {
        if (execution.getExecutionContext().containsKey(
                SupplierImportErrorReportListener.ERROR_REPORT_FILE_CONTEXT_KEY)) {
            return execution.getExecutionContext().getString(
                    SupplierImportErrorReportListener.ERROR_REPORT_FILE_CONTEXT_KEY);
        }
        for (StepExecution stepExecution : execution.getStepExecutions()) {
            if (stepExecution.getExecutionContext().containsKey(
                    SupplierImportErrorReportListener.ERROR_REPORT_FILE_CONTEXT_KEY)) {
                return stepExecution.getExecutionContext().getString(
                        SupplierImportErrorReportListener.ERROR_REPORT_FILE_CONTEXT_KEY);
            }
        }

        String inputFile = execution.getJobParameters()
                .getString(SupplierImportBatchConfiguration.INPUT_FILE_PARAMETER);
        if (!StringUtils.hasText(inputFile)) {
            return null;
        }
        Path inputPath = Path.of(inputFile).toAbsolutePath().normalize();
        return inputPath.resolveSibling(inputPath.getFileName() + ".errors.csv").toString();
    }

    private String sanitizeErrorReportLocation(String reportPath) {
        if (!StringUtils.hasText(reportPath)) {
            return null;
        }
        Path importDirectory = properties.normalizedImportDirectory();
        Path normalizedReportPath = Path.of(reportPath).toAbsolutePath().normalize();
        if (normalizedReportPath.startsWith(importDirectory)) {
            return importDirectory.relativize(normalizedReportPath).toString().replace('\\', '/');
        }
        Path fileName = normalizedReportPath.getFileName();
        return fileName == null ? null : fileName.toString();
    }

    private String normalizeExistingInputParameter(String inputFile) {
        try {
            return Path.of(inputFile).toAbsolutePath().normalize().toString();
        } catch (InvalidPathException ex) {
            return inputFile;
        }
    }
}
