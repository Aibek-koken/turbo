# E-Commerce Night Agent Task Queue

Statuses: `pending`, `in_progress`, `done`, `blocked`.

This queue is consumed by `scripts/night-agent-runner.sh`.

## Night Scope

Primary target:

- Sprint 1 complete.
- Sprint 2 complete: US-06 through US-10.
- Complete Sprint 3: US-11 through US-15.

Do not continue into Payment, Audit, Notification or CI unless the queue is
explicitly extended.

## Shared Rules

- Do not ask the user questions overnight.
- If blocked, update `STATE.md` and `docs/night-runner/handoff.md`, then stop.
- Do not edit the source PDF/XLSX.
- Do not edit `LiveAssist-download/`.
- Do not commit or push from the agent session.
- Keep Java 21 as the target even if the local Java version is lower.
- Use compact context docs first. Do not repeatedly read the PDF/XLSX.

<!-- task:id=ECOM-001 phase=sprint1 status=done -->
## ECOM-001: Scaffold the Java 21 Spring Boot monorepo

Status: done

Story coverage:
- US-01 Service Skeleton & Docker Compose

Scope:
- Create a Maven monorepo or equivalent Spring Boot multi-service structure.
- Add service modules for gateway, catalog, order, payment and audit-notification.
- Add a small optional shared event-contracts module only if it removes real duplication.
- Add base package names, application classes and minimal health-ready service configs.
- Add `.gitignore`, root README and developer setup notes.
- Preserve service independence. Do not create a shared business database or shared domain model.

Allowed files:
- `.gitignore`
- `README.md`
- `pom.xml`
- `services/**`
- `libs/**`
- `docs/**`
- `AGENTS.md`
- `DESIGN.md`
- `STATE.md`

Validation:
- `scripts/project-validate.sh structure`
<!-- /task -->

<!-- task:id=ECOM-002 phase=sprint1 status=done -->
## ECOM-002: Add local infrastructure Docker Compose baseline

Status: done

Story coverage:
- US-01 Service Skeleton & Docker Compose
- US-05 Observability Baseline

Scope:
- Add Docker Compose for PostgreSQL, Redis, Kafka, Debezium Connect, MongoDB, Keycloak, Prometheus and Jaeger or Zipkin.
- Add health checks where practical.
- Add environment placeholders without real secrets.
- Add infra README notes for ports and service purpose.
- Application containers are optional in this task; infrastructure must be defined first.

Allowed files:
- `docker-compose.yml`
- `.env.example`
- `infra/**`
- `docs/**`
- `README.md`
- `STATE.md`

Validation:
- `scripts/project-validate.sh compose-config`
<!-- /task -->

<!-- task:id=ECOM-003 phase=sprint1 status=done -->
## ECOM-003: Configure Keycloak realm, clients and roles

Status: done

Story coverage:
- US-02 Keycloak Realm, Clients & Roles

Scope:
- Add importable Keycloak realm configuration.
- Define roles `CUSTOMER`, `CATALOG_ADMIN` and `OPS_ADMIN`.
- Define clients needed for local gateway/API testing.
- Document local token retrieval flow with placeholders only.
- Ensure no real credentials or tokens are committed.

Allowed files:
- `infra/keycloak/**`
- `docker-compose.yml`
- `.env.example`
- `docs/**`
- `README.md`
- `STATE.md`

Validation:
- `scripts/project-validate.sh keycloak-config`
<!-- /task -->

<!-- task:id=ECOM-004 phase=sprint1 status=done -->
## ECOM-004: Implement secure API Gateway routing

Status: done

Story coverage:
- US-03 Secure API Gateway Routing

Scope:
- Configure Spring Cloud Gateway routes to catalog, order, payment and audit-notification services.
- Add JWT resource server validation at the gateway.
- Return 401 for missing or invalid tokens on protected routes.
- Keep route configuration environment-driven where useful.
- Add tests or configuration checks for route and auth behavior.

Allowed files:
- `services/gateway-service/**`
- `pom.xml`
- `docs/**`
- `README.md`
- `STATE.md`

Validation:
- `scripts/project-validate.sh maven-validate`
<!-- /task -->

<!-- task:id=ECOM-005 phase=sprint1 status=done -->
## ECOM-005: Add service-level RBAC baseline

Status: done

Story coverage:
- US-04 Service-Level RBAC

Scope:
- Make each business service a JWT resource server.
- Enforce role checks locally in catalog, order, payment and audit-notification services.
- Add minimal protected stub endpoints only where needed to prove RBAC.
- Ensure customer-scoped access rules are documented for later order/catalog work.

Allowed files:
- `services/catalog-service/**`
- `services/order-service/**`
- `services/payment-service/**`
- `services/audit-notification-service/**`
- `libs/**`
- `pom.xml`
- `docs/**`
- `README.md`
- `STATE.md`

Validation:
- `scripts/project-validate.sh maven-validate`
<!-- /task -->

<!-- task:id=ECOM-006 phase=sprint1 status=done -->
## ECOM-006: Add observability baseline across services

Status: done

Story coverage:
- US-05 Observability Baseline

Scope:
- Add Actuator and Prometheus endpoints for each service.
- Add Micrometer/OpenTelemetry tracing configuration.
- Add trace/correlation ID logging conventions.
- Add Prometheus scrape config.
- Add a short observability runbook.

Allowed files:
- `services/**`
- `infra/observability/**`
- `docker-compose.yml`
- `pom.xml`
- `docs/**`
- `README.md`
- `STATE.md`

Validation:
- `scripts/project-validate.sh maven-validate`
<!-- /task -->

<!-- task:id=ECOM-007 phase=sprint2 status=done -->
## ECOM-007: Implement Catalog data model and Flyway migrations

Status: done

Story coverage:
- US-06 Catalog Data Model & Flyway

Scope:
- Add versioned Flyway migrations for categories, products and product attributes.
- Add JPA entities and repositories.
- Add constraints for SKU uniqueness, status and required fields.
- Add tests for migration repeatability and repository basics where practical.

Allowed files:
- `services/catalog-service/**`
- `pom.xml`
- `docs/**`
- `README.md`
- `STATE.md`

Validation:
- `scripts/project-validate.sh catalog-validate`
<!-- /task -->

<!-- task:id=ECOM-008 phase=sprint2 status=done -->
## ECOM-008: Implement Catalog admin management API

Status: done

Story coverage:
- US-07 Product & Category Management API

Scope:
- Add admin endpoints to create, update and deactivate products, categories and attributes.
- Validate request DTOs.
- Return useful 4xx errors for validation/domain failures.
- Require `CATALOG_ADMIN` for admin writes.
- Add tests for authorized, unauthorized and invalid-request paths.

Allowed files:
- `services/catalog-service/**`
- `pom.xml`
- `docs/**`
- `README.md`
- `STATE.md`

Validation:
- `scripts/project-validate.sh catalog-validate`
<!-- /task -->

<!-- task:id=ECOM-009 phase=sprint2 status=done -->
## ECOM-009: Implement Catalog customer browse and detail API

Status: done

Story coverage:
- US-08 Product Browse, Filter & Details

Scope:
- Add paginated product browsing.
- Add filters for category, status and text/SKU where practical.
- Add product detail endpoint including category and attributes.
- Avoid obvious N+1 reads with EntityGraph or explicit fetch plans.
- Add tests for pagination, filters, detail response and unauthorized admin-only paths.

Allowed files:
- `services/catalog-service/**`
- `pom.xml`
- `docs/**`
- `README.md`
- `STATE.md`

Validation:
- `scripts/project-validate.sh catalog-validate`
<!-- /task -->

<!-- task:id=ECOM-010 phase=sprint2 status=done -->
## ECOM-010: Add Redis and Redisson catalog cache foundation

Status: done

Story coverage:
- US-09 Redis Cache-Aside & Stampede Protection

Scope:
- Add Spring Data Redis and Redisson dependencies to Catalog Service.
- Add environment-driven Redis connection, cache TTL, lock wait and lease settings.
- Introduce a focused cache abstraction for customer product-detail responses.
- Use stable, namespaced product-detail keys and JSON serialization that is safe across restarts.
- Keep database reads functional when caching is disabled for tests or local troubleshooting.
- Add focused tests for configuration, key generation and cache value round-tripping.

Allowed files:
- `services/catalog-service/**`
- `pom.xml`
- `.env.example`
- `docs/**`
- `README.md`
- `STATE.md`

Validation:
- `scripts/project-validate.sh catalog-test`
<!-- /task -->

<!-- task:id=ECOM-011 phase=sprint2 status=done -->
## ECOM-011: Implement product-detail cache-aside and invalidation

Status: done

Story coverage:
- US-09 Redis Cache-Aside & Stampede Protection

Scope:
- Apply cache-aside behavior to customer product-detail reads.
- On a miss, load the authoritative active product from PostgreSQL and populate Redis with TTL.
- On a hit, return the cached response without querying the product repository.
- Evict the affected product cache after product or attribute create/update/deactivate operations.
- Invalidate all affected product entries after category update/deactivation using a bounded strategy.
- Never serve inactive products or stale admin changes from cache.
- Add tests for hit, miss, TTL write and each relevant invalidation path.

Allowed files:
- `services/catalog-service/**`
- `pom.xml`
- `.env.example`
- `docs/**`
- `README.md`
- `STATE.md`

Validation:
- `scripts/project-validate.sh catalog-test`
<!-- /task -->

<!-- task:id=ECOM-012 phase=sprint2 status=done -->
## ECOM-012: Add Redisson stampede protection

Status: done

Story coverage:
- US-09 Redis Cache-Aside & Stampede Protection

Scope:
- Protect concurrent product-detail cache misses with a per-product Redisson `RLock`.
- Re-check Redis after acquiring the lock before loading from PostgreSQL.
- Bound lock wait and lease times through configuration.
- Release only locks owned by the current thread and handle timeout/interruption cleanly.
- Preserve a clear failure path when Redis or locking is unavailable; do not silently cache bad data.
- Add a deterministic concurrency test proving concurrent misses perform one database load.

Allowed files:
- `services/catalog-service/**`
- `pom.xml`
- `.env.example`
- `docs/**`
- `README.md`
- `STATE.md`

Validation:
- `scripts/project-validate.sh catalog-test`
<!-- /task -->

<!-- task:id=ECOM-013 phase=sprint2 status=done -->
## ECOM-013: Add Spring Batch supplier import foundation

Status: done

Story coverage:
- US-10 Supplier CSV Bulk Import

Scope:
- Add Spring Batch dependencies and Catalog Service batch configuration.
- Define and document the supplier CSV contract with SKU, product, category, price, currency, status and attribute fields.
- Add a restartable supplier import job and chunk-oriented step with environment-driven chunk size.
- Keep job metadata in the Catalog PostgreSQL database and avoid in-memory production metadata.
- Add a small non-production sample CSV for tests/documentation only.
- Add tests that the job and step are registered and a valid CSV is processed in chunks.

Allowed files:
- `services/catalog-service/**`
- `pom.xml`
- `docs/**`
- `README.md`
- `STATE.md`

Validation:
- `scripts/project-validate.sh catalog-test`
<!-- /task -->

<!-- task:id=ECOM-014 phase=sprint2 status=done -->
## ECOM-014: Implement supplier row validation, upsert and error reporting

Status: done

Story coverage:
- US-10 Supplier CSV Bulk Import

Scope:
- Validate required fields, UUID-independent business keys, status, currency and non-negative price.
- Upsert categories and products by stable slug/SKU rules without duplicating existing records.
- Process valid records transactionally in bounded chunks.
- Skip invalid rows without aborting the complete import and produce a deterministic error report with row number and reason.
- Evict product-detail cache entries for products changed by the import.
- Add tests for mixed valid/invalid files, updates, duplicate SKUs and error-report contents.

Allowed files:
- `services/catalog-service/**`
- `pom.xml`
- `docs/**`
- `README.md`
- `STATE.md`

Validation:
- `scripts/project-validate.sh catalog-test`
<!-- /task -->

<!-- task:id=ECOM-015 phase=sprint2 status=done -->
## ECOM-015: Add protected import control and restartability

Status: done

Story coverage:
- US-10 Supplier CSV Bulk Import

Scope:
- Add `CATALOG_ADMIN` endpoints to launch an import from an allowed local import path and inspect job status/results.
- Reject path traversal and unsupported file types; never accept arbitrary command execution or remote URLs.
- Prevent accidental concurrent execution of the same supplier file.
- Make restart after a failed chunk resume safely without duplicating already committed records.
- Return processed, skipped and failed counts plus the error-report location without exposing secrets or host internals.
- Add tests for authorization, path validation, duplicate launch, failure/restart and idempotent final data.
- Document the local import and restart workflow.

Allowed files:
- `services/catalog-service/**`
- `pom.xml`
- `docs/**`
- `README.md`
- `STATE.md`

Validation:
- `scripts/project-validate.sh catalog-test`
<!-- /task -->

<!-- task:id=ECOM-016 phase=sprint3 status=pending -->
## ECOM-016: Add Order Service persistence foundation

Status: pending

Story coverage:
- US-11 Create Order with Product Snapshot
- US-12 Order State Machine
- US-13 Transactional Outbox Persistence

Scope:
- Add Spring Data JPA, Flyway, PostgreSQL and H2 test dependencies to Order Service.
- Configure the service-owned `orders` PostgreSQL database without sharing Catalog entities or repositories.
- Add Flyway schema for orders, immutable order-item snapshots, status history and append-only outbox events.
- Model UUID identifiers, currency/monetary precision, UTC timestamps, optimistic versioning, constraints and read-oriented indexes.
- Add JPA entities and repositories that preserve aggregate ownership and avoid cascade behavior outside the order boundary.
- Add migration and repository tests, including unique/foreign-key and monetary constraints.

Allowed files:
- `services/order-service/**`
- `pom.xml`
- `.env.example`
- `docs/**`
- `README.md`
- `STATE.md`

Validation:
- `scripts/project-validate.sh order-test`
<!-- /task -->

<!-- task:id=ECOM-017 phase=sprint3 status=pending -->
## ECOM-017: Add the Catalog product-snapshot client

Status: pending

Story coverage:
- US-11 Create Order with Product Snapshot

Scope:
- Add an Order Service Catalog client abstraction using Spring `RestClient` and an environment-driven Catalog base URL.
- Forward the current bearer token and correlation ID when resolving products; do not add a trusted anonymous bypass.
- Map the existing active product-detail response into a small Order-owned snapshot contract.
- Convert missing, inactive, malformed and unavailable Catalog responses into explicit order-domain/API failures without persisting partial data.
- Keep network calls outside database transactions and make the client replaceable in unit tests.
- Add focused client tests for a valid product and each relevant failure class.

Allowed files:
- `services/order-service/**`
- `pom.xml`
- `.env.example`
- `docs/**`
- `README.md`
- `STATE.md`

Validation:
- `scripts/project-validate.sh order-test`
<!-- /task -->

<!-- task:id=ECOM-018 phase=sprint3 status=pending -->
## ECOM-018: Implement authenticated order creation

Status: pending

Story coverage:
- US-11 Create Order with Product Snapshot

Scope:
- Add a `CUSTOMER` order-create API under the existing customer route boundary.
- Derive customer identity only from the authenticated JWT `sub` claim.
- Accept product IDs and bounded positive quantities; reject empty orders, duplicate products and mixed currencies.
- Resolve current products through the Catalog client, snapshot ID/SKU/name/unit price/currency, and calculate line/order totals with `BigDecimal`.
- Persist the order, items and initial `CREATED` history record atomically; do not accept names or prices from clients.
- Return a stable response and Problem Details errors without exposing persistence or remote-service internals.
- Add service and MockMvc tests for totals, snapshots, JWT ownership and all specified rejection paths.

Allowed files:
- `services/order-service/**`
- `pom.xml`
- `docs/**`
- `README.md`
- `STATE.md`

Validation:
- `scripts/project-validate.sh order-test`
<!-- /task -->

<!-- task:id=ECOM-019 phase=sprint3 status=pending -->
## ECOM-019: Implement the order state machine and history

Status: pending

Story coverage:
- US-12 Order State Machine

Scope:
- Centralize the allowed V1 transitions for `CREATED`, `PAYMENT_PENDING`, `PAID`, `PAYMENT_FAILED` and `CANCELLED`.
- Add an operations transition API protected by `OPS_ADMIN`; keep the application service usable by future internal payment handling.
- Persist the current state and append its timestamped history entry in one transaction.
- Reject no-op and invalid transitions without changing the order or history.
- Use optimistic locking so concurrent transitions cannot silently overwrite each other.
- Add exhaustive transition-matrix, persistence/history, authorization and concurrent-update tests.

Allowed files:
- `services/order-service/**`
- `pom.xml`
- `docs/**`
- `README.md`
- `STATE.md`

Validation:
- `scripts/project-validate.sh order-test`
<!-- /task -->

<!-- task:id=ECOM-020 phase=sprint3 status=pending -->
## ECOM-020: Persist OrderCreated through the transactional outbox

Status: pending

Story coverage:
- US-13 Transactional Outbox Persistence

Scope:
- Build the versioned `OrderCreated` event envelope defined in `DESIGN.md` and the Sprint 3 context pack.
- Persist the created order and one append-only outbox event in the same Spring transaction.
- Include event ID, aggregate ID, event type/version, UTC occurrence time, trace ID, correlation ID and the immutable order snapshot payload.
- Keep JSON serialization deterministic and independent from JPA lazy proxies.
- Do not publish directly to Kafka and do not add a second transaction or after-commit outbox insert.
- Add integration tests proving successful atomic commit and a forced rollback that leaves neither order nor outbox row.

Allowed files:
- `services/order-service/**`
- `pom.xml`
- `docs/**`
- `README.md`
- `STATE.md`

Validation:
- `scripts/project-validate.sh order-test`
<!-- /task -->

<!-- task:id=ECOM-021 phase=sprint3 status=pending -->
## ECOM-021: Route Order outbox events through Debezium and Kafka

Status: pending

Story coverage:
- US-14 Debezium CDC to Kafka

Scope:
- Add a versioned Debezium PostgreSQL connector definition that captures only the Order Service outbox table.
- Configure the outbox event router for `ecommerce.order.events`, keyed by aggregate ID, while preserving the complete versioned event envelope.
- Add environment placeholders required for local logical replication without checking in real credentials.
- Add an idempotent connector registration helper and concise local runbook with connector/topic inspection commands.
- Add a bounded smoke helper that can insert or create a test outbox event and verify event ID/aggregate ID on the expected Kafka topic without deleting existing data.
- Validate JSON/config structure and Docker Compose wiring; do not implement an application-side Kafka publisher.

Allowed files:
- `infra/debezium/**`
- `infra/README.md`
- `docker-compose.yml`
- `.env.example`
- `scripts/register-order-outbox-connector.sh`
- `scripts/verify-order-cdc.sh`
- `services/order-service/**`
- `docs/**`
- `README.md`
- `STATE.md`

Validation:
- `scripts/project-validate.sh order-test`
- `scripts/project-validate.sh order-cdc-config`
<!-- /task -->

<!-- task:id=ECOM-022 phase=sprint3 status=pending -->
## ECOM-022: Add customer order queries and operations history

Status: pending

Story coverage:
- US-15 Order Query & History API

Scope:
- Add bounded, deterministic pagination for a customer to list only orders owned by their JWT subject.
- Add customer order-detail lookup with the same ownership enforcement.
- Add an `OPS_ADMIN` lookup by order ID for support without exposing arbitrary customer-list access.
- Return item snapshots, monetary totals/currency, current status and chronologically ordered status history.
- Use repository fetch plans or focused projections to avoid obvious N+1 query behavior.
- Return stable Problem Details responses for missing or non-owned orders without leaking another customer's data.
- Add repository/service/MockMvc tests for ownership isolation, operations access, response shape, pagination and history order.

Allowed files:
- `services/order-service/**`
- `pom.xml`
- `docs/**`
- `README.md`
- `STATE.md`

Validation:
- `scripts/project-validate.sh order-test`
<!-- /task -->
