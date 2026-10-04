# Project State

Last updated: 2026-10-04

## Current Status

ECOM-030 is complete. Payment Service now has environment-driven bounded retry,
backoff and dead-letter settings for `OrderCreated` consumption, including a
Spring Kafka `DefaultErrorHandler`, configured DLT destination and string
producer serializers for local DLT publication. Malformed `OrderCreated`
payloads are rethrown after structured rejection logging and classified as
non-retryable so they route to DLT without repeated retries. Retryable provider
timeout/5xx outcomes now clear the temporary processed-event claim before the
transaction commits, allowing Kafka redelivery to retry provider authorization
against the existing pending payment instead of creating a duplicate payment;
terminal success/failure keeps the processed marker and duplicate replay skips
without another provider call. Order Service now has the same environment-driven
bounded retry/backoff/DLT configuration for payment-result consumption.
Malformed payment-result payloads are classified as non-retryable for DLT
routing, while transient updater/database failures remain retryable. Order
payment-result idempotency now uses the configured consumer group ID as its
consumer name, keeping replay markers aligned with the listener configuration.
Focused listener, configuration and replay tests cover retryable provider
failures, poison payload propagation to the error handler, DLT destination
routing, bounded retry properties and duplicate replay idempotency. Validation
passed: `scripts/project-validate.sh payment-test` and
`scripts/project-validate.sh order-test`. Next task: none queued.

ECOM-029 is complete. Order Service now consumes versioned payment result
events from `ecommerce.payment.events` through Spring Kafka string consumer
configuration with environment-driven topic, group ID and listener enablement
placeholders. The Order-owned payment result contracts parse and validate
`PaymentSucceeded` and `PaymentFailed` envelopes, including event type,
version, event ID, aggregate/order ID consistency, occurred time and payment
ID. Malformed payloads are rejected in the listener without writing partial
state. Order persistence now has an Order-owned `processed_events` marker
table and JPA repository keyed by `(consumer_name, event_id)`. Valid result
events are handled transactionally: the marker is claimed in the same
transaction as the status update or stable skip, matching orders transition to
`PAID` or `PAYMENT_FAILED` through the existing state machine/history model,
and orders still in `CREATED` first move through `PAYMENT_PENDING` so history
remains complete. Duplicate result events are acknowledged/skipped without
extra history rows; missing orders, already-terminal orders and invalid
transitions return stable skip results without exposing persistence internals.
Focused parser, listener, migration and updater tests cover success, failure,
duplicate events, CREATED-to-terminal progression, malformed payloads, missing
orders and invalid transitions. Validation passed:
`scripts/project-validate.sh order-test`. Next task: ECOM-030.

ECOM-028 is complete. Payment Service outbox events now have a dedicated
versioned Debezium PostgreSQL connector definition at
`infra/debezium/payment-outbox-connector.json`. The connector captures only
`payments.public.outbox_events`, routes through the outbox event router to
`ecommerce.payment.events`, uses `aggregate_id`/order ID as the Kafka key and
emits the stored JSON `payload` as the complete versioned
`PaymentSucceeded`/`PaymentFailed` event envelope. Docker Compose and
`.env.example` now include local placeholder Debezium logical-replication
settings for the Payment database, slot, publication and topic prefix. Local
helpers now support idempotent connector registration and a bounded CDC smoke
check that inserts one synthetic payment result outbox row and verifies the
event ID plus order ID on the expected Kafka topic without deleting existing
data. The infrastructure runbook documents connector registration, status,
topic inspection and smoke-test commands. Validation passed:
`scripts/project-validate.sh payment-test` and
`scripts/project-validate.sh payment-cdc-config`. Next task: ECOM-029.

ECOM-027 is complete. Payment Service now persists terminal
`PaymentSucceeded` and `PaymentFailed` result events through the Payment-owned
outbox. The result event factory builds deterministic version-1 envelopes from
scalar payment and attempt values, including event ID, event type/version,
aggregate/order ID, occurred time, trace ID, correlation ID and result data
with payment ID, customer ID, amount, currency, payment status, provider
attempt ID/outcome, provider reference or safe failure reason, and the original
`OrderCreated` event ID. The same transactional `OrderCreated` processing flow
marks the payment terminal, records the provider attempt outcome and appends
one outbox row for successful, declined or malformed terminal provider results;
timeout and provider-5xx attempts remain pending for retry/DLQ work and emit no
result event. No direct Kafka publishing, after-commit insert or second
transaction was added. Focused integration tests cover committed success,
decline and malformed-result event envelopes, duplicate delivery without a
second event, retryable outcomes without events and rollback of payment,
attempt, processed-event and outbox rows. Validation passed:
`scripts/project-validate.sh payment-test`. Next task: ECOM-028.

ECOM-026 is complete. Payment Service now has a local mock payment provider
client backed by Spring `RestClient`, with environment-driven base URL,
authorize path, connect timeout and read timeout placeholders. The
`OrderCreated` application flow remains idempotency-gated: only the first
claimed event that creates a new pending payment creates a provider attempt and
calls the provider; duplicate events and already-existing payments do not make
another provider call. Provider attempts now persist a normalized outcome
(`SUCCEEDED`, `DECLINED`, `TIMED_OUT`, `PROVIDER_5XX` or
`MALFORMED_RESPONSE`) alongside the coarse attempt status. Approved provider
responses mark the payment `SUCCEEDED` with a provider reference, declines and
malformed provider responses mark the payment `FAILED` with safe failure
reasons, and timeout/5xx outcomes persist failed attempt records while leaving
the payment `PENDING` for the later retry/DLQ task. No
`PaymentSucceeded`/`PaymentFailed` outbox events are emitted yet. Focused
provider-client and application tests cover approved, declined, timeout, 5xx,
malformed provider response and duplicate/concurrent duplicate delivery paths.
Validation passed: `scripts/project-validate.sh payment-test`. Next task:
ECOM-027.

ECOM-025 is complete. Payment Service now processes valid `OrderCreated`
events idempotently by claiming `(consumer_name, event_id)` in the
`processed_events` table before creating a payment. The claim and the payment
creation, duplicate skip, or existing-payment skip all execute through the same
application transaction, with PostgreSQL using `ON CONFLICT DO NOTHING` and H2
test runs using an equivalent deterministic claim path. Duplicate and
concurrent duplicate deliveries of the same valid event now create one
`PENDING` payment, one processed-event marker and no provider attempts.
Malformed payloads are still rejected in the listener before the application
service, so no processed-event marker is written for bad messages. Focused
service and listener tests cover first delivery, sequential duplicate,
concurrent duplicate and malformed payload paths. Validation passed:
`scripts/project-validate.sh payment-test`. Next task: ECOM-026.

ECOM-024 is complete. Payment Service now consumes `OrderCreated` messages from
`ecommerce.order.events` through Spring Kafka string consumer configuration and
a Payment-owned versioned envelope parser. The parser validates malformed,
unsupported and incomplete payloads before persistence, including event type,
version, event/aggregate/order IDs, `CREATED` status, customer ID,
positive `NUMERIC(19,4)` total amount and uppercase three-letter currency. A
Kafka listener logs rejected records with structured metadata and creates no
partial records; valid events flow into a transactional application service
that persists one `PENDING` payment with order ID, string customer ID, amount,
currency and source event ID. Payment customer IDs now match the Order-owned
JWT subject contract as `VARCHAR(128)` instead of UUID. Provider calls and
duplicate-delivery idempotency remain deferred to later Sprint 4 tasks.
Focused parser, listener and application tests were added. Validation passed:
`scripts/project-validate.sh payment-test`. Next task: ECOM-025.

ECOM-023 is complete. Payment Service now has its persistence foundation:
Spring Data JPA, Flyway, PostgreSQL runtime wiring, H2 test support, local
`payments` database configuration, and a versioned schema for `payments`,
`payment_attempts`, `processed_events` and `outbox_events`. The Payment-owned
JPA entities and repositories model UUID identifiers, order/customer references
from events, `PENDING`/`SUCCEEDED`/`FAILED` status, `NUMERIC(19,4)` money,
three-letter uppercase currency constraints, provider references, event
metadata, UTC `Instant` timestamps, optimistic locking on mutable payments,
processed-event idempotency keys and read-oriented indexes. Repository and
migration tests cover uniqueness, constraints, processed-event idempotency and
outbox payload persistence without adding Kafka listeners, provider calls or
event production. Validation passed: `scripts/project-validate.sh payment-test`.
ECOM-024 followed this task.

ECOM-022 is complete. Order Service now exposes customer-owned order query
APIs at `GET /api/orders/customer/orders` and
`GET /api/orders/customer/orders/{orderId}`, plus an operations support lookup
at `GET /api/orders/ops/orders/{orderId}`. Customer list pagination is bounded
and deterministic by `created_at DESC, id DESC`, ownership comes only from the
JWT subject, and non-owned customer detail uses the same stable not-found
Problem Details response as a missing order. Query responses include immutable
item snapshots, subtotal/total/currency, current status, timestamps and
chronologically ordered status history. The query service hydrates page results
with focused bulk item/history repository queries instead of per-order child
loads. Repository, service and MockMvc tests cover ownership isolation,
operations access, response shape, pagination bounds and history ordering.
Validation passed: `scripts/project-validate.sh order-test`. Next task: none
queued in the current Sprint 3 task queue.

ECOM-021 is complete. The repository now includes a versioned Debezium
PostgreSQL connector definition for the Order Service outbox table at
`infra/debezium/order-outbox-connector.json`. The connector captures only
`public.outbox_events` from the `orders` database, uses the outbox event router
to publish to `ecommerce.order.events`, keys messages by `aggregate_id` and
emits the stored JSON `payload` as the complete versioned event envelope.
Docker Compose wires local placeholder environment variables for Debezium
logical replication credentials, slot and publication names through Kafka
Connect's env config provider. Local helpers now support idempotent connector
registration and a bounded CDC smoke check that inserts one synthetic outbox
row and verifies its event ID and aggregate ID on the expected Kafka topic
without deleting existing data. The local infrastructure runbook documents
registration, connector status, topic inspection and smoke-test commands.
Validation passed:
`scripts/project-validate.sh order-test` and
`scripts/project-validate.sh order-cdc-config`.

ECOM-020 is complete. Order creation now persists a versioned `OrderCreated`
outbox event in the same Spring transaction as the order aggregate, items and
initial status history. The event row includes event ID, aggregate ID, event
type/version, UTC occurrence time, trace ID, correlation ID and a deterministic
JSON envelope whose `data` field is built from an immutable order snapshot
rather than JPA entities or lazy proxies. Outbox payloads are stored as JSON
objects through the existing append-only `outbox_events` table, and no
application-side Kafka publisher, after-commit insert or second transaction was
added. Integration tests cover a committed order/outbox pair, trace and
correlation propagation, envelope contents and a forced rollback that leaves
neither order nor outbox row.

ECOM-019 is complete. Order Service now has a centralized V1 state machine for
`CREATED`, `PAYMENT_PENDING`, `PAID`, `PAYMENT_FAILED` and `CANCELLED`,
an application transition service for future internal payment handling, and an
`OPS_ADMIN` operations API at
`POST /api/orders/ops/orders/{orderId}/transitions`. Accepted transitions update
the current order status and append a timestamped history entry in one database
transaction. No-op and invalid transitions return Problem Details and leave the
order/history unchanged. Existing JPA optimistic locking protects mutable order
state from concurrent overwrite. Tests cover the exhaustive transition matrix,
persistence/history behavior, operations authorization, invalid/no-op rejection
and stale concurrent update detection.

ECOM-018 is complete. Order Service now exposes authenticated customer order
creation at `POST /api/orders/customer/orders`, deriving ownership only from
the JWT `sub` claim. The create flow accepts product IDs and bounded positive
quantities, rejects empty orders, duplicate product IDs, missing IDs, invalid
quantities and mixed currencies with Problem Details, resolves snapshots
through the Order-owned Catalog client outside the database transaction,
calculates item/order totals with `BigDecimal`, and atomically persists the
order, immutable item snapshots and initial `CREATED` history row. Service and
MockMvc tests cover totals, snapshot persistence, JWT-subject ownership,
client-supplied name/price/customer fields being ignored, CUSTOMER role access
and the specified rejection paths.

ECOM-017 is complete. Sprint 3 now has an Order-owned Catalog product-snapshot
client: a replaceable `CatalogProductClient` abstraction backed by Spring
`RestClient`, an environment-driven `ORDER_SERVICE_CATALOG_BASE_URL`, forwarding
of the inbound bearer token and correlation ID, an immutable `ProductSnapshot`
contract, explicit product-resolution failure types for missing, inactive,
malformed and unavailable Catalog responses, API Problem Details mapping and a
transaction guard that prevents outbound Catalog calls inside active database
transactions. Focused client tests cover valid snapshots, forwarded headers,
missing products, inactive products, malformed responses, unavailable Catalog
responses and missing bearer-token rejection without a trusted anonymous bypass.

ECOM-016 is complete. Sprint 3 also has the Order Service persistence
foundation: Spring Data JPA, Flyway, PostgreSQL runtime wiring, H2 test support,
an initial orders schema, immutable order-item snapshots, status history,
append-only outbox persistence, JPA entities/repositories and focused
migration/repository tests for unique, foreign-key, currency and monetary
constraints.

Sprint 2 has the Redis/Redisson catalog cache foundation plus product-detail
cache-aside, invalidation and stampede protection in place, and the Spring
Batch supplier-import path now validates
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

ECOM-016 added the Order Service persistence foundation. The order module now
depends on Spring Data JPA, Flyway, PostgreSQL and H2 tests, connects by
default to the service-owned local `orders` database, validates Hibernate
metadata against Flyway and uses UTC JDBC timestamps. `V1__create_order_schema.sql`
creates `orders`, `order_items`, `order_status_history` and `outbox_events`
with UUID identifiers, status/currency checks, `NUMERIC(19,4)` money columns,
optimistic `version`, immutable product snapshot fields, append-only JSON
outbox payloads, foreign keys, uniqueness constraints and read-oriented
indexes. JPA entities and repositories stay inside the Order boundary and do
not reference Catalog entities or repositories. Tests cover migration metadata,
aggregate/outbox persistence, duplicate product snapshots, missing parent
orders, lowercase currencies and negative money.

ECOM-018 added authenticated customer order creation. The Order Service now has
a customer create endpoint under `/api/orders/customer/orders`, request/response
contracts, an application service that validates order lines before Catalog
lookup, a transactional persistence step for order/items/initial history, and
Problem Details mapping for create-order validation and Catalog snapshot
resolution failures. The API does not accept customer IDs, product names or
prices from clients as authoritative data.

## Active Delivery Target

Night run target:

1. Sprint 1 complete.
2. Sprint 2 complete through ECOM-015.
3. Sprint 3 is complete through ECOM-022, covering US-11 through US-15.
4. Sprint 4 is complete through ECOM-030, covering the Payment persistence
   foundation, `OrderCreated` consumption, idempotent duplicate handling, mock
   provider attempts, terminal payment-result outbox persistence, Debezium
   routing for payment result events and Order Service consumption/status
   updates plus bounded retry/backoff/dead-letter handling for both payment
   event consumers. No further Sprint 4 task is queued.

Sprint 4 prepared execution order:

- Payment Service persistence foundation. (complete)
- `OrderCreated` consumption and pending payment creation. (complete)
- `processed_events` idempotency for duplicate Kafka deliveries. (complete)
- Mock provider RestClient and provider-attempt handling. (complete)
- `PaymentSucceeded`/`PaymentFailed` outbox persistence. (complete)
- Payment outbox Debezium routing to `ecommerce.payment.events`. (complete)
- Order Service consumption of payment results and status transitions. (complete)
- Retry/backoff/dead-letter handling for payment event flows. (complete)

Completed Sprint 3 execution order:

- Order persistence foundation.
- Catalog snapshot client and authenticated order creation.
- Order state machine and status history.
- Transactional `OrderCreated` outbox persistence.
- Debezium CDC routing to `ecommerce.order.events`.
- Customer-owned and operations order query/history APIs.

Excluded from this Sprint 4 run:

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

ECOM-030 validation passed:

```bash
scripts/project-validate.sh payment-test
scripts/project-validate.sh order-test
```

ECOM-029 validation passed:

```bash
scripts/project-validate.sh order-test
```

ECOM-028 validation passed:

```bash
scripts/project-validate.sh payment-test
scripts/project-validate.sh payment-cdc-config
```

ECOM-027 validation passed:

```bash
scripts/project-validate.sh payment-test
```

ECOM-025 validation passed:

```bash
scripts/project-validate.sh payment-test
```

Sprint 4 runner preparation validation passed:

```bash
scripts/night-agent-runner.sh --agent codex --overnight --phase sprint4 --dry-run
scripts/night-agent-status.sh
git diff --check
scripts/project-validate.sh payment-test
```

ECOM-022 validation passed:

```bash
scripts/project-validate.sh order-test
```

ECOM-018 validation passed:

```bash
scripts/project-validate.sh order-test
```

ECOM-017 validation passed:

```bash
scripts/project-validate.sh order-test
```

ECOM-016 validation passed:

```bash
scripts/project-validate.sh order-test
```

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

Continue Sprint 4 with the next queued task, ECOM-030. Dry-run the runner with:

```bash
scripts/night-agent-runner.sh --agent codex --overnight --phase sprint4 --max-minutes 28800 --dry-run
```

Start the overnight Sprint 4 runner from the repo root with:

```bash
caffeinate -dimsu scripts/night-agent-runner.sh --agent codex --overnight --phase sprint4 --max-minutes 28800
```

Next task: ECOM-030.
