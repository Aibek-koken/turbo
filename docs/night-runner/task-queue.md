# E-Commerce Night Agent Task Queue

Statuses: `pending`, `in_progress`, `done`, `blocked`.

This queue is consumed by `scripts/night-agent-runner.sh`.

## Night Scope

Primary target:

- Sprint 1 complete.
- Sprint 2 complete: US-06 through US-10.
- Sprint 3 complete: US-11 through US-15.
- Sprint 4 complete: US-16 through US-20.
- Sprint 5 complete: US-21 through US-25.
- Complete Sprint 6: US-26 through US-30.

Do not add work beyond the Sprint 6 release target unless the queue is
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

<!-- task:id=ECOM-040 phase=sprint6 status=pending -->
## ECOM-040: Add Testcontainers foundation and PostgreSQL integration suites

Status: pending

Story coverage:
- US-26 Integration Tests with Testcontainers

Scope:
- Add an opt-in Maven integration-test profile using a pinned Testcontainers BOM/version and Failsafe-style `*IT` execution without slowing the default unit-test loop.
- Add real PostgreSQL container coverage for the Catalog, Order and Payment owned schemas, Flyway migrations, repository constraints and representative persistence flows.
- Start isolated databases dynamically and wire service properties through supported Spring test mechanisms; do not depend on locally running PostgreSQL or fixed host ports.
- Keep each service responsible for its own schema tests and avoid a shared business database or shared JPA domain module.
- Ensure containers and temporary data are cleaned up by the test lifecycle without deleting developer Docker volumes.
- Document the opt-in command and clear Docker prerequisite/failure behavior.
- Do not add Redis, Kafka, MongoDB or full end-to-end coverage yet.

Allowed files:
- `pom.xml`
- `services/catalog-service/**`
- `services/order-service/**`
- `services/payment-service/**`
- `docs/**`
- `README.md`
- `STATE.md`

Validation:
- `scripts/project-validate.sh integration-test`
<!-- /task -->

<!-- task:id=ECOM-041 phase=sprint6 status=pending -->
## ECOM-041: Verify Redis cache and lock behavior with Testcontainers

Status: pending

Story coverage:
- US-26 Integration Tests with Testcontainers

Scope:
- Add real Redis Testcontainers coverage for Catalog product-detail cache serialization, cache-aside reads, invalidation and Redisson lock behavior.
- Prove a cache miss populates Redis and a subsequent read uses the cached value while a product update invalidates the relevant entry.
- Add a bounded concurrent-miss test that demonstrates one protected database load without timing-sensitive unbounded stress.
- Use dynamic container endpoints and the existing cache configuration; do not depend on localhost Redis or fixed ports.
- Keep unit tests fast and retain graceful no-Redis behavior outside the opt-in integration profile.
- Do not add load testing or change production cache semantics unless a demonstrated integration defect requires a scoped fix.

Allowed files:
- `pom.xml`
- `services/catalog-service/**`
- `docs/**`
- `README.md`
- `STATE.md`

Validation:
- `scripts/project-validate.sh integration-test`
<!-- /task -->

<!-- task:id=ECOM-042 phase=sprint6 status=pending -->
## ECOM-042: Verify Kafka event processing with Testcontainers

Status: pending

Story coverage:
- US-26 Integration Tests with Testcontainers

Scope:
- Add real Kafka Testcontainers coverage for the Payment `OrderCreated` consumer, Order payment-result consumer and Audit order/payment consumers.
- Exercise representative valid, duplicate and malformed deliveries through Kafka listener containers rather than calling listener methods directly.
- Verify persisted outcomes, idempotent replay behavior and bounded dead-letter routing for poison messages using isolated topics and consumer groups.
- Use dynamic broker endpoints and deterministic polling/timeouts; do not use fixed sleeps or depend on locally running Kafka.
- Keep event contracts versioned and service-owned, and do not bypass existing parser, retry or persistence boundaries.
- Do not attempt the complete Gateway-to-notification E2E flow yet.

Allowed files:
- `pom.xml`
- `services/order-service/**`
- `services/payment-service/**`
- `services/audit-notification-service/**`
- `docs/**`
- `README.md`
- `STATE.md`

Validation:
- `scripts/project-validate.sh integration-test`
<!-- /task -->

<!-- task:id=ECOM-043 phase=sprint6 status=pending -->
## ECOM-043: Verify Mongo audit and notification persistence with Testcontainers

Status: pending

Story coverage:
- US-26 Integration Tests with Testcontainers

Scope:
- Add real MongoDB Testcontainers coverage for audit event persistence, indexes, duplicate event handling and notification delivery ledger uniqueness.
- Verify searchable event metadata and complete structured payloads survive a real BSON round trip.
- Exercise replay recovery for missing or failed channel deliveries while proving completed email/push delivery records are not duplicated.
- Use a dynamic MongoDB connection and isolated database; do not require the local Compose MongoDB instance.
- Keep mock notification adapters local and deterministic with no real contact data, credentials or external provider calls.
- Do not add the complete cross-service E2E flow yet.

Allowed files:
- `pom.xml`
- `services/audit-notification-service/**`
- `docs/**`
- `README.md`
- `STATE.md`

Validation:
- `scripts/project-validate.sh integration-test`
<!-- /task -->

<!-- task:id=ECOM-044 phase=sprint6 status=pending -->
## ECOM-044: Containerize application services and the mock payment provider

Status: pending

Story coverage:
- US-29 One-Command Full Environment

Scope:
- Add reproducible multi-stage container builds for Gateway, Catalog, Order, Payment and Audit Notification services using Java 21 runtime images and non-root execution where practical.
- Add a deterministic local mock payment-provider container or bounded mock service that supports approved, declined, timeout and 5xx scenarios required by existing Payment behavior.
- Extend Docker Compose with all five application services and the mock provider using internal service DNS, environment placeholders, health checks and explicit dependencies.
- Keep external host ports configurable and preserve service-owned database, Kafka, MongoDB, Keycloak and observability boundaries.
- Add an appropriate root `.dockerignore` and avoid copying local build output, VCS data, secrets or agent logs into images.
- Do not add production deployment manifests, registries or cloud credentials.

Allowed files:
- `.dockerignore`
- `.env.example`
- `pom.xml`
- `docker-compose.yml`
- `services/**`
- `infra/mock-payment-provider/**`
- `infra/README.md`
- `docs/**`
- `README.md`
- `STATE.md`

Validation:
- `scripts/project-validate.sh full-stack-config`
<!-- /task -->

<!-- task:id=ECOM-045 phase=sprint6 status=pending -->
## ECOM-045: Complete one-command startup and full-stack smoke validation

Status: pending

Story coverage:
- US-29 One-Command Full Environment

Scope:
- Make `docker compose up --build` start the complete local platform from a clean clone without manual application-process startup.
- Add bounded idempotent bootstrap for both Debezium outbox connectors after PostgreSQL, Kafka and Connect are healthy.
- Ensure service health checks and dependency conditions reflect actual readiness without masking permanently failed services.
- Add a bounded full-stack smoke script that waits with a deadline, verifies Keycloak, Gateway, all services, connector status, Prometheus and Jaeger, and reports actionable failures.
- Keep cleanup opt-in and non-destructive; the smoke check must not delete developer volumes or application data.
- Update local setup documentation and environment placeholders for the exact one-command workflow.
- Do not add the authenticated purchase E2E scenario yet.

Allowed files:
- `.env.example`
- `docker-compose.yml`
- `infra/**`
- `scripts/bootstrap-debezium-connectors.sh`
- `scripts/verify-full-stack.sh`
- `docs/**`
- `README.md`
- `STATE.md`

Validation:
- `scripts/project-validate.sh full-stack-config`
- `scripts/project-validate.sh full-stack-smoke`
<!-- /task -->

<!-- task:id=ECOM-046 phase=sprint6 status=pending -->
## ECOM-046: Add producer-consumer event contract tests

Status: pending

Story coverage:
- US-27 Contract & Full End-to-End Tests

Scope:
- Add executable compatibility tests for the version-1 `OrderCreated`, `PaymentSucceeded` and `PaymentFailed` JSON envelopes across their actual producers and consumers.
- Verify required envelope fields, aggregate/order consistency, money/currency representation, trace/correlation metadata and supported event versions.
- Use representative producer output as consumer input so schema drift fails before full-stack E2E execution.
- Keep contracts additive and service boundaries explicit; do not create a shared business-domain module or silently loosen consumer validation.
- Add an opt-in contract-test profile or focused test command that is deterministic and does not require the full Compose stack.
- Document how to run the contract suite locally.

Allowed files:
- `pom.xml`
- `libs/**`
- `services/order-service/**`
- `services/payment-service/**`
- `services/audit-notification-service/**`
- `tests/**`
- `docs/**`
- `README.md`
- `STATE.md`

Validation:
- `scripts/project-validate.sh contract-test`
<!-- /task -->

<!-- task:id=ECOM-047 phase=sprint6 status=pending -->
## ECOM-047: Add authenticated purchase-flow end-to-end tests

Status: pending

Story coverage:
- US-27 Contract & Full End-to-End Tests

Scope:
- Add a bounded black-box E2E harness against the full Compose stack using tokens obtained from the local Keycloak realm and all external REST traffic through Gateway.
- Create deterministic catalog fixtures through authorized APIs, place a customer order and wait through CDC/Kafka processing until the order reaches `PAID`.
- Verify the corresponding Payment state, MongoDB audit records and exactly-once mock email/push delivery evidence without reaching into service implementation classes.
- Add a deterministic provider-decline scenario ending in `PAYMENT_FAILED` with consistent order, payment, audit and notification state.
- Prove replay/duplicate safety for the tested flow without charging or notifying twice.
- Use deadlines and diagnostic output instead of fixed long sleeps; keep teardown non-destructive unless the harness created the isolated project explicitly.
- Never embed real tokens, passwords or customer contact data.

Allowed files:
- `.env.example`
- `pom.xml`
- `tests/**`
- `scripts/run-e2e-tests.sh`
- `infra/keycloak/**`
- `infra/mock-payment-provider/**`
- `docs/**`
- `README.md`
- `STATE.md`

Validation:
- `scripts/project-validate.sh e2e-test`
<!-- /task -->

<!-- task:id=ECOM-048 phase=sprint6 status=pending -->
## ECOM-048: Add bounded load and cache-effect validation

Status: pending

Story coverage:
- US-28 Load & Resilience Validation

Scope:
- Add a versioned, bounded local load scenario for health-safe Catalog browse/detail traffic through Gateway with configurable virtual users, duration and base URL.
- Record latency percentiles, throughput and error rate with explicit smoke thresholds suitable for a developer machine and CI smoke mode.
- Compare cold and warm product-detail reads using existing cache hit/miss and database-observation evidence to demonstrate that cache use reduces repeated database work.
- Include a small virtual-thread concurrency scenario or observation that validates the configured runtime behavior without claiming production capacity.
- Store scripts and human-readable run instructions, not generated result archives or machine-specific reports.
- Cap defaults and require explicit opt-in for higher load; do not run unbounded stress or destructive data generation.

Allowed files:
- `tests/load/**`
- `scripts/run-load-validation.sh`
- `docs/**`
- `README.md`
- `STATE.md`

Validation:
- `scripts/project-validate.sh load-smoke`
<!-- /task -->

<!-- task:id=ECOM-049 phase=sprint6 status=pending -->
## ECOM-049: Add restart and replay resilience validation

Status: pending

Story coverage:
- US-28 Load & Resilience Validation

Scope:
- Add bounded local resilience scenarios for a temporary Payment or Audit service restart and a temporary Kafka interruption during an in-flight purchase.
- Verify recovery completes without corrupt order/payment state, duplicate provider attempts, duplicate terminal events or duplicate completed notifications.
- Exercise existing retry, DLT and idempotency boundaries and report the observed recovery timeline and final consistency checks.
- Use explicit deadlines, unique test identifiers and targeted Compose operations; do not delete volumes, reset databases or disrupt unrelated Docker projects.
- Make the smoke mode safe for a developer machine and keep longer fault windows explicitly opt-in.
- Document prerequisites, recovery expectations and troubleshooting output.

Allowed files:
- `docker-compose.yml`
- `tests/resilience/**`
- `scripts/run-resilience-validation.sh`
- `docs/**`
- `README.md`
- `STATE.md`

Validation:
- `scripts/project-validate.sh resilience-smoke`
<!-- /task -->

<!-- task:id=ECOM-050 phase=sprint6 status=pending -->
## ECOM-050: Publish OpenAPI documentation for external APIs

Status: pending

Story coverage:
- US-30 API, Architecture Documentation & CI Release

Scope:
- Add compatible Springdoc OpenAPI support for externally useful Catalog, Order, Payment and Audit/Notification HTTP APIs while preserving existing security rules.
- Describe bearer JWT security, role expectations, request/response schemas, pagination and Problem Details responses where applicable.
- Ensure OpenAPI JSON and Swagger UI endpoints are reachable in the local environment through documented service URLs or intentional Gateway routes.
- Keep actuator and internal-only implementation details out of the public API description.
- Add focused tests for document availability, key operations and security configuration without weakening production endpoint authorization.
- Update README/API documentation links.

Allowed files:
- `pom.xml`
- `services/gateway-service/**`
- `services/catalog-service/**`
- `services/order-service/**`
- `services/payment-service/**`
- `services/audit-notification-service/**`
- `docs/**`
- `README.md`
- `STATE.md`

Validation:
- `scripts/project-validate.sh api-docs-test`
<!-- /task -->

<!-- task:id=ECOM-051 phase=sprint6 status=pending -->
## ECOM-051: Complete architecture, ERD, setup and demo documentation

Status: pending

Story coverage:
- US-30 API, Architecture Documentation & CI Release

Scope:
- Update the root README with prerequisites, one-command startup, validation commands, API documentation links and a concise authenticated demo flow.
- Add `docs/architecture.md` showing service/data ownership, synchronous calls, both outbox/CDC paths, Kafka consumers, observability and security boundaries.
- Add `docs/data-model.md` with ERDs or schema documentation for Catalog, Order, Payment and Mongo audit/notification persistence using text-based source that is reviewable in Git.
- Add `docs/demo-runbook.md` for the authenticated happy path, payment-failure demonstration and expected audit/notification evidence.
- Consolidate operational runbook links for health, metrics, traces, connector status, DLT inspection, E2E, load and resilience checks.
- Verify all documentation commands and relative links against the repository; remove stale statements such as services being only future skeletons.
- Do not modify the source PDF/XLSX or add generated binary documentation artifacts.

Allowed files:
- `README.md`
- `DESIGN.md`
- `docs/**`
- `infra/README.md`
- `STATE.md`

Validation:
- `scripts/project-validate.sh docs-check`
<!-- /task -->

<!-- task:id=ECOM-052 phase=sprint6 status=pending -->
## ECOM-052: Add CI pipeline and final release-readiness checks

Status: pending

Story coverage:
- US-30 API, Architecture Documentation & CI Release

Scope:
- Add a CI workflow for Java 21 that restores Maven dependencies safely, runs unit tests, opt-in integration/contract suites and repository configuration checks from a fresh checkout.
- Add bounded E2E or full-stack smoke coverage in CI when Docker is available, with clear timeouts and uploaded diagnostics on failure.
- Validate Docker Compose configuration and application image builds without publishing images or requiring registry credentials.
- Add concurrency cancellation and least-privilege workflow permissions; pin major action versions and avoid untrusted secret exposure.
- Add a final release-readiness command that checks required docs, OpenAPI tests, Compose configuration and Maven suites without modifying source or pushing artifacts.
- Update `STATE.md` and handoff with Sprint 6 completion, exact validations and any intentionally manual long-running load/resilience steps.
- Do not create releases, tags, commits or pushes from the night agent.

Allowed files:
- `.github/**`
- `pom.xml`
- `scripts/**`
- `docs/**`
- `README.md`
- `DESIGN.md`
- `STATE.md`

Validation:
- `scripts/project-validate.sh ci-config`
- `scripts/project-validate.sh release-check`
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

<!-- task:id=ECOM-016 phase=sprint3 status=done -->
## ECOM-016: Add Order Service persistence foundation

Status: done

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

<!-- task:id=ECOM-017 phase=sprint3 status=done -->
## ECOM-017: Add the Catalog product-snapshot client

Status: done

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

<!-- task:id=ECOM-018 phase=sprint3 status=done -->
## ECOM-018: Implement authenticated order creation

Status: done

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

<!-- task:id=ECOM-019 phase=sprint3 status=done -->
## ECOM-019: Implement the order state machine and history

Status: done

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

<!-- task:id=ECOM-020 phase=sprint3 status=done -->
## ECOM-020: Persist OrderCreated through the transactional outbox

Status: done

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

<!-- task:id=ECOM-021 phase=sprint3 status=done -->
## ECOM-021: Route Order outbox events through Debezium and Kafka

Status: done

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

<!-- task:id=ECOM-022 phase=sprint3 status=done -->
## ECOM-022: Add customer order queries and operations history

Status: done

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

<!-- task:id=ECOM-023 phase=sprint4 status=done -->
## ECOM-023: Add Payment Service persistence foundation

Status: done

Story coverage:
- US-16 Consume OrderCreatedEvent & Create Payment
- US-17 Idempotent Payment Event Processing
- US-19 Payment Result Events & Order Update

Scope:
- Add Spring Data JPA, Flyway, PostgreSQL runtime and H2 test dependencies to Payment Service.
- Configure the service-owned local `payments` PostgreSQL database without sharing Order Service entities or repositories.
- Add Flyway schema for payments, provider attempts, processed Kafka events and append-only outbox events.
- Model UUID identifiers, order/customer references from events, payment status, monetary precision, currency, provider references, event metadata, UTC timestamps, optimistic locking and read-oriented indexes.
- Add JPA entities and repositories that preserve the Payment Service boundary.
- Add migration/repository tests for constraints, uniqueness, processed-event idempotency keys and outbox persistence.
- Do not add Kafka listeners, provider calls or payment-result event production yet.

Allowed files:
- `services/payment-service/**`
- `pom.xml`
- `.env.example`
- `docs/**`
- `README.md`
- `STATE.md`

Validation:
- `scripts/project-validate.sh payment-test`
<!-- /task -->

<!-- task:id=ECOM-024 phase=sprint4 status=done -->
## ECOM-024: Consume OrderCreated and create pending payments

Status: done

Story coverage:
- US-16 Consume OrderCreatedEvent & Create Payment

Scope:
- Add Spring Kafka dependencies and Payment Service consumer configuration for the `ecommerce.order.events` topic.
- Define a Payment-owned `OrderCreated` envelope contract matching the Order Service outbox JSON payload.
- Validate event type, version, aggregate ID, order ID, customer ID, total amount and currency before persistence.
- Add a listener/application flow that creates one `PENDING` payment with order ID, customer ID, amount, currency and event metadata.
- Reject malformed, unsupported or incomplete payloads with structured logs and no partial payment records.
- Keep provider calls and duplicate-delivery idempotency for later Sprint 4 tasks.
- Add focused parser/listener/application tests for valid and invalid events.

Allowed files:
- `services/payment-service/**`
- `pom.xml`
- `.env.example`
- `docs/**`
- `README.md`
- `STATE.md`

Validation:
- `scripts/project-validate.sh payment-test`
<!-- /task -->

<!-- task:id=ECOM-025 phase=sprint4 status=done -->
## ECOM-025: Add idempotent OrderCreated event processing

Status: done

Story coverage:
- US-17 Idempotent Payment Event Processing

Scope:
- Use the `processed_events` table to make Payment Service handling idempotent by event ID and consumer name.
- Persist the processed-event marker in the same transaction as the payment creation or skip decision.
- Ensure duplicate delivery of the same `OrderCreated` event creates no second payment and no second provider attempt.
- Handle concurrent duplicate deliveries deterministically through database uniqueness and clear application behavior.
- Keep malformed payloads out of `processed_events` so corrected events can be replayed.
- Add service/listener tests for duplicate, concurrent duplicate and normal first-delivery paths.

Allowed files:
- `services/payment-service/**`
- `pom.xml`
- `docs/**`
- `README.md`
- `STATE.md`

Validation:
- `scripts/project-validate.sh payment-test`
<!-- /task -->

<!-- task:id=ECOM-026 phase=sprint4 status=done -->
## ECOM-026: Add mock payment provider client and attempt handling

Status: done

Story coverage:
- US-18 Mock Payment Provider via RestClient

Scope:
- Add a mock external payment provider client using Spring `RestClient` with environment-driven base URL and bounded timeouts.
- Externalize provider settings without real secrets, credentials or production endpoints.
- Add a payment processing application flow that calls the provider for pending payments only after idempotent `OrderCreated` handling.
- Persist provider attempt records, provider references and normalized outcomes.
- Map success, decline, timeout and 5xx responses into explicit domain results; transient provider failures must remain retryable for the later retry/DLQ task.
- Do not emit `PaymentSucceeded` or `PaymentFailed` events yet.
- Add focused client and application tests for success, decline, timeout, 5xx and malformed provider responses.

Allowed files:
- `services/payment-service/**`
- `pom.xml`
- `.env.example`
- `docs/**`
- `README.md`
- `STATE.md`

Validation:
- `scripts/project-validate.sh payment-test`
<!-- /task -->

<!-- task:id=ECOM-027 phase=sprint4 status=done -->
## ECOM-027: Persist payment result events through the outbox

Status: done

Story coverage:
- US-19 Payment Result Events & Order Update

Scope:
- Build versioned `PaymentSucceeded` and `PaymentFailed` event envelopes using the project event-envelope convention.
- Persist terminal payment status changes, provider attempt outcome and one append-only outbox event in the same transaction.
- Include event ID, aggregate ID, payment ID, order ID, event type/version, occurred time, trace ID, correlation ID, original `OrderCreated` event ID and result data.
- Keep JSON serialization deterministic and independent from JPA lazy proxies.
- Do not publish directly to Kafka and do not add a second transaction or after-commit outbox insert.
- Add integration tests proving atomic payment/result-event commit and rollback behavior.

Allowed files:
- `services/payment-service/**`
- `pom.xml`
- `docs/**`
- `README.md`
- `STATE.md`

Validation:
- `scripts/project-validate.sh payment-test`
<!-- /task -->

<!-- task:id=ECOM-028 phase=sprint4 status=done -->
## ECOM-028: Route Payment outbox events through Debezium and Kafka

Status: done

Story coverage:
- US-19 Payment Result Events & Order Update

Scope:
- Add a versioned Debezium PostgreSQL connector definition that captures only the Payment Service outbox table.
- Configure the outbox event router for `ecommerce.payment.events`, keyed by order ID or aggregate ID, while preserving the complete versioned event envelope.
- Add local placeholder environment variables required for Debezium logical replication without checking in real credentials.
- Add an idempotent connector registration helper and concise local runbook updates with connector/topic inspection commands.
- Add a bounded smoke helper that inserts one synthetic payment result outbox row and verifies event ID/order ID on the expected Kafka topic without deleting existing data.
- Validate JSON/config structure and Docker Compose wiring; do not implement an application-side Kafka publisher.

Allowed files:
- `infra/debezium/**`
- `infra/README.md`
- `docker-compose.yml`
- `.env.example`
- `scripts/register-payment-outbox-connector.sh`
- `scripts/verify-payment-cdc.sh`
- `services/payment-service/**`
- `docs/**`
- `README.md`
- `STATE.md`

Validation:
- `scripts/project-validate.sh payment-test`
- `scripts/project-validate.sh payment-cdc-config`
<!-- /task -->

<!-- task:id=ECOM-029 phase=sprint4 status=done -->
## ECOM-029: Consume payment result events in Order Service

Status: done

Story coverage:
- US-19 Payment Result Events & Order Update

Scope:
- Add Spring Kafka dependencies and Order Service consumer configuration for the `ecommerce.payment.events` topic.
- Define Order-owned `PaymentSucceeded` and `PaymentFailed` envelope contracts and validate event type, version, order ID and payment ID.
- Transition matching orders to `PAID` or `PAYMENT_FAILED` exactly once using the existing state machine and history model.
- If an order is still `CREATED`, move it through `PAYMENT_PENDING` before applying the terminal result so the V1 state machine remains valid and history is complete.
- Add an Order-owned processed-event marker so duplicate payment result events are acknowledged/skipped without duplicate history entries.
- Return stable handling for missing orders, invalid transitions and malformed events without exposing persistence internals.
- Add tests for success, failure, duplicate result events, CREATED-to-terminal progression, invalid payloads and missing orders.

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

<!-- task:id=ECOM-030 phase=sprint4 status=done -->
## ECOM-030: Add retry, backoff and dead-letter handling

Status: done

Story coverage:
- US-20 Retry, Backoff & Dead-Letter Handling

Scope:
- Configure bounded retry/backoff and dead-letter behavior for Payment Service `OrderCreated` consumption.
- Configure bounded retry/backoff and dead-letter behavior for Order Service payment-result consumption.
- Distinguish retryable provider or transient infrastructure failures from non-retryable malformed business payloads.
- Ensure replay after retry or dead-letter recovery does not create duplicate payments, provider attempts or order history entries.
- Add topic names, retry limits and backoff settings through environment-driven configuration with safe local defaults.
- Add focused tests for retryable failures, non-retryable poison messages, DLQ routing configuration and replay idempotency.
- Do not add broad Testcontainers or full E2E suites in this Sprint 4 task.

Allowed files:
- `services/payment-service/**`
- `services/order-service/**`
- `pom.xml`
- `.env.example`
- `docs/**`
- `README.md`
- `STATE.md`

Validation:
- `scripts/project-validate.sh payment-test`
- `scripts/project-validate.sh order-test`
<!-- /task -->

<!-- task:id=ECOM-031 phase=sprint5 status=done -->
## ECOM-031: Add MongoDB audit persistence foundation

Status: done

Story coverage:
- US-21 Central Audit Event Store in MongoDB

Scope:
- Add Spring Data MongoDB runtime configuration to Audit Notification Service using the existing local `audit` database and environment placeholders.
- Define an Audit-owned document model for the complete versioned event envelope plus source topic, partition, offset and receipt time.
- Store event ID, event type/version, aggregate ID, occurred time, trace ID, correlation ID, structured payload and searchable order/customer identifiers without importing Order or Payment domain classes.
- Add a unique event-ID index for idempotency and focused indexes for event type, aggregate/order ID, correlation ID and occurred time.
- Add repository/configuration tests that do not require a manually running MongoDB instance.
- Do not add Kafka listeners or notification adapters yet.

Allowed files:
- `services/audit-notification-service/**`
- `pom.xml`
- `.env.example`
- `docs/**`
- `README.md`
- `STATE.md`

Validation:
- `scripts/project-validate.sh audit-notification-test`
<!-- /task -->

<!-- task:id=ECOM-032 phase=sprint5 status=done -->
## ECOM-032: Consume and audit OrderCreated events idempotently

Status: done

Story coverage:
- US-21 Central Audit Event Store in MongoDB

Scope:
- Add an Audit-owned Spring Kafka consumer for `ecommerce.order.events` with an environment-driven topic, consumer group, retry/backoff and DLT settings.
- Parse and validate the version-1 `OrderCreated` envelope without reusing Order Service JPA or application classes.
- Persist the complete structured event and searchable metadata in MongoDB before acknowledging successful handling.
- Treat the unique event ID as the replay boundary so duplicate and concurrent duplicate deliveries create one audit document.
- Keep malformed envelopes out of the audit collection and classify them as non-retryable; keep transient Mongo/Kafka failures retryable and bounded.
- Add focused parser, listener, persistence and duplicate-delivery tests.
- Do not send email or push notifications yet.

Allowed files:
- `services/audit-notification-service/**`
- `pom.xml`
- `.env.example`
- `docs/**`
- `README.md`
- `STATE.md`

Validation:
- `scripts/project-validate.sh audit-notification-test`
<!-- /task -->

<!-- task:id=ECOM-033 phase=sprint5 status=done -->
## ECOM-033: Consume and audit payment result events idempotently

Status: done

Story coverage:
- US-21 Central Audit Event Store in MongoDB

Scope:
- Add an Audit-owned consumer for `PaymentSucceeded` and `PaymentFailed` events from `ecommerce.payment.events` with its own environment-driven group, retry/backoff and DLT settings.
- Parse and validate the version-1 payment-result envelope, including event/aggregate/order/payment/customer identifiers and terminal payment status.
- Persist the complete structured event and searchable metadata through the existing idempotent audit application flow.
- Ensure duplicates across retries or concurrent delivery produce one audit document and malformed events create no partial audit data.
- Keep Order and Payment service domain classes outside the Audit Notification Service boundary.
- Add focused success, failure, malformed, duplicate and transient-failure tests.
- Do not send email or push notifications yet.

Allowed files:
- `services/audit-notification-service/**`
- `pom.xml`
- `.env.example`
- `docs/**`
- `README.md`
- `STATE.md`

Validation:
- `scripts/project-validate.sh audit-notification-test`
<!-- /task -->

<!-- task:id=ECOM-034 phase=sprint5 status=done -->
## ECOM-034: Add replay-safe notification routing and delivery ledger

Status: done

Story coverage:
- US-22 Email Notifications
- US-23 Push Notification Adapter

Scope:
- Define which audited V1 events create customer notifications: order creation and terminal payment success/failure.
- Add a MongoDB notification-delivery document with channel, event ID, order/customer references, status, attempt metadata, safe failure detail and timestamps.
- Enforce one delivery record per event and channel with a compound unique index while allowing failed or interrupted attempts to be retried deterministically.
- Route valid audited events into `EMAIL` and `PUSH` delivery records without requiring real email addresses, device tokens or external credentials.
- Keep audit-event persistence and delivery bookkeeping independently replay-safe so a retry can recover missing deliveries without duplicating completed ones.
- Add routing and repository tests for relevant, irrelevant, duplicate and retry paths.
- Do not call an email or push provider yet.

Allowed files:
- `services/audit-notification-service/**`
- `pom.xml`
- `.env.example`
- `docs/**`
- `README.md`
- `STATE.md`

Validation:
- `scripts/project-validate.sh audit-notification-test`
<!-- /task -->

<!-- task:id=ECOM-035 phase=sprint5 status=done -->
## ECOM-035: Add mock email notification delivery

Status: done

Story coverage:
- US-22 Email Notifications

Scope:
- Add a local mock/test email adapter behind an Audit-owned notification port; do not add SMTP credentials or a real provider integration.
- Build deterministic email payloads for order-created, payment-succeeded and payment-failed events using customer, order and event metadata from the delivery record.
- Dispatch pending email deliveries and transition the ledger through explicit success/failure states with bounded retry behavior.
- Ensure replay of the same business event or delivery request does not send a second completed email.
- Log only safe delivery metadata and never invent or expose real customer contact details.
- Add focused adapter, payload, retry and duplicate-delivery tests.

Allowed files:
- `services/audit-notification-service/**`
- `pom.xml`
- `.env.example`
- `docs/**`
- `README.md`
- `STATE.md`

Validation:
- `scripts/project-validate.sh audit-notification-test`
<!-- /task -->

<!-- task:id=ECOM-036 phase=sprint5 status=done -->
## ECOM-036: Add mock push notification delivery

Status: done

Story coverage:
- US-23 Push Notification Adapter

Scope:
- Add a local mock/test push adapter behind the existing notification port boundary; do not add real device tokens, credentials or a production push SDK.
- Build deterministic push payloads linked to the customer, order, event and notification type.
- Dispatch pending push deliveries and update the delivery ledger through explicit success/failure states with bounded retry behavior.
- Ensure duplicate business events and replayed delivery work do not send a second completed push notification.
- Keep channel-specific adapter code separate from audit persistence and shared routing rules.
- Add focused adapter, payload, retry and duplicate-delivery tests.

Allowed files:
- `services/audit-notification-service/**`
- `pom.xml`
- `.env.example`
- `docs/**`
- `README.md`
- `STATE.md`

Validation:
- `scripts/project-validate.sh audit-notification-test`
<!-- /task -->

<!-- task:id=ECOM-037 phase=sprint5 status=done -->
## ECOM-037: Complete end-to-end correlation and trace propagation

Status: done

Story coverage:
- US-24 End-to-End Correlation & Trace Propagation

Scope:
- Verify and complete propagation of `traceId`, `correlationId` and `eventId` across Gateway, Order creation, both Kafka hops, Payment processing, audit persistence and notification delivery.
- Enable Spring Kafka observation/tracing where needed and create consumer processing spans without replacing the event-envelope correlation contract.
- Restore envelope correlation and event metadata into structured consumer logging context for the duration of handling, then clear it safely.
- Preserve incoming `X-Correlation-Id` through Gateway and Order; generate a value only when the request does not provide one.
- Keep identifiers in audit documents and notification logs so one purchase can be followed without using high-cardinality metric tags.
- Add focused propagation tests and update the observability runbook with a concrete local trace/correlation inspection flow.
- Do not add broad Sprint 6 E2E/Testcontainers coverage.

Allowed files:
- `services/gateway-service/**`
- `services/order-service/**`
- `services/payment-service/**`
- `services/audit-notification-service/**`
- `libs/**`
- `pom.xml`
- `.env.example`
- `docs/**`
- `README.md`
- `STATE.md`

Validation:
- `scripts/project-validate.sh observability-test`
<!-- /task -->

<!-- task:id=ECOM-038 phase=sprint5 status=done -->
## ECOM-038: Add bounded operational service metrics

Status: done

Story coverage:
- US-25 Operational Metrics & Alert Signals

Scope:
- Keep Spring Boot HTTP latency/error metrics and add focused custom meters for Catalog cache hit/miss, Kafka consumer outcomes, terminal payment failures and notification delivery outcomes.
- Record retry and dead-letter publication activity for the Order, Payment and Audit Notification consumer flows where those signals are not already exposed.
- Use stable low-cardinality tags such as service, topic, event type, channel and outcome; never tag metrics with event, order, payment, customer, trace or correlation IDs.
- Name and describe meters consistently so Prometheus queries remain understandable across services.
- Add focused meter tests using an in-memory registry and simulated success/failure paths.
- Update the observability runbook with metric names and representative PromQL queries.
- Do not add dashboards, external alert delivery or load tests.

Allowed files:
- `services/catalog-service/**`
- `services/order-service/**`
- `services/payment-service/**`
- `services/audit-notification-service/**`
- `libs/**`
- `pom.xml`
- `.env.example`
- `docs/**`
- `README.md`
- `STATE.md`

Validation:
- `scripts/project-validate.sh observability-test`
<!-- /task -->

<!-- task:id=ECOM-039 phase=sprint5 status=done -->
## ECOM-039: Add Prometheus alert rules and DLQ depth signals

Status: done

Story coverage:
- US-25 Operational Metrics & Alert Signals

Scope:
- Expose a bounded Kafka DLT retained-record/depth gauge for the known Order, Payment and Audit Notification dead-letter topics using environment-driven configuration and safe behavior when Kafka is unavailable.
- Keep topic names as a fixed configured set and avoid dynamic high-cardinality labels or broker-wide discovery.
- Add Prometheus rule files and Docker Compose wiring for service-down, sustained HTTP error, payment-failure, notification-failure, consumer-failure and positive-DLQ-depth signals.
- Add configuration/unit tests for gauge refresh behavior, unavailable brokers, rule loading and expected metric expressions.
- Document local Prometheus queries, rule inspection and a bounded failure simulation that does not delete Kafka or application data.
- Keep alert notification delivery and production monitoring infrastructure out of Sprint 5.

Allowed files:
- `services/audit-notification-service/**`
- `infra/observability/**`
- `docker-compose.yml`
- `.env.example`
- `docs/**`
- `infra/README.md`
- `README.md`
- `STATE.md`

Validation:
- `scripts/project-validate.sh audit-notification-test`
- `scripts/project-validate.sh observability-config`
<!-- /task -->
