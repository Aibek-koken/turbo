# Catalog Supplier Import

The Catalog Service owns supplier product imports through Spring Batch. Batch
metadata is stored in the Catalog PostgreSQL database by Flyway migrations, and
`spring.batch.jdbc.initialize-schema` is disabled so production does not fall
back to in-memory or auto-created metadata.

## Job

- Job bean: `supplierImportJob`
- Step bean: `supplierImportStep`
- Required job parameter: `inputFile`
- Optional job parameter: `errorReportFile`
- Recommended identifying job parameter: `importId`
- Chunk size: `catalog.supplier-import.chunk-size`
- Environment override: `CATALOG_SUPPLIER_IMPORT_CHUNK_SIZE`
- Local import directory: `catalog.supplier-import.import-directory`
- Environment override: `CATALOG_SUPPLIER_IMPORT_DIRECTORY`

Failed executions are restartable when relaunched with the same identifying job
parameters and the same input file.

Rows that fail CSV parsing or supplier-field validation are skipped without
aborting the complete import. Each run writes a deterministic CSV error report:

```csv
row_number,reason
3,"price_amount must be non-negative"
```

When `errorReportFile` is provided, the report is written there. Otherwise the
default path is the input file name plus `.errors.csv` in the same directory.
The report path and skipped-row count are also stored in the Batch execution
context as `supplierImport.errorReportFile` and
`supplierImport.errorReportCount`.

## CSV Contract

The file must include a header row with these columns in order:

```csv
sku,product_name,product_description,category_slug,category_name,category_description,price_amount,currency,status,attributes
```

| Column | Required | Notes |
| --- | --- | --- |
| `sku` | yes | Product SKU. Upsert key. |
| `product_name` | yes | Product display name. |
| `product_description` | no | Product description. Blank values import as `null`. |
| `category_slug` | yes | Category slug. Upsert key. |
| `category_name` | yes | Category display name. |
| `category_description` | no | Category description. Blank values import as `null`. |
| `price_amount` | yes | Decimal product price. |
| `currency` | yes | Three-letter ISO-style currency code, stored uppercase. |
| `status` | yes | `DRAFT`, `ACTIVE` or `INACTIVE`. |
| `attributes` | no | Semicolon-delimited `key=value` pairs, for example `roast=medium;origin=colombia`. |

Required fields must be non-blank, price must parse as a non-negative decimal,
and imported text must fit the Catalog database limits. Categories upsert by
`category_slug`; products upsert by `sku`, including duplicate SKU rows in the
same file where the later row updates the same product. Product-detail cache
entries for changed products are evicted after the import chunk commits.

A small non-production example lives at
`services/catalog-service/src/test/resources/supplier-import/sample-supplier-products.csv`.

## Local Admin Workflow

Catalog admins launch imports through the protected Catalog Service admin API.
The API accepts only a relative `.csv` path under the configured local import
directory. The default directory is `var/catalog/imports` relative to the
service working directory.

Example local setup:

```bash
mkdir -p var/catalog/imports
cp services/catalog-service/src/test/resources/supplier-import/sample-supplier-products.csv \
  var/catalog/imports/sample-supplier-products.csv
```

Launch the import with a `CATALOG_ADMIN` bearer token:

```bash
curl -X POST http://localhost:8081/api/catalog/admin/supplier-imports \
  -H "Authorization: Bearer <catalog-admin-token>" \
  -H "Content-Type: application/json" \
  -d '{"importPath":"sample-supplier-products.csv"}'
```

The response returns the Batch execution ID, status, exit code, processed row
count, skipped row count, failed execution count and a sanitized error-report
location such as `sample-supplier-products.csv.errors.csv`. Absolute host paths
are not returned by the API.

Inspect status and counts with:

```bash
curl http://localhost:8081/api/catalog/admin/supplier-imports/{executionId} \
  -H "Authorization: Bearer <catalog-admin-token>"
```

Rejected launch requests include absolute paths, path traversal such as
`../supplier.csv`, URL-style values such as `https://...`, and non-CSV files.
Place files in the configured import directory first; the API never downloads
remote supplier files or executes shell commands.

## Restart Workflow

When a chunk fails for an unexpected write/database reason, already committed
chunks remain committed in Catalog PostgreSQL and the Batch metadata records the
reader position. Fix the same CSV file in place and launch the same relative
`importPath` again. The API uses the canonical input file as the identifying
Batch job parameter, so Spring Batch restarts the failed execution instead of
starting over from the first committed chunk.

The import writer upserts categories by `category_slug` and products by `sku`,
so a safe restart does not duplicate records that were already committed.
Concurrent launches of the same supplier file are rejected while an execution
for that file is running.
