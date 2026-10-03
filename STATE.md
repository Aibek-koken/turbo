# Project State

Last updated: 2026-10-03

## Current Status

ECOM-015 is complete. Sprint 2 now has the Redis/Redisson catalog cache
foundation plus product-detail cache-aside, invalidation and stampede
protection in place, and the Spring Batch supplier-import path now validates
supplier CSV rows, skips bad rows with deterministic error reports, upserts by
category slug and product SKU without duplicate records, evicts changed
product-detail cache entries after committed import chunks, and exposes
protected `CATALOG_ADMIN` launch/status APIs constrained to a configured local
import directory. The import control layer rejects traversal, remote/URL-style
paths and non-CSV files, prevents concurrent launches of the same supplier file,
returns sanitized processed/skipped/failed counts plus the relative error-report
location, and restarts failed chunks by relaunching the same supplier file
without duplicating committed records. The repository has a Java 21 / Spring
Boot 3.x Maven monorepo scaffold, a local Docker Compose infrastructure baseline, an
importable Keycloak realm for local gateway/API testing, secure gateway
routing, service-level RBAC for the first downstream service boundaries and a
local observability baseline. Catalog Service now has its initial PostgreSQL
schema, Flyway migrations, JPA entities, repositories, admin management API and
customer-facing browse/detail API plus Redis-backed product-detail cache-aside
reads, write-side invalidation, Redisson miss protection and a restartable
chunk-oriented supplier CSV import job.

- `services/gateway-service`
- `services/catalog-service`
- `services/order-service`
- `services/payment-service`
- `services/audit-notification-service`

Each service has its own Maven module, Spring Boot application class and
actuator-ready `application.yml`. No shared business domain or shared database
model was introduced.

Prepared by this setup:

- Compact architecture baseline: `DESIGN.md`
- Agent operating guide: `AGENTS.md`
- Night runner queue: `docs/night-runner/task-queue.md`
- Night runner handoff: `docs/night-runner/handoff.md`
- Night runner scripts under `scripts/`
- Root Maven parent: `pom.xml`
- Developer setup notes: `README.md` and `docs/developer-setup.md`
- Service RBAC notes: `docs/service-rbac.md`
- Observability runbook: `docs/observability-runbook.md`
- Supplier import contract: `docs/catalog/supplier-import.md`
- Local infrastructure compose: `docker-compose.yml`
- Local placeholder environment sample: `.env.example`
- Infrastructure notes and configs under `infra/`
- Shared security helper module: `libs/security-support`

ECOM-002 added infrastructure containers for PostgreSQL, Redis, Kafka,
Debezium Connect, MongoDB, Keycloak, Prometheus and Jaeger. Postgres initializes
local `catalog`, `orders`, `payments` and `keycloak` databases and is configured
for future logical replication. Prometheus scrapes itself, Keycloak and the host
Spring service actuator Prometheus endpoints.

ECOM-003 added `infra/keycloak/ecommerce-realm.json` and wired the Keycloak
container to import it with `start-dev --import-realm`. The realm is named
`ecommerce`, defines the `CUSTOMER`, `CATALOG_ADMIN` and `OPS_ADMIN` realm
roles, and includes public local clients `ecommerce-gateway` and
`ecommerce-local-test` for gateway/API token testing. Documentation now covers
placeholder-only local token retrieval without checked-in real credentials,
client secrets or tokens.

ECOM-004 added Spring Cloud Gateway routes for catalog, order, payment and
audit-notification APIs. Route targets default to the local service ports and
can be overridden with environment variables. The gateway is now a JWT resource
server using the local Keycloak issuer/JWK defaults, keeps selected actuator
endpoints public and requires bearer-token authentication for `/api/**`.
Configuration tests were added for route coverage plus missing/invalid bearer
token behavior.

ECOM-005 made catalog, order, payment and audit-notification services JWT
resource servers. Each service now maps Keycloak `realm_access.roles` through
`libs/security-support`, keeps selected actuator endpoints public, denies
unmatched API paths by default and exposes temporary RBAC probe endpoints under
the service API path. Documentation records role/path rules and the later
customer ownership checks needed for catalog/order/payment work.

ECOM-006 added a concrete observability baseline across all five services.
Each service now tags Micrometer metrics with its application name, exposes
health/info/Prometheus actuator endpoints, uses W3C trace propagation and
exports spans to the local Jaeger OTLP HTTP endpoint by default. The gateway
normalizes and forwards `X-Correlation-Id`, servlet services add that value to
response headers and logging MDC, and service log patterns include application,
trace, span and correlation IDs. Docker Compose now mounts
`infra/observability/prometheus.yml`, which scrapes Prometheus, Keycloak and
all five local Spring service actuator Prometheus endpoints.

ECOM-007 added the Catalog Service persistence baseline. The catalog module now
depends on Spring Data JPA, Flyway, the PostgreSQL driver and H2 for tests.
`V1__create_catalog_schema.sql` creates `categories`, `products` and
`product_attributes` with required fields, SKU uniqueness, status checks,
foreign keys and read-oriented indexes. JPA entities and repositories mirror the
schema, including detail fetch plans for category and product attributes.
Repository and migration tests were added for the catalog module.

ECOM-008 added Catalog Service admin management APIs under
`/api/catalog/admin/**`. Catalog admins can create, update and deactivate
categories, products and product attributes. Request DTOs use Bean Validation,
domain failures return Problem Details 4xx responses, admin writes are guarded
by the existing `CATALOG_ADMIN` route rule plus method security, and MockMvc
tests cover authorized writes, missing/incorrect roles, invalid requests and a
duplicate-slug domain failure. `V2__add_product_attribute_active_flag.sql`
adds soft-deactivation support for product attributes.

ECOM-009 added Catalog Service customer browse/detail APIs under
`/api/catalog/products`. Customer-facing reads require the existing catalog GET
roles, return only `ACTIVE` products in active categories, support pagination
plus category, active-status and text/SKU filters, and use repository fetch
plans for category/detail reads to avoid obvious N+1 access. Product details
include category and active attributes. MockMvc tests cover pagination, filters,
detail response shape, hidden draft/inactive state and forbidden customer access
to admin-only write paths. Catalog tests also now use an H2 PostgreSQL-mode URL
that lets Flyway and Hibernate schema validation agree on table metadata.

ECOM-010 added the Catalog Service Redis/Redisson cache foundation. The catalog
module now depends on Spring Data Redis and Redisson, binds Redis connection and
product-detail cache settings from environment variables, and exposes a focused
`ProductDetailCache` abstraction with Redis and no-op implementations. Product
detail cache keys are stable and namespaced, lock keys are generated alongside
data keys for the next stampede-protection task, and cached values use explicit
Jackson JSON serialization without Java or polymorphic type metadata. Existing
web/API tests run with catalog caching and Redis health disabled so database
reads remain functional without Redis in test/local troubleshooting contexts.
Focused cache tests cover configuration binding/bounds, deterministic key
generation and product-detail JSON round-tripping.

ECOM-011 wired product-detail cache-aside behavior into customer detail reads.
Valid active cache hits return without querying the product repository, while
misses load the authoritative active product from PostgreSQL and populate Redis
through the configured TTL path. Admin product and attribute create/update/
deactivate operations schedule affected product cache eviction after commit.
Category update/deactivation now invalidates affected product entries through a
bounded paged product-ID query. Focused tests cover hit, miss, inactive cached
entry rejection, Redis TTL writes and the product, attribute and category
invalidation paths.

ECOM-012 added per-product Redisson stampede protection around product-detail
cache misses. Detail reads now check Redis, acquire the configured product lock
on a miss, re-check Redis inside the lock, then perform the PostgreSQL load and
cache write only if the entry is still absent. Lock wait and lease durations use
the existing bounded `catalog.cache` settings, lock timeout/interruption produce
explicit failures, and unlock is attempted only when the current thread owns the
lock. Tests cover Redisson wait/lease usage, timeout, interruption, owned-lock
release and a deterministic two-thread miss proving one repository load.

ECOM-013 added the Catalog Service Spring Batch supplier import foundation. The
catalog module now depends on Spring Batch, disables automatic job startup and
schema initialization, binds `catalog.supplier-import.chunk-size` from the
environment and stores Batch metadata through Flyway migration
`V3__create_catalog_batch_metadata.sql` in the Catalog database. The
`supplierImportJob` and `supplierImportStep` read a documented supplier CSV
contract from an `inputFile` job parameter with restartable reader state and
chunk-oriented processing. Documentation and a non-production sample CSV were
added, and tests cover job/step registration plus chunked processing of a valid
CSV into H2 with Batch metadata persisted in JDBC tables.

ECOM-014 completed supplier CSV row validation and error reporting. CSV rows
now carry source line numbers, expected validation failures are represented as
skippable row-validation exceptions, and the supplier import step skips
validation/read-format failures without skipping unexpected writer/database
failures. Each run writes a deterministic `row_number,reason` error report to
the optional `errorReportFile` job parameter or to the default
`<inputFile>.errors.csv` path, and records the report path/count in Batch
execution context. Import writes keep per-chunk category/product maps so
duplicate slugs/SKUs in the same chunk update the same JPA entities, products
upsert by SKU, categories upsert by slug, currencies normalize uppercase, and
changed product-detail cache entries are evicted after chunk commit. Tests now
cover mixed valid/invalid files, update upserts, duplicate SKU rows, report
contents and import-driven cache eviction.

ECOM-015 added protected Catalog Service supplier-import control. Catalog admins
can launch imports through `POST /api/catalog/admin/supplier-imports` using a
relative `.csv` path under `catalog.supplier-import.import-directory` and
inspect executions through `GET /api/catalog/admin/supplier-imports/{executionId}`.
The control service rejects path traversal, absolute paths, URL-style values and
non-CSV files, avoids exposing host paths by returning relative error-report
locations, blocks duplicate running launches for the same supplier file, and
uses the canonical input file as the identifying Batch job parameter so failed
chunks restart safely without duplicating already committed product/category
upserts. Tests cover import authorization, path validation, duplicate launch
protection, launch/status responses and failure/restart idempotency.

## Active Delivery Target

Night run target:

1. Sprint 1 complete.
2. Sprint 2 complete through ECOM-015.
3. Sprint 3 is queued as ECOM-016 through ECOM-022, covering US-11 through US-15.

Sprint 3 execution order:

- Order persistence foundation.
- Catalog snapshot client and authenticated order creation.
- Order state machine and status history.
- Transactional `OrderCreated` outbox persistence.
- Debezium CDC routing to `ecommerce.order.events`.
- Customer-owned and operations order query/history APIs.

Excluded from this Sprint 3 run:

- Payment consumption and payment-result handling.
- Audit and notification flows.
- Broad E2E/Testcontainers work.
- CI release pipeline.

## Environment Notes

- Maven is available on this machine.
- Docker is available on this machine.
- The default shell Java still appears to be Java 17, but
  `/usr/libexec/java_home -v 21` locates OpenJDK 21.0.11. The project target
  remains Java 21.
- The runner does not require a Git repository, but Git is recommended before
  long overnight work because it improves rollback and changed-file tracking.

## Latest Validation

ECOM-015 validation passed:

```bash
scripts/project-validate.sh catalog-test
```

Running plain `mvn -q -pl services/catalog-service -am test` without setting
`JAVA_HOME` still picks up Java 17 and fails with `release version 21 not
supported`.

The runner and `scripts/project-validate.sh` select the installed Java 21 even
when the parent shell exports a Java 17 `JAVA_HOME`. The ECOM-015 protected
supplier import launch/status, path validation, duplicate-launch guard,
restartability and final-data idempotency work passed the full Catalog Service
tests:

```bash
scripts/project-validate.sh catalog-test
```

Order Service tests use Mockito's subclass mock maker because this machine's
Java 21 runtime does not permit Byte Buddy's inline self-attachment.

Sprint 3 runner preparation validation passed:

```bash
bash -n scripts/night-agent-runner.sh scripts/night-agent-prompt-builder.sh scripts/night-agent-safe-approve.sh scripts/night-agent-status.sh scripts/project-validate.sh
scripts/project-validate.sh order-test
scripts/night-agent-runner.sh --agent codex --overnight --phase sprint3 --max-minutes 28800 --dry-run
```

## Next Command

Sprint 2 is committed at `758bd38`. Commit the Sprint 3 queue preparation, then
check the first generated task without running it:

```bash
scripts/night-agent-runner.sh --agent codex --overnight --phase sprint3 --max-minutes 28800 --dry-run
```

After a successful dry run, start the Sprint 3 queue under `caffeinate`:

```bash
caffeinate -dimsu scripts/night-agent-runner.sh --agent codex --overnight --phase sprint3 --max-minutes 28800
```

Next task: ECOM-016.
