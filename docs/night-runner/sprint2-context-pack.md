# Sprint 2 Context Pack

Sprint 2 goal: complete Catalog Service with PostgreSQL/Flyway, optimized reads,
Redis/Redisson caching and Spring Batch supplier import.

Completed:

- US-06 Catalog Data Model & Flyway
- US-07 Product & Category Management API
- US-08 Product Browse, Filter & Details

Active night scope:

- US-09 Redis Cache-Aside & Stampede Protection
- US-10 Supplier CSV Bulk Import

## Existing Catalog Baseline

- Customer detail endpoint: `GET /api/catalog/products/{productId}`.
- Customer reads expose only active products in active categories.
- Admin product/category/attribute writes live under `/api/catalog/admin/**`.
- Catalog data is owned by PostgreSQL and migrated with Flyway.
- Redis is already available in local Docker Compose.
- Catalog security already enforces `CATALOG_ADMIN` for admin routes.

## US-09 Required Outcomes

- Cache customer product-detail responses with cache-aside behavior.
- A miss reads PostgreSQL and writes Redis with a configurable TTL.
- A hit avoids a duplicate product repository read.
- Product and attribute writes evict the affected product entry.
- Category changes invalidate every affected product entry with a bounded strategy.
- A per-product Redisson `RLock` protects concurrent misses.
- The code re-checks the cache after obtaining the lock.
- Lock wait and lease durations are configurable and bounded.
- A deterministic concurrency test proves one database load for concurrent misses.

Cache only product details in this sprint. Do not cache arbitrary paginated browse
queries; their key cardinality and invalidation policy need a separate design.

## US-10 Required Outcomes

- Use Spring Batch with a chunk-oriented supplier CSV job.
- Define a stable CSV contract for category and product upserts.
- Validate required fields, status, currency and price.
- Upsert by category slug and product SKU without duplicate catalog rows.
- Skip bad rows and create an error report containing row number and reason.
- Persist production Batch metadata in Catalog PostgreSQL.
- Allow safe restart after failure without duplicating committed records.
- Provide `CATALOG_ADMIN` launch and status endpoints.
- Restrict imports to an allowed local directory and reject traversal/remote URLs.
- Invalidate product-detail cache entries changed by an import.

## Non-Goals

- Do not implement order creation or checkout.
- Do not implement Kafka, payment, audit or notification behavior.
- Do not add real inventory semantics.
- Do not expose arbitrary filesystem reads or remote supplier downloads.
- Do not add broad Testcontainers/E2E infrastructure from Sprint 6.

## Token Discipline

Use the existing Catalog source files, this context pack and the current task
block. Do not re-read the PDF or XLSX unless a task cannot be completed from the
compact context.
