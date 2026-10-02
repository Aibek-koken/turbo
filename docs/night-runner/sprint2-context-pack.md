# Sprint 2 Context Pack

Sprint 2 goal: complete Catalog Service with PostgreSQL/Flyway, optimized reads,
Redis and Spring Batch.

This night only targets the first slice of Sprint 2:

- US-06 Catalog Data Model & Flyway
- US-07 Product & Category Management API
- US-08 Product Browse, Filter & Details

Deferred from this night:

- US-09 Redis Cache-Aside & Stampede Protection
- US-10 Supplier CSV Bulk Import

## Required Outcomes

- Catalog schema is versioned with Flyway.
- Entities cover products, categories and product attributes.
- Admin endpoints can create, update and deactivate records.
- Customer endpoints support paginated product browse, filters and detail view.
- Product detail includes category and attributes.
- Read queries avoid obvious N+1 behavior using an EntityGraph or equivalent
  explicit fetch plan.
- Endpoint security matches roles from Sprint 1.

## Non-Goals

- Do not implement Redis cache behavior in this slice.
- Do not implement Spring Batch CSV import in this slice.
- Do not implement order creation or checkout.
- Do not add real inventory semantics. Use product status only.

## Token Discipline

Use Sprint 1 source files and this context pack. Do not re-read broad source
documents unless the compact context is insufficient.

