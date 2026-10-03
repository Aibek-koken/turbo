package com.kora.ecommerce.catalog.batch;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.SkipListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.batch.item.file.transform.IncorrectLineLengthException;
import org.springframework.batch.item.file.transform.IncorrectTokenCountException;
import org.springframework.util.StringUtils;

public class SupplierImportErrorReportListener
        implements SkipListener<SupplierProductCsvRow, SupplierProductImportCommand>, StepExecutionListener {

    static final String ERROR_REPORT_FILE_PARAMETER = "errorReportFile";
    static final String ERROR_REPORT_FILE_CONTEXT_KEY = "supplierImport.errorReportFile";
    static final String ERROR_REPORT_COUNT_CONTEXT_KEY = "supplierImport.errorReportCount";

    private static final String HEADER = "row_number,reason";

    private final String inputFile;
    private final String errorReportFile;
    private final List<RowError> errors = new ArrayList<>();

    private Path reportPath;

    SupplierImportErrorReportListener(String inputFile, String errorReportFile) {
        this.inputFile = inputFile;
        this.errorReportFile = errorReportFile;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        this.errors.clear();
        this.reportPath = resolveReportPath();
        stepExecution.getExecutionContext().putString(ERROR_REPORT_FILE_CONTEXT_KEY, reportPath.toString());
        stepExecution.getJobExecution().getExecutionContext().putString(ERROR_REPORT_FILE_CONTEXT_KEY, reportPath.toString());
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        writeReport();
        stepExecution.getExecutionContext().putInt(ERROR_REPORT_COUNT_CONTEXT_KEY, errors.size());
        stepExecution.getJobExecution().getExecutionContext().putInt(ERROR_REPORT_COUNT_CONTEXT_KEY, errors.size());
        return null;
    }

    @Override
    public void onSkipInRead(Throwable throwable) {
        if (throwable instanceof FlatFileParseException parseException) {
            errors.add(new RowError(parseException.getLineNumber(), readFailureReason(parseException)));
            return;
        }
        errors.add(new RowError(0, "row could not be read"));
    }

    @Override
    public void onSkipInProcess(SupplierProductCsvRow item, Throwable throwable) {
        if (throwable instanceof SupplierImportRowValidationException validationException) {
            errors.add(new RowError(validationException.getRowNumber(), validationException.getMessage()));
            return;
        }
        errors.add(new RowError(item.rowNumber(), "row could not be processed"));
    }

    @Override
    public void onSkipInWrite(SupplierProductImportCommand item, Throwable throwable) {
        errors.add(new RowError(0, "row could not be written"));
    }

    private Path resolveReportPath() {
        if (StringUtils.hasText(errorReportFile)) {
            return Path.of(errorReportFile).toAbsolutePath().normalize();
        }
        if (!StringUtils.hasText(inputFile)) {
            throw new IllegalArgumentException("Supplier import job parameter is required: "
                    + SupplierImportBatchConfiguration.INPUT_FILE_PARAMETER);
        }
        Path inputPath = Path.of(inputFile).toAbsolutePath().normalize();
        return inputPath.resolveSibling(inputPath.getFileName() + ".errors.csv");
    }

    private void writeReport() {
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        errors.stream()
                .sorted(Comparator
                        .comparingInt(RowError::rowNumber)
                        .thenComparing(RowError::reason))
                .map(error -> error.rowNumber() + "," + csv(error.reason()))
                .forEach(lines::add);

        try {
            Path parent = reportPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(
                    reportPath,
                    lines,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write supplier import error report.", ex);
        }
    }

    private static String readFailureReason(FlatFileParseException parseException) {
        Throwable cause = parseException.getCause();
        if (cause instanceof IncorrectTokenCountException tokenCountException) {
            return "expected " + tokenCountException.getExpectedCount()
                    + " fields but found " + tokenCountException.getActualCount();
        }
        if (cause instanceof IncorrectLineLengthException lineLengthException) {
            return "expected line length " + lineLengthException.getExpectedLength()
                    + " but found " + lineLengthException.getActualLength();
        }
        return "row could not be parsed";
    }

    private static String csv(String value) {
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private record RowError(int rowNumber, String reason) {
    }
}
