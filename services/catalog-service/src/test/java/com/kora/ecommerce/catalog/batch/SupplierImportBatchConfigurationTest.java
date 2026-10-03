package com.kora.ecommerce.catalog.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import com.kora.ecommerce.catalog.cache.ProductDetailCache;
import com.kora.ecommerce.catalog.domain.Category;
import com.kora.ecommerce.catalog.domain.Product;
import com.kora.ecommerce.catalog.domain.ProductAttribute;
import com.kora.ecommerce.catalog.domain.ProductStatus;
import com.kora.ecommerce.catalog.repository.CategoryRepository;
import com.kora.ecommerce.catalog.repository.ProductAttributeRepository;
import com.kora.ecommerce.catalog.repository.ProductRepository;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

@SpringBatchTest
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:catalog_supplier_import;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.batch.job.enabled=false",
                "catalog.cache.enabled=false",
                "catalog.supplier-import.chunk-size=2",
                "catalog.supplier-import.import-directory=target/supplier-import-control-test",
                "management.health.redis.enabled=false"
        })
class SupplierImportBatchConfigurationTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private JobRepositoryTestUtils jobRepositoryTestUtils;

    @Autowired
    @Qualifier(SupplierImportBatchConfiguration.SUPPLIER_IMPORT_JOB_NAME)
    private Job supplierImportJob;

    @Autowired
    @Qualifier(SupplierImportBatchConfiguration.SUPPLIER_IMPORT_STEP_NAME)
    private Step supplierImportStep;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductAttributeRepository productAttributeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SupplierImportControlService supplierImportControlService;

    @MockBean
    private ProductDetailCache productDetailCache;

    @SpyBean
    private SupplierProductImportService supplierProductImportService;

    @TempDir
    private Path tempDir;

    private final Path importDirectory = Path.of("target/supplier-import-control-test").toAbsolutePath().normalize();

    @BeforeEach
    void resetCatalog() throws Exception {
        Files.createDirectories(importDirectory);
        jobLauncherTestUtils.setJob(supplierImportJob);
        jobRepositoryTestUtils.removeJobExecutions();
        reset(productDetailCache);
        reset(supplierProductImportService);
        productAttributeRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
        categoryRepository.deleteAllInBatch();
    }

    @Test
    void supplierImportJobAndStepAreRegistered() {
        assertThat(supplierImportJob.getName())
                .isEqualTo(SupplierImportBatchConfiguration.SUPPLIER_IMPORT_JOB_NAME);
        assertThat(supplierImportStep.getName())
                .isEqualTo(SupplierImportBatchConfiguration.SUPPLIER_IMPORT_STEP_NAME);
    }

    @Test
    void validSupplierCsvIsProcessedInChunks() throws Exception {
        JobExecution execution = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addString(
                        SupplierImportBatchConfiguration.INPUT_FILE_PARAMETER,
                        sampleCsvPath().toString())
                .addString("importId", UUID.randomUUID().toString())
                .toJobParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        StepExecution stepExecution = execution.getStepExecutions().iterator().next();
        assertThat(stepExecution.getStepName())
                .isEqualTo(SupplierImportBatchConfiguration.SUPPLIER_IMPORT_STEP_NAME);
        assertThat(stepExecution.getReadCount()).isEqualTo(3);
        assertThat(stepExecution.getWriteCount()).isEqualTo(3);
        assertThat(stepExecution.getCommitCount()).isGreaterThanOrEqualTo(2);

        assertThat(categoryRepository.findBySlug("coffee")).isPresent();
        assertThat(categoryRepository.findBySlug("mugs")).isPresent();
        assertThat(productRepository.findAll()).hasSize(3);
        assertThat(productAttributeRepository.findAll()).hasSize(4);

        var coffee = productRepository.findDetailsBySku("COF-001").orElseThrow();
        assertThat(coffee.getStatus()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(coffee.getPriceAmount()).isEqualByComparingTo("14.50");
        assertThat(coffee.getCategory().getSlug()).isEqualTo("coffee");
        assertThat(coffee.getAttributes())
                .extracting(ProductAttribute::getAttributeKey)
                .containsExactlyInAnyOrder("origin", "roast");

        assertThat(jdbcTemplate.queryForObject("select count(*) from BATCH_JOB_EXECUTION", Long.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from BATCH_STEP_EXECUTION", Long.class))
                .isEqualTo(1);
    }

    @Test
    void mixedValidAndInvalidRowsCompleteWithDeterministicErrorReport() throws Exception {
        Path input = writeCsv("mixed-supplier-products.csv", """
                sku,product_name,product_description,category_slug,category_name,category_description,price_amount,currency,status,attributes
                MIX-001,Valid Product,Ready,mixed,Mixed,Valid rows,10.00,usd,ACTIVE,color=black
                NEG-001,Negative Product,Invalid,mixed,Mixed,Valid rows,-1.00,USD,ACTIVE,color=red
                CUR-001,Bad Currency,Invalid,mixed,Mixed,Valid rows,1.00,US,ACTIVE,color=red
                BAD-001,Bad Status,Invalid,mixed,Mixed,Valid rows,1.00,USD,ARCHIVED,color=red
                SHORT-001,Too Few Fields
                MIX-002,Second Valid,Ready,mixed,Mixed,Valid rows,11.00,USD,DRAFT,color=white
                """);
        Path errorReport = tempDir.resolve("mixed-errors.csv");

        JobExecution execution = jobLauncherTestUtils.launchJob(importParameters(input, errorReport));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        StepExecution stepExecution = execution.getStepExecutions().iterator().next();
        assertThat(stepExecution.getReadCount()).isEqualTo(5);
        assertThat(stepExecution.getReadSkipCount()).isEqualTo(1);
        assertThat(stepExecution.getProcessSkipCount()).isEqualTo(3);
        assertThat(stepExecution.getWriteCount()).isEqualTo(2);

        assertThat(productRepository.findAll())
                .extracting(Product::getSku)
                .containsExactlyInAnyOrder("MIX-001", "MIX-002");
        assertThat(productRepository.findDetailsBySku("MIX-001").orElseThrow().getCurrency())
                .isEqualTo("USD");

        assertThat(Files.readAllLines(errorReport))
                .containsExactly(
                        "row_number,reason",
                        "3,\"price_amount must be non-negative\"",
                        "4,\"currency must be a 3-letter currency code\"",
                        "5,\"status must be one of ACTIVE, DRAFT, INACTIVE\"",
                        "6,\"expected 10 fields but found 2\"");
    }

    @Test
    void existingSkuAndCategoryAreUpdatedWithoutCreatingDuplicates() throws Exception {
        Category category = categoryRepository.saveAndFlush(new Category("Old Coffee", "coffee"));
        Product product = new Product(category, "COF-UPD", "Old Name", new BigDecimal("9.00"), "USD");
        product.setDescription("Old description");
        product.setStatus(ProductStatus.DRAFT);
        product.addAttribute(new ProductAttribute("roast", "light"));
        Product existingProduct = productRepository.saveAndFlush(product);

        Path input = writeCsv("update-supplier-products.csv", """
                sku,product_name,product_description,category_slug,category_name,category_description,price_amount,currency,status,attributes
                COF-UPD,Updated Name,Updated description,coffee,Coffee,Fresh beans,12.25,eur,ACTIVE,roast=dark;origin=brazil
                """);
        Path errorReport = tempDir.resolve("update-errors.csv");

        JobExecution execution = jobLauncherTestUtils.launchJob(importParameters(input, errorReport));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(categoryRepository.findAll()).hasSize(1);
        assertThat(productRepository.findAll()).hasSize(1);

        Product updated = productRepository.findDetailsBySku("COF-UPD").orElseThrow();
        assertThat(updated.getId()).isEqualTo(existingProduct.getId());
        assertThat(updated.getName()).isEqualTo("Updated Name");
        assertThat(updated.getDescription()).isEqualTo("Updated description");
        assertThat(updated.getPriceAmount()).isEqualByComparingTo("12.25");
        assertThat(updated.getCurrency()).isEqualTo("EUR");
        assertThat(updated.getStatus()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(updated.getCategory().getName()).isEqualTo("Coffee");
        assertThat(updated.getAttributes())
                .extracting(ProductAttribute::getAttributeKey, ProductAttribute::getAttributeValue)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("roast", "dark"),
                        org.assertj.core.groups.Tuple.tuple("origin", "brazil"));
        assertThat(Files.readAllLines(errorReport)).containsExactly("row_number,reason");
    }

    @Test
    void duplicateSkuRowsInSameFileUpdateOneProduct() throws Exception {
        Path input = writeCsv("duplicate-sku-supplier-products.csv", """
                sku,product_name,product_description,category_slug,category_name,category_description,price_amount,currency,status,attributes
                DUP-001,First Version,First,alpha,Alpha,First category,5.00,USD,DRAFT,color=red
                DUP-001,Final Version,Second,beta,Beta,Second category,7.50,USD,ACTIVE,color=blue
                """);
        Path errorReport = tempDir.resolve("duplicate-errors.csv");

        JobExecution execution = jobLauncherTestUtils.launchJob(importParameters(input, errorReport));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(productRepository.findAll()).hasSize(1);
        assertThat(categoryRepository.findBySlug("alpha")).isPresent();
        assertThat(categoryRepository.findBySlug("beta")).isPresent();

        Product product = productRepository.findDetailsBySku("DUP-001").orElseThrow();
        assertThat(product.getName()).isEqualTo("Final Version");
        assertThat(product.getCategory().getSlug()).isEqualTo("beta");
        assertThat(product.getPriceAmount()).isEqualByComparingTo("7.50");
        assertThat(product.getStatus()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(product.getAttributes())
                .extracting(ProductAttribute::getAttributeKey, ProductAttribute::getAttributeValue)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("color", "blue"));
        assertThat(Files.readAllLines(errorReport)).containsExactly("row_number,reason");
    }

    @Test
    void changedProductsAreEvictedFromProductDetailCacheAfterCommit() throws Exception {
        Category category = categoryRepository.saveAndFlush(new Category("Cache Category", "cache"));
        Product product = productRepository.saveAndFlush(new Product(
                category,
                "CACHE-001",
                "Cached Product",
                new BigDecimal("20.00"),
                "USD"));

        Path input = writeCsv("cache-eviction-supplier-products.csv", """
                sku,product_name,product_description,category_slug,category_name,category_description,price_amount,currency,status,attributes
                CACHE-001,Cache Bust,Updated,cache,Cache Category,Cache imports,21.00,USD,ACTIVE,color=green
                """);

        JobExecution execution = jobLauncherTestUtils.launchJob(importParameters(input, tempDir.resolve("cache-errors.csv")));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        verify(productDetailCache, times(1)).evict(product.getId());
        verify(productDetailCache, never()).evict(null);
    }

    @Test
    void failedControlLaunchRestartsSameFileWithoutDuplicatingCommittedRows() throws Exception {
        String importPath = "restart-" + UUID.randomUUID() + ".csv";
        writeImportCsv(importDirectory.resolve(importPath), """
                sku,product_name,product_description,category_slug,category_name,category_description,price_amount,currency,status,attributes
                RST-001,Restart One,First,restart,Restart,Restart category,10.00,USD,ACTIVE,color=blue
                RST-002,Restart Two,Second,restart,Restart,Restart category,11.00,USD,ACTIVE,color=green
                RST-003,Restart Three,Third,restart,Restart,Restart category,12.00,USD,ACTIVE,color=red
                """);

        AtomicBoolean failedOnce = new AtomicBoolean(false);
        doAnswer(invocation -> {
            Collection<? extends SupplierProductImportCommand> rows = invocation.getArgument(0);
            boolean failingChunk = rows.stream().anyMatch(row -> "RST-003".equals(row.sku()));
            if (failingChunk && failedOnce.compareAndSet(false, true)) {
                throw new IllegalStateException("Injected supplier import chunk failure");
            }
            return invocation.callRealMethod();
        }).when(supplierProductImportService).importRows(anyCollection());

        SupplierImportExecutionSummary failed = supplierImportControlService.launch(importPath);

        assertThat(failed.status()).isEqualTo(BatchStatus.FAILED.name());
        assertThat(failed.processedCount()).isEqualTo(2);
        assertThat(failed.skippedCount()).isZero();
        assertThat(failed.failedCount()).isEqualTo(1);
        assertThat(failed.errorReportLocation()).isEqualTo(importPath + ".errors.csv");
        assertThat(productRepository.findAll())
                .extracting(Product::getSku)
                .containsExactlyInAnyOrder("RST-001", "RST-002");

        SupplierImportExecutionSummary restarted = supplierImportControlService.launch(importPath);

        assertThat(restarted.status()).isEqualTo(BatchStatus.COMPLETED.name());
        assertThat(restarted.processedCount()).isEqualTo(1);
        assertThat(restarted.skippedCount()).isZero();
        assertThat(restarted.failedCount()).isZero();
        assertThat(restarted.errorReportLocation()).isEqualTo(importPath + ".errors.csv");
        assertThat(productRepository.findAll())
                .extracting(Product::getSku)
                .containsExactlyInAnyOrder("RST-001", "RST-002", "RST-003");
        assertThat(productRepository.findAll()).hasSize(3);
    }

    private JobParameters importParameters(Path inputFile, Path errorReportFile) {
        return new JobParametersBuilder()
                .addString(
                        SupplierImportBatchConfiguration.INPUT_FILE_PARAMETER,
                        inputFile.toString())
                .addString(
                        SupplierImportBatchConfiguration.ERROR_REPORT_FILE_PARAMETER,
                        errorReportFile.toString())
                .addString("importId", UUID.randomUUID().toString())
                .toJobParameters();
    }

    private Path writeCsv(String fileName, String contents) throws Exception {
        Path path = tempDir.resolve(fileName);
        writeImportCsv(path, contents);
        return path;
    }

    private void writeImportCsv(Path path, String contents) throws Exception {
        Files.writeString(path, contents);
    }

    private Path sampleCsvPath() throws URISyntaxException {
        return Path.of(Objects.requireNonNull(getClass()
                        .getResource("/supplier-import/sample-supplier-products.csv"))
                .toURI());
    }

    @TestConfiguration
    static class JwtDecoderConfiguration {

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                if (!"catalog-admin-token".equals(token)) {
                    throw new JwtException("Test decoder rejects unknown bearer tokens.");
                }
                Instant issuedAt = Instant.now();
                return new Jwt(
                        token,
                        issuedAt,
                        issuedAt.plusSeconds(300),
                        Map.of("alg", "none"),
                        Map.of(
                                "sub", "test-user",
                                "iss", "http://localhost:8085/realms/ecommerce",
                                "realm_access", Map.of("roles", List.of("CATALOG_ADMIN"))));
            };
        }
    }
}
