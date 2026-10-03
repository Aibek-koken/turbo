package com.kora.ecommerce.catalog.batch;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.SkipListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.batch.item.file.LineMapper;
import org.springframework.batch.item.file.transform.DelimitedLineTokenizer;
import org.springframework.batch.item.file.transform.FieldSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties(SupplierImportBatchProperties.class)
class SupplierImportBatchConfiguration {

    static final String SUPPLIER_IMPORT_JOB_NAME = "supplierImportJob";
    static final String SUPPLIER_IMPORT_STEP_NAME = "supplierImportStep";
    static final String SUPPLIER_IMPORT_READER_NAME = "supplierProductCsvReader";
    static final String INPUT_FILE_PARAMETER = "inputFile";
    static final String ERROR_REPORT_FILE_PARAMETER = SupplierImportErrorReportListener.ERROR_REPORT_FILE_PARAMETER;

    private static final String[] SUPPLIER_CSV_FIELD_NAMES = {
            "sku",
            "product_name",
            "product_description",
            "category_slug",
            "category_name",
            "category_description",
            "price_amount",
            "currency",
            "status",
            "attributes"
    };

    @Bean(name = SUPPLIER_IMPORT_JOB_NAME)
    Job supplierImportJob(JobRepository jobRepository, Step supplierImportStep) {
        return new JobBuilder(SUPPLIER_IMPORT_JOB_NAME, jobRepository)
                .start(supplierImportStep)
                .build();
    }

    @Bean(name = SUPPLIER_IMPORT_STEP_NAME)
    Step supplierImportStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            SupplierImportBatchProperties properties,
            FlatFileItemReader<SupplierProductCsvRow> supplierProductCsvReader,
            ItemProcessor<SupplierProductCsvRow, SupplierProductImportCommand> supplierProductCsvProcessor,
            ItemWriter<SupplierProductImportCommand> supplierProductImportWriter,
            SupplierImportErrorReportListener supplierImportErrorReportListener) {
        return new StepBuilder(SUPPLIER_IMPORT_STEP_NAME, jobRepository)
                .<SupplierProductCsvRow, SupplierProductImportCommand>chunk(properties.chunkSize(), transactionManager)
                .reader(supplierProductCsvReader)
                .processor(supplierProductCsvProcessor)
                .writer(supplierProductImportWriter)
                .faultTolerant()
                .skip(SupplierImportRowValidationException.class)
                .skip(FlatFileParseException.class)
                .skipLimit(Integer.MAX_VALUE)
                .listener((SkipListener<SupplierProductCsvRow, SupplierProductImportCommand>)
                        supplierImportErrorReportListener)
                .listener((StepExecutionListener) supplierImportErrorReportListener)
                .build();
    }

    @Bean(name = SUPPLIER_IMPORT_READER_NAME)
    @StepScope
    FlatFileItemReader<SupplierProductCsvRow> supplierProductCsvReader(
            @Value("#{jobParameters['" + INPUT_FILE_PARAMETER + "']}") String inputFile) {
        if (!StringUtils.hasText(inputFile)) {
            throw new IllegalArgumentException("Supplier import job parameter is required: " + INPUT_FILE_PARAMETER);
        }

        DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer();
        tokenizer.setNames(SUPPLIER_CSV_FIELD_NAMES);
        tokenizer.setStrict(true);

        LineMapper<SupplierProductCsvRow> lineMapper = (line, lineNumber) ->
                mapSupplierCsvRow(tokenizer.tokenize(line), lineNumber);

        FlatFileItemReader<SupplierProductCsvRow> reader = new FlatFileItemReader<>();
        reader.setName(SUPPLIER_IMPORT_READER_NAME);
        reader.setResource(new FileSystemResource(inputFile));
        reader.setLinesToSkip(1);
        reader.setLineMapper(lineMapper);
        reader.setSaveState(true);
        return reader;
    }

    @Bean
    ItemWriter<SupplierProductImportCommand> supplierProductImportWriter(
            SupplierProductImportService supplierProductImportService) {
        return chunk -> supplierProductImportService.importRows(chunk.getItems());
    }

    @Bean
    @StepScope
    SupplierImportErrorReportListener supplierImportErrorReportListener(
            @Value("#{jobParameters['" + INPUT_FILE_PARAMETER + "']}") String inputFile,
            @Value("#{jobParameters['" + ERROR_REPORT_FILE_PARAMETER + "']}") String errorReportFile) {
        return new SupplierImportErrorReportListener(inputFile, errorReportFile);
    }

    private static SupplierProductCsvRow mapSupplierCsvRow(FieldSet fieldSet, int rowNumber) {
        return new SupplierProductCsvRow(
                rowNumber,
                fieldSet.readString("sku"),
                fieldSet.readString("product_name"),
                fieldSet.readString("product_description"),
                fieldSet.readString("category_slug"),
                fieldSet.readString("category_name"),
                fieldSet.readString("category_description"),
                fieldSet.readString("price_amount"),
                fieldSet.readString("currency"),
                fieldSet.readString("status"),
                fieldSet.readString("attributes"));
    }
}
