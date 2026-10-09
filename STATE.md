# Project State

Last updated: 2026-10-09

## Current Status

ECOM-052 is complete. The CI workflow is in place at
`.github/workflows/ci.yml` with Java 21,
read-only repository permissions, concurrency cancellation, pinned major
actions, Maven dependency caching, unit/OpenAPI tests, contract tests,
Docker-gated integration tests, local Compose image builds, full-stack smoke
coverage and failure diagnostics upload. `scripts/project-validate.sh` now has
stronger `ci-config` checks, a `compose-image-build` mode and a bounded
`release-check` path for API docs, contracts, integration tests and Compose
image builds. README documents the CI and release-readiness commands, with
load and resilience evidence intentionally left as manual long-running checks.

The final release gate now stops only this project's running Compose services
before Testcontainers integration tests, preserving containers, volumes and
application data while freeing enough Docker Desktop memory for PostgreSQL,
Kafka and MongoDB suites. Compose image validation defaults to the regular
Compose builder because Buildx Bake cannot transport the non-ASCII checkout
path in a macOS session header. Validation passed:
`bash -n scripts/project-validate.sh`,
`scripts/project-validate.sh ci-config`,
`scripts/project-validate.sh integration-test`,
`scripts/project-validate.sh compose-image-build`, and the complete
`scripts/project-validate.sh release-check`. Sprint 6 and the six-sprint
delivery queue are complete; no further task is queued.

ECOM-051 is complete. The root README now describes the current implemented
service responsibilities instead of future skeletons, lists prerequisites,
one-command startup, validation commands, direct OpenAPI/Swagger UI URLs, and
a concise authenticated demo entry point. New Git-reviewable documentation was
added for architecture, data model and demo operations:
`docs/architecture.md`, `docs/data-model.md` and `docs/demo-runbook.md`.
The architecture doc covers service/data ownership, synchronous calls,
Order and Payment outbox/CDC paths, Kafka consumers, observability and
security boundaries. The data-model doc records the Catalog, Order and Payment
PostgreSQL schemas plus MongoDB `audit_events` and
`notification_deliveries`. The demo runbook covers the authenticated happy
path, deterministic `400.00` payment failure path and expected Payment,
provider, audit, notification, connector, metric and trace evidence.

Operational documentation was consolidated through README and architecture
links for health, metrics, traces, connector status, DLT inspection,
integration, contract, E2E, load and resilience checks. `infra/README.md`,
`docs/developer-setup.md`, `docs/service-rbac.md` and
`docs/observability-runbook.md` were refreshed to remove stale setup or route
language and point at the current Compose/bootstrap/API behavior. `DESIGN.md`
now links to the detailed architecture, data-model and demo docs. Validation
passed: `scripts/project-validate.sh docs-check`. A scoped whitespace check
also passed for the edited documentation files:
`git diff --check -- README.md DESIGN.md infra/README.md docs/architecture.md docs/data-model.md docs/demo-runbook.md docs/observability-runbook.md docs/service-rbac.md docs/developer-setup.md docs/night-runner/task-queue.md STATE.md docs/night-runner/handoff.md`.
The runner initially marked the completed task blocked because a documentation
skill shown in the Codex log contained illustrative `git push` and AWS
placeholder text. `scripts/night-agent-safe-approve.sh` now distinguishes
executed Codex command records from read-only prompt, skill and prior dirty-diff
content, while still rejecting executed dangerous commands and secret-like log
values.
Next task at that point was ECOM-052; it is now complete.

ECOM-050 is complete. Catalog, Order, Payment and Audit Notification now publish
Springdoc OpenAPI JSON and Swagger UI for their externally useful HTTP APIs
while keeping actuator, RBAC probe and internal implementation details out of
the public API description. The root Maven parent pins compatible Springdoc
2.5.0, each service exposes `/v3/api-docs` and `/swagger-ui.html` directly on
its local service port, and service security explicitly permits only the docs
endpoints while preserving existing JWT/RBAC authorization for production API
routes.

The public API docs now describe bearer JWT security, role expectations,
request and response schemas, pageable endpoints, and Problem Details responses
for the Catalog browse/admin APIs, customer and operations Order APIs, new
safe read-only Payment lookup APIs, and new safe read-only Audit/Notification
query APIs. Payment exposes customer-scoped lookup by order and operations
lookup by payment/order without exposing provider/internal outbox details.
Audit Notification exposes customer-scoped notification delivery history and
operations audit-event lookup/page queries without exposing Kafka offsets or
other internal-only fields. `docs/api-documentation.md` documents the direct
local OpenAPI and Swagger UI URLs plus JWT role expectations, and README links
to it.

Focused tests cover OpenAPI document availability, key documented paths,
hidden internal probes, public docs security, unchanged endpoint authorization,
Payment query scoping, Audit/Notification query scoping and pagination
validation. Validation passed on Java 21:
`scripts/project-validate.sh api-docs-test`. During validation, earlier local
attempts exposed three integration issues that were fixed before the passing
run: Gateway tests no longer bind random sockets, Springdoc was pinned to a
Spring Boot 3.3-compatible version, and the new Payment query test now cleans
up after each test so the shared H2 test database does not leak state into
existing Payment application tests. Next task: ECOM-051.

ECOM-049 is complete. The repository now has bounded local restart/replay
resilience validation for US-28. `scripts/run-resilience-validation.sh` starts
or reuses the full Compose stack with the resource-bounded
`tests/resilience/docker-compose.resilience.yml` override, creates isolated
local Keycloak users, injects targeted fault windows, prints per-scenario
recovery timelines, verifies final consistency through Gateway/PostgreSQL/
MongoDB/mock-provider/Kafka checks, and restores any touched Payment, Audit or
Kafka service without deleting volumes or resetting databases. The smoke
profile runs a Payment service restart window and a Kafka interruption window;
the local/all profiles can also run the Audit Notification restart scenario,
and longer fault windows require explicit opt-in. The runner also publishes
malformed local-only records to exercise the configured Payment, Order and
Audit Notification DLTs, then replays captured `OrderCreated` and
`PaymentSucceeded` payloads to prove provider attempts, terminal payment
outbox rows, audit documents and completed notification deliveries remain
exactly once.

Documentation now covers the resilience smoke command, optional scenarios,
fault-window caps, recovery expectations, retained local evidence and failure
diagnostics in `docs/resilience-validation.md`, with links from
`docs/integration-tests.md` and `README.md`. Validation passed:
`bash -n scripts/run-resilience-validation.sh`, merged Compose configuration
for `docker-compose.yml` plus the resilience override, `git diff --check`, and
the required `scripts/project-validate.sh resilience-smoke` with Docker access.
The passing run was
`resilience-20261008163848-609699cce410`: Payment restart recovered to `PAID`
with one provider attempt and one terminal outbox row, reached exactly-once
audit/EMAIL/PUSH state, and stayed idempotent after duplicate replay; Kafka
interruption recovered after Kafka, clients and Debezium connectors came back,
then reached the same final consistency and duplicate replay checks. The DLT
probe advanced all four configured DLT offsets from 1 to 2. Earlier validation
attempts were useful harness feedback: the sandboxed run was blocked by Docker
socket permissions, the first approved run exposed that stopped Compose
containers require `docker compose ps -a -q`, and the second exposed that the
Apache Kafka image provides `kafka-get-offsets.sh` rather than the older
`GetOffsetShell` class. Both script issues were fixed before the passing run.
Next task: ECOM-050.

ECOM-048 is complete. The repository now has a bounded, versioned Catalog
Gateway load validation harness for US-28. `scripts/run-load-validation.sh`
drives a smoke-safe Catalog browse/detail scenario through Gateway with
configurable base URL, virtual users, duration and thresholds, and uses the
load-specific Compose override in `tests/load/docker-compose.load.yml` to keep
the Catalog/Gateway slice within local Docker Desktop resources. The smoke
profile is capped at 2 virtual users for 8 seconds by default, records
throughput, error rate and latency percentiles, and requires explicit opt-in
for the higher profile while still enforcing hard caps.

The load runner creates one local Catalog fixture through Gateway, proves cold
versus warm product-detail cache behavior with existing
`ecommerce_cache_requests_total` hit/miss counters, and records a best-effort
PostgreSQL `pg_stat_database` tuple-read observation for the Catalog database.
The passing run showed cold cache misses, warm cache hits, zero warm misses and
zero warm database tuple-read delta. A small Java 21 virtual-thread runtime
probe under `tests/load/VirtualThreadRuntimeProbe.java` validates bounded
virtual-thread execution without making production capacity claims.
`docs/load-validation.md`, `docs/integration-tests.md` and `README.md` document
the smoke command, thresholds, opt-in higher profile and result interpretation.

Validation passed: `bash -n scripts/run-load-validation.sh`, Python compile
syntax check for `tests/load/catalog_browse_detail_load.py`, Java 21 compile
and runtime probe check for `tests/load/VirtualThreadRuntimeProbe.java`,
`git diff --check`, and the required
`scripts/project-validate.sh load-smoke` with Docker access. During validation,
the initial sandboxed run was blocked by Docker socket permissions, the first
Compose startup path unnecessarily depended on the unrelated mock provider, and
the first load threshold was too tight for a just-started local stack. The
runner now starts only the Catalog/Gateway slice when needed and uses a
developer-machine smoke p95 threshold that the final run passed with zero
errors. A post-task safety check then rejected an `rm -rf` temporary-directory
cleanup; it was replaced with deletion limited to generated `.class` files and
`rmdir`. The recovery smoke run passed with 548/548 requests, zero errors,
59.2 ms p95 latency, 68.23 requests/second and 16/16 virtual-thread probe
tasks. Next task: ECOM-049.

ECOM-047 is complete. The authenticated
purchase-flow E2E harness now runs through Gateway with local Keycloak-issued
tokens, creates deterministic Catalog fixtures through authorized APIs, places
approved and declined customer orders, polls with deadlines through
CDC/Kafka-driven terminal states, checks Payment PostgreSQL rows, Audit MongoDB
records, mock email/push delivery evidence and duplicate replay safety, and
prints bounded diagnostics on failure. The runner now performs its own Compose
readiness checks, restarts services left in `Created` after transient Compose
dependency failures, reconciles the two Debezium outbox connectors for E2E
execution with `snapshot.mode=no_data`, avoids the incompatible Debezium
outbox timestamp-field mapping at runtime, ensures the local Keycloak test
client exposes realm roles and gateway audience claims, and uses a focused E2E
Compose override to keep Kafka, Debezium, Keycloak, MongoDB and five Spring JVMs
stable within a small Docker Desktop memory budget. Payment now serializes its
provider request explicitly with the configured `ObjectMapper`, preventing the
runtime client from sending `{}`; the local provider ledger records safe field
shape diagnostics and accepts camelCase and snake_case request keys.

Validation passed: `bash -n scripts/run-e2e-tests.sh`, the two Python mock
provider regression tests, the focused
`RestClientMockPaymentProviderClientTest` suite on Java 21, merged Compose
configuration validation, and
`E2E_STACK_BUILD_MODE=never scripts/project-validate.sh e2e-test` with Docker.
The full E2E run proved approved `PAID` and declined `PAYMENT_FAILED` flows,
one provider attempt per order, one audit plus EMAIL/PUSH delivery per event,
and duplicate replay without additional charges or notifications. Docker
registry metadata was unavailable during the recovery session, so current
local images were refreshed from the tested source artifacts instead of
pulling base layers; no volumes or application data were deleted. Next task:
ECOM-048.

ECOM-046 is complete. The repository now has an opt-in `contract-tests` Maven
profile, selected by `scripts/project-validate.sh contract-test`, that adds the
new `tests/event-contracts` module and runs focused `*ContractTest` classes
without Docker, Testcontainers or the full Compose stack. Order, Payment and
Audit Notification attach profile-only `plain` classifier jars so the contract
module can compile against service-owned classes while normal executable
Spring Boot packaging remains unchanged. The new `EventEnvelopeContractTest`
generates representative `OrderCreated`, `PaymentSucceeded` and
`PaymentFailed` JSON through the actual Order and Payment outbox factories and
feeds that output into the real Payment, Order and Audit Notification consumer
parsers. Coverage verifies version-1 envelope fields, aggregate/order
consistency, money/currency representation, trace/correlation metadata,
required field rejection and unsupported version rejection without adding a
shared business-domain module or loosening consumer validation. README and
`docs/integration-tests.md` now document the local contract-test command.
Validation passed: `scripts/project-validate.sh contract-test` on Java 21.
During validation, an initial compile exposed the executable Boot jar
classpath issue for cross-service tests, and the first test run showed that
Order emits JSON money as numeric values with fewer trailing zeros while
consumers normalize to four decimals; both were fixed in the test/profile
wiring. Next task: ECOM-047.

ECOM-045 is complete. Docker Compose now includes a default
`debezium-connector-bootstrap` service backed by a small local Alpine image and
the bounded `scripts/bootstrap-debezium-connectors.sh` helper. The bootstrap
waits for PostgreSQL, Kafka, Connect, Order and Payment readiness, renders the
connector environment placeholders to concrete local values, and idempotently
PUTs both `ecommerce-order-outbox-v1` and `ecommerce-payment-outbox-v1` before
waiting for their tasks to reach `RUNNING`. Compose health checks now use real
readiness probes for MongoDB, Keycloak and the Spring services, plus Prometheus
readiness, while Jaeger remains verified by the smoke script because its image
does not expose a portable shell health check. The new
`scripts/verify-full-stack.sh` starts the stack in detached mode, builds local
images only when they are missing unless `--rebuild` is requested, waits with a
bounded deadline, verifies Keycloak, the mock provider, Gateway, all services,
both Debezium connectors, Prometheus and Jaeger, and never removes volumes or
application data. Local setup docs and `.env.example` now document the exact
one-command and smoke workflow. Validation passed:
`scripts/project-validate.sh full-stack-config` and
`scripts/project-validate.sh full-stack-smoke` with Docker access. During
validation, an initial sandboxed smoke attempt was blocked by Docker socket
permissions, and the first full smoke exposed unresolved connector placeholders
and a too-short Debezium REST timeout; both were fixed before the passing run.
Next task: ECOM-046.

ECOM-044 is complete. The repository now has reproducible local container
builds for Gateway, Catalog, Order, Payment and Audit Notification through
service-local multi-stage Dockerfiles that build with Maven on Java 21 and run
the packaged Spring Boot jars on Java 21 JRE images as a non-root user. A root
`.dockerignore` now keeps local build output, VCS metadata, local env files,
agent logs, source package documents and other non-image inputs out of Docker
build contexts. Docker Compose now defines all five application services plus a
deterministic `mock-payment-provider` service with internal DNS wiring,
environment placeholders, health checks, explicit readiness dependencies and
configurable host ports. The mock provider supports approved, declined,
timeout and 5xx local scenarios without any real payment integration. Local
environment placeholders and setup docs now describe the container-internal DNS
values, host port overrides and mock-provider scenario amounts. Validation
passed: `scripts/project-validate.sh full-stack-config` and `git diff --check`.
Next task: ECOM-045.

ECOM-043 is complete. Audit Notification Service now has a dedicated MongoDB
Testcontainers suite for its Mongo-owned audit and notification persistence
boundaries. The new `AuditNotificationMongoIT` starts an isolated MongoDB 7
database through a dynamic replica-set URL and verifies real index creation for
`audit_events` and `notification_deliveries`, including the unique audit event
ID replay index and the unique delivery event/channel ledger index. The suite
persists OrderCreated and PaymentSucceeded audit documents through the real
Spring Data Mongo repositories, proves duplicate audit event handling converges
to one document, verifies searchable metadata query methods, and inspects raw
BSON to confirm the complete structured nested payload survives a real Mongo
round trip. Notification ledger coverage now proves real Mongo uniqueness,
replay recovery for a missing channel, failed and interrupted channel records
being returned to `PENDING`, and completed email/push `SENT` records not being
duplicated. `docs/integration-tests.md` now records the dedicated Mongo
coverage. Validation passed: `scripts/project-validate.sh audit-notification-test`
on Java 21 and, after an initial sandboxed Docker-socket failure,
`scripts/project-validate.sh integration-test` with Docker access on Java 21.
Next task: ECOM-044.

ECOM-042 is complete. Payment, Order and Audit Notification now have real Kafka
Testcontainers coverage through Spring Kafka listener containers instead of
direct listener-method calls. Payment verifies the `OrderCreated` consumer with
isolated Kafka topics/groups/DLTs and PostgreSQL persistence, covering a valid
event, duplicate replay idempotency, persisted payment/outbox/processed-event
state and bounded malformed-message DLT routing. Order verifies the
payment-result consumer with isolated Kafka and PostgreSQL, covering order
status/history updates, processed-event idempotency and poison-message DLT
routing. Audit Notification verifies both order-created and payment-result
audit consumers with isolated Kafka and MongoDB, covering audit document
persistence, notification delivery ledger records, duplicate replay behavior
and separate DLT routing for malformed order/payment payloads. Service-local
test dependencies now include the needed Testcontainers Kafka support and
Audit MongoDB support, and `docs/integration-tests.md` documents the Kafka
coverage and direct service-slice command. Validation passed:
`scripts/project-validate.sh integration-test` on Java 21 with Docker access.
An initial sandboxed validation attempt was blocked by Docker socket
permissions before the approved Docker-access run passed. Next task: ECOM-043.

ECOM-041 is complete. Catalog Service now has Redis Testcontainers coverage for
the existing product-detail cache and Redisson miss-lock path. The new
`CatalogRedisCacheIT` suite starts an isolated Redis 7 container with dynamic
`spring.data.redis.*` endpoints, keeps the default unit loop untouched, and
uses the existing Catalog cache configuration instead of localhost or fixed
ports. It verifies product-detail JSON serialization without Java type metadata,
Redis TTL writes, cache-aside miss population, cached reads after an
authoritative database-only change, admin product update invalidation and
repopulation, plus a bounded two-request concurrent miss that proves the
Redisson product lock is held and only one protected database load occurs.
Catalog now declares the core Testcontainers test dependency directly for the
Redis `GenericContainer`, and `docs/integration-tests.md` documents the Redis
coverage alongside the PostgreSQL suites. This resume made no additional
source changes: the sandboxed validation attempt was blocked by Docker socket
permissions, then the approved Docker-access
`scripts/project-validate.sh integration-test` run passed on Java 21 with the
Catalog Redis suite reporting 3/3 tests. `git diff --check` also passed. Next
task: ECOM-042.

ECOM-040 is complete. The root Maven parent now pins
Testcontainers 1.20.4 through the Testcontainers BOM, adds an opt-in
`integration-tests` profile that skips Surefire unit execution and runs
Failsafe `*IT` suites, and Catalog, Order and Payment now have service-local
PostgreSQL Testcontainers dependencies and `*PostgreSqlIT` suites. The suites
start isolated PostgreSQL databases with dynamic Testcontainers endpoints,
wire datasource properties through `@DynamicPropertySource`, and cover Flyway
migrations, owned schema tables, repository persistence, database constraints
and representative outbox/idempotency JSON persistence flows without touching
developer Compose volumes. Documentation now records the opt-in commands and
Docker prerequisite/failure behavior in `docs/integration-tests.md`, with links
from the README and developer setup guide. After Docker Desktop was started,
`scripts/project-validate.sh integration-test` passed on Java 21 against three
isolated PostgreSQL 16 containers for Catalog, Order and Payment. The suites
confirmed Flyway migrations, owned schema tables, repository persistence,
database constraints, outbox JSON and idempotent processed-event behavior.
ECOM-041 followed this task.

ECOM-039 is complete. Audit Notification Service now exposes a bounded
Micrometer gauge, `ecommerce.kafka.dead.letter.topic.depth`, for the fixed
configured Order, Payment and Audit Notification dead-letter topics. The gauge
configuration is environment-driven, uses only bounded `service` and `topic`
labels, sums retained records across topic partitions through Kafka Admin
offset reads, caches refreshes, and falls back to zero depth with a warning if
Kafka is unavailable or a configured DLT topic cannot be inspected. Prometheus
now loads `infra/observability/alert-rules.yml` through `rule_files`, and
Docker Compose mounts that local rule file into the Prometheus container. The
rules cover service-down, sustained HTTP 5xx, terminal payment failures,
notification delivery failures, Kafka consumer failures/rejections and
positive DLT retained depth. The observability runbook now documents the local
DLT depth query, alert/rule inspection commands and a bounded one-record
smoke simulation against a temporary local DLT topic without deleting Kafka or
application data. Focused tests cover DLT-depth property binding and bounds,
gauge refresh caching, unavailable-broker behavior and expected Prometheus
rule expressions. Validation passed:
`scripts/project-validate.sh audit-notification-test` and
`scripts/project-validate.sh observability-config` on Java 21. Next task:
none queued.

ECOM-038 is complete. Catalog product-detail cache lookups are now wrapped with
bounded Micrometer hit/miss counters for the Redis-backed and no-op cache
paths. Order, Payment and Audit Notification Kafka consumers now record
low-cardinality consumer outcome counters, plus retry and dead-letter
publication attempt counters through their existing Spring Kafka error-handler
paths. Payment Service records terminal payment result outcomes so failed
terminal payments are directly queryable without tagging payment, order,
customer, trace, correlation or event identifiers. Audit Notification Service
records audit persistence outcomes and mock email/push delivery outcomes by
service, topic, event type, channel and outcome only. Focused SimpleMeterRegistry
tests cover cache hit/miss, consumer success/rejection/failure paths,
retry/DLT counters, terminal payment failure metrics, audit persistence metrics
and notification delivery metrics. The observability runbook now lists the
custom meter names and representative PromQL for HTTP errors, cache hit ratio,
consumer failures, retries, DLT publication attempts, payment failures, audit
persistence failures and notification failures. Validation passed:
`scripts/project-validate.sh observability-test` on Java 21. Next task:
ECOM-039.

ECOM-037 is complete. End-to-end correlation and trace propagation now keeps
the envelope contract as the cross-service source of truth while adding
service-local Kafka processing observations and scoped logging context. Gateway
continues to preserve an incoming `X-Correlation-Id` or generate one only when
absent, and Order now has focused filter coverage proving the same
correlation ID is available in MDC during order handling and cleared
afterward. Order creation still writes MDC `traceId` and `correlationId` into
the `OrderCreated` outbox envelope. Order, Payment and Audit Kafka listener
configuration now enables Spring Kafka observation, and valid consumer
handling creates low-cardinality processing observations while temporarily
binding envelope `traceId`, `correlationId`, `eventId`, `eventType`,
`eventVersion` and `aggregateId` into MDC for the active handler scope only.
Payment result handling in Order, `OrderCreated` handling in Payment, and both
Audit consumer paths have focused tests proving the MDC scope is restored
safely. Audit notification dispatch now binds each delivery record's own event
metadata while sending pending email or push deliveries so batched notification
logs are not polluted by the triggering listener's context. Email, push and
mock-adapter logs now include trace and correlation IDs plus event/order/payment
metadata, while no high-cardinality metric tags were added. The observability
runbook documents a concrete local inspection flow across Gateway, Order
outbox, Kafka topics, Payment, Audit Mongo documents, notification deliveries,
logs and Jaeger. Validation passed:
`scripts/project-validate.sh observability-test` on Java 21. Focused Gateway
unit validation also passed:
`mvn -q -pl services/gateway-service -Dtest=CorrelationIdWebFilterTest test`
with Java 21 selected through `/usr/libexec/java_home`. Next task: ECOM-038.

ECOM-036 is complete. Audit Notification Service now dispatches pending `PUSH`
notification deliveries through the existing Audit-owned notification port
boundary with a local mock adapter only; no device tokens, provider credentials
or production push SDKs were added. Push payloads are deterministic for V1
`OrderCreated`, `PaymentSucceeded` and `PaymentFailed` deliveries and are built
from safe delivery-ledger metadata: customer reference, order reference,
optional payment reference, event ID/type/version, occurrence time, trace ID
and correlation ID. Valid Kafka audit handling now persists the audit document,
routes delivery records and then dispatches both pending email and push work.
The push dispatcher moves records through `PENDING -> IN_PROGRESS -> SENT` on
success or `PENDING -> IN_PROGRESS -> FAILED` on adapter/payload failures,
caps retry attempts through bounded push settings, and marks exhausted
deliveries failed without sending again. Replayed business events still recover
failed or interrupted push records through the existing routing path, while
completed `SENT` push records are not requeued or sent a second time. Safe logs
include delivery/event/order/payment/correlation metadata only. Focused tests
cover push payload construction, the mock adapter, success/failure/exhausted
dispatch, disabled dispatch and duplicate completed-delivery behavior.
Validation passed: `scripts/project-validate.sh audit-notification-test` on
Java 21. Next task: ECOM-037.

ECOM-035 is complete. Audit Notification Service now dispatches pending
`EMAIL` notification deliveries through an Audit-owned email port backed by a
local mock adapter only; no SMTP credentials, provider endpoints, real customer
email addresses or production SDKs were added. Email payloads are deterministic
for V1 `OrderCreated`, `PaymentSucceeded` and `PaymentFailed` deliveries and
are built only from safe delivery-ledger metadata: customer reference, order
reference, optional payment reference, event ID/type/version, occurrence time,
trace ID and correlation ID. Valid Kafka audit handling now persists the audit
document, routes delivery records and then dispatches pending email work. The
dispatcher moves records through `PENDING -> IN_PROGRESS -> SENT` on success
or `PENDING -> IN_PROGRESS -> FAILED` on adapter/payload failures, caps retry
attempts through bounded email settings, and marks exhausted deliveries failed
without sending again. Replayed business events still recover failed or
interrupted email records through the existing routing path, while completed
`SENT` email records are not requeued or sent a second time. Safe logs include
delivery/event/order/payment/correlation metadata only. Focused tests cover
payload construction, the mock adapter, success/failure/exhausted dispatch,
disabled dispatch and duplicate completed-delivery behavior. Validation passed:
`scripts/project-validate.sh audit-notification-test` on Java 21. Next task:
ECOM-036.

ECOM-034 is complete. Audit Notification Service now owns a replay-safe
MongoDB notification delivery ledger in `notification_deliveries`. Delivery
documents capture `EMAIL` and `PUSH` channel records with event ID/type/version,
order/customer/payment references, delivery status
(`PENDING`, `IN_PROGRESS`, `SENT`, `FAILED`), attempt count, last-attempt time,
safe failure detail, event/timeline timestamps, trace ID and correlation ID.
The ledger declares a compound unique event ID plus channel index so each
event/channel has one delivery record while still supporting focused lookup by
event, channel, order, customer, status and correlation ID. A new routing
service defines the relevant customer-notification events as V1 `OrderCreated`,
`PaymentSucceeded` and `PaymentFailed`; unsupported event types or versions are
ignored without delivery side effects. The order-created and payment-result
Kafka audit listeners now persist the audit event first and then route
notification ledger records, even when audit persistence returns a duplicate
replay skip. This keeps the audit-event replay boundary independent from
delivery bookkeeping, allowing Kafka retry/replay to recover a missing channel
record without duplicating an existing completed `SENT` delivery. Existing
`FAILED` or `IN_PROGRESS` records are deterministically returned to `PENDING`
for later adapter retry, while no real email, push, SMTP, device-token or
provider integration was added. Focused document, index, routing, repository
metadata and listener tests cover relevant, irrelevant, duplicate, missing
channel and retry paths. Validation passed:
`scripts/project-validate.sh audit-notification-test` on Java 21. Next task:
ECOM-035.

ECOM-033 is complete. Audit Notification Service now consumes
`PaymentSucceeded` and `PaymentFailed` version-1 envelopes from
`ecommerce.payment.events` through an Audit-owned Spring Kafka listener with
its own environment-driven topic, consumer group, enablement, bounded
retry/backoff and payment-result DLT settings. Listener container factories now
bind OrderCreated and payment-result consumers to their respective error
handlers so malformed parser failures remain non-retryable while transient
Mongo/Kafka failures stay retryable. The new Audit-local payment-result parser
validates event type/version, event and aggregate identifiers, aggregate/order
consistency, occurred time, trace/correlation IDs, payment/order/customer
identifiers, money/currency, provider attempt metadata and terminal payment
status matching before any persistence side effect. Valid payment result
events are persisted through the existing Mongo audit application flow with the
complete structured payload plus searchable event, aggregate/order/customer
and payment metadata; payment ID is now indexed for focused lookups. Duplicate
or concurrent duplicate deliveries still converge on one audit document via
the unique event ID boundary, and malformed events create no partial audit
data. Email and push notification delivery remain intentionally out of scope.
Focused parser, listener, persistence, duplicate, transient-failure and Kafka
configuration tests were added. Validation passed:
`scripts/project-validate.sh audit-notification-test` on Java 21. Next task:
ECOM-034.

ECOM-032 is complete. Audit Notification Service now has an Audit-owned Spring
Kafka consumer for version-1 `OrderCreated` envelopes from
`ecommerce.order.events`, with environment-driven topic, consumer group,
enablement, bounded retry/backoff and service-specific DLT settings. The
consumer uses an Audit-local parser and validation model without importing
Order Service JPA or application classes, rejects malformed or unsupported
envelopes before persistence, and classifies those parser failures as
non-retryable for Kafka error handling. Valid events are persisted through the
Audit-owned Mongo audit document before listener completion, including the
complete structured payload, event metadata, order/customer identifiers,
occurred and received times, trace/correlation IDs and Kafka source position.
Duplicate or concurrent duplicate deliveries converge on one audit document by
treating the unique event ID as the replay boundary; Mongo duplicate-key races
return a duplicate skip while transient Mongo failures remain retryable. Email
and push notification delivery remain intentionally out of scope. Focused
parser, listener, persistence, duplicate-delivery and Kafka configuration tests
were added. Validation passed:
`scripts/project-validate.sh audit-notification-test` on Java 21. Next task:
ECOM-033.

ECOM-031 is complete. Audit Notification Service now has Spring Data MongoDB
runtime wiring against the local `audit` database through environment-driven
placeholders. The new Audit-owned `audit_events` document stores the complete
structured versioned event payload plus event ID, type/version, aggregate ID,
order/customer/payment identifiers, occurred and received times, trace and
correlation IDs, and source topic/partition/offset without importing Order or
Payment domain classes. Mongo index auto-creation is enabled by default for
the service, with event ID uniqueness for the audit replay boundary plus
focused search indexes for event type,
aggregate/order/customer ID, correlation ID and occurred time. The repository
exposes event-ID replay lookups and focused query methods for future consumers.
Tests cover document validation, structured payload defensive copying, index
annotations, Mongo property binding and repository configuration metadata
without requiring a manually running MongoDB instance. Validation passed:
`scripts/project-validate.sh audit-notification-test` on Java 21. Next task:
ECOM-032.

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
5. Sprint 5 is complete through ECOM-039, covering Audit-owned Mongo audit
   persistence, idempotent Order/Payment Kafka audit consumers,
   replay-safe notification routing/delivery ledger creation, local mock
   email and push delivery, and end-to-end REST/Kafka trace and correlation
   propagation, bounded operational metrics, local Prometheus alert rules and
   fixed-topic DLT depth signals.
6. Sprint 6 is complete through ECOM-052, covering the opt-in Testcontainers
   profile, PostgreSQL/Redis/Kafka/MongoDB integration coverage, Java 21
   application container images, full Compose application service wiring,
   deterministic local mock payment-provider container, Debezium connector
   bootstrap, contract and authenticated E2E coverage, bounded load and
   resilience validation, OpenAPI publication and architecture/data/demo
   documentation, CI automation and a passing local release-readiness gate.

Sprint 5 execution order so far:

- MongoDB audit event persistence and searchable event model. (complete)
- Idempotent `OrderCreated` audit consumer. (complete)
- Idempotent payment-result audit consumer. (complete)
- Replay-safe notification routing and delivery ledger. (complete)
- Mock email notification delivery. (complete)
- Mock push notification delivery. (complete)
- End-to-end correlation and trace propagation. (complete)
- Bounded low-cardinality service metrics. (complete)
- Prometheus alert rules and Kafka DLT depth signals. (complete)

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

Still excluded from completed work:

- Real email, SMTP, push device-provider integrations or credentials.

## Environment Notes

- Maven is available on this machine.
- Docker is available on this machine.
- The default shell Java still appears to be Java 17, but
  `/usr/libexec/java_home -v 21` locates OpenJDK 21.0.11. The project target
  remains Java 21.
- The runner does not require a Git repository, but Git is recommended before
  long overnight work because it improves rollback and changed-file tracking.

## Latest Validation

ECOM-052 validation status:

```bash
bash -n scripts/project-validate.sh
scripts/project-validate.sh ci-config
scripts/project-validate.sh integration-test
scripts/project-validate.sh compose-image-build
scripts/project-validate.sh release-check
```

All commands passed. The final release check stops project Compose services
before Testcontainers to fit the integration stack within local Docker Desktop
memory without deleting containers, volumes or application data. Compose image
builds use `COMPOSE_BAKE=false` by default to avoid a Buildx session-header
failure caused by the non-ASCII checkout path on macOS.

ECOM-051 validation passed:

```bash
scripts/project-validate.sh docs-check
git diff --check -- README.md DESIGN.md infra/README.md docs/architecture.md docs/data-model.md docs/demo-runbook.md docs/observability-runbook.md docs/service-rbac.md docs/developer-setup.md docs/night-runner/task-queue.md STATE.md docs/night-runner/handoff.md
```

ECOM-044 validation passed:

```bash
scripts/project-validate.sh full-stack-config
git diff --check
```

ECOM-041 validation passed:

```bash
scripts/project-validate.sh integration-test
git diff --check
```

The required integration validation passed on Java 21 with Docker/Testcontainers
against the existing PostgreSQL suites and the new Catalog Redis suite. A first
sandboxed attempt failed only because Docker socket access was blocked; the
approved Docker-access run completed successfully.

ECOM-039 validation passed:

```bash
scripts/project-validate.sh audit-notification-test
scripts/project-validate.sh observability-config
```

ECOM-037 validation passed:

```bash
scripts/project-validate.sh observability-test
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -q -pl services/gateway-service -Dtest=CorrelationIdWebFilterTest test
```

Running the focused Gateway test without overriding `JAVA_HOME` still uses the
default shell Java 17 and fails because the test classes were compiled for Java
21. The Java 21 rerun passed.

ECOM-036 validation passed:

```bash
scripts/project-validate.sh audit-notification-test
```

ECOM-035 validation passed:

```bash
scripts/project-validate.sh audit-notification-test
```

ECOM-034 validation passed:

```bash
scripts/project-validate.sh audit-notification-test
```

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

No queued implementation task remains. Review and commit the completed Sprint
6 changes; the night runner should report no pending `sprint6` tasks.
