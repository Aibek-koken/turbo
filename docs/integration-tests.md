# Integration Tests

The default unit-test loop stays on Surefire and does not run `*IT` classes:

```bash
mvn test
```

Real infrastructure tests are opt-in through the Maven `integration-tests`
profile and the project validation wrapper:

```bash
scripts/project-validate.sh integration-test
```

The equivalent direct Maven command is:

```bash
mvn -Pintegration-tests verify
```

The integration profile is Failsafe-only: it skips Surefire unit execution and
runs `*IT` classes. Run `mvn test` separately when you want the normal unit
suite.

To run one service slice while still building required upstream modules:

```bash
mvn -Pintegration-tests -pl services/catalog-service -am verify
mvn -Pintegration-tests -pl services/order-service -am verify
mvn -Pintegration-tests -pl services/payment-service -am verify
mvn -Pintegration-tests -pl services/audit-notification-service -am verify
```

## Contract Tests

Producer-consumer event contract tests are opt-in through the Maven
`contract-tests` profile and the project validation wrapper:

```bash
scripts/project-validate.sh contract-test
```

The equivalent direct Maven command is:

```bash
mvn -Pcontract-tests verify
```

The contract suite is deterministic and does not require Docker, Testcontainers
or the full Compose stack. It adds the `tests/event-contracts` module only when
the profile is active and runs `*ContractTest` classes. The suite produces
representative `OrderCreated`, `PaymentSucceeded` and `PaymentFailed` JSON
through the actual Order and Payment outbox factories, then parses that output
through the real Payment, Order and Audit Notification consumer parsers.

Coverage verifies version-1 envelope fields, aggregate/order consistency,
money and currency representation, trace and correlation metadata, required
payload fields and explicit rejection of unsupported event versions. The tests
depend on service artifacts only for compatibility checks; they do not add a
shared business-domain module or loosen service-local consumer validation.

## End-To-End Tests

Authenticated purchase-flow E2E tests run against the full local Compose stack:

```bash
scripts/project-validate.sh e2e-test
```

The validation wrapper runs `scripts/run-e2e-tests.sh`. The runner starts or
reuses the Compose stack, waits for the full set of service, Keycloak, Debezium,
Prometheus and Jaeger readiness checks, reconciles the outbox connectors for
the E2E run, creates isolated local-only Keycloak users through the Keycloak
admin API, obtains JWTs from the imported `ecommerce` realm, and sends Catalog
and Order REST traffic only through Gateway.

Coverage includes:

- authorized Catalog fixture creation through `/api/catalog/admin/**`
- customer browse/detail and order creation through Gateway
- a happy path that reaches `PAID`
- a deterministic mock-provider decline that reaches `PAYMENT_FAILED`
- Payment PostgreSQL state, Audit MongoDB records and `SENT` email/push delivery
  ledger records for each business event
- duplicate Kafka replay of the captured `OrderCreated` and payment-result
  payloads, with provider attempts and notification deliveries remaining
  exactly once

Useful direct invocations:

```bash
scripts/run-e2e-tests.sh --skip-up
scripts/run-e2e-tests.sh --rebuild
scripts/run-e2e-tests.sh --cleanup
```

The E2E runner uses bounded polling and prints Compose, provider, PostgreSQL,
MongoDB and service-log diagnostics on failure. It never stores generated
tokens or runtime user passwords in the repository. By default it deletes only
the isolated Keycloak users it created and leaves Compose volumes, catalog
fixtures, orders, payments, audit documents and unrelated local data intact.
By default, it rebuilds local application and mock-provider images before the
run so the black-box flow does not accidentally exercise stale service images;
set `E2E_STACK_BUILD_MODE=missing` or pass `--no-build` only when that tradeoff
is intentional. The harness also merges
`tests/e2e/docker-compose.e2e.yml` by default to bound JVM heaps and MongoDB's
WiredTiger cache on developer Docker Desktop installations. Override the path
with `E2E_COMPOSE_OVERRIDE_FILE` when a different local resource profile is
required.

## Load Validation

Bounded load validation runs against the local Compose stack through Gateway:

```bash
scripts/project-validate.sh load-smoke
```

The validation wrapper runs `scripts/run-load-validation.sh --smoke`, which
creates one local Catalog fixture, checks cold/warm product-detail cache
behavior through existing `ecommerce_cache_requests_total` metrics, records a
best-effort PostgreSQL tuple-read observation for the `catalog` database, then
runs the versioned Catalog browse/detail load scenario. Smoke thresholds record
p95 latency, throughput and error rate for developer-machine and CI evidence
only; they are not production capacity claims.

For full options and the explicit higher-load opt-in, see
[load-validation.md](load-validation.md).

## Resilience Validation

Bounded restart and replay resilience validation runs against the local Compose
stack through Gateway:

```bash
scripts/project-validate.sh resilience-smoke
```

The validation wrapper runs `scripts/run-resilience-validation.sh --smoke`.
The smoke profile creates isolated local Keycloak users, injects a short
payment-service restart window and a short Kafka interruption around approved
purchase flows, checks recovery to consistent order/payment/audit/notification
state, replays captured business events to prove idempotency, and publishes
malformed local-only records to verify the configured dead-letter topics.

For prerequisites, recovery expectations, troubleshooting output and the
optional Audit Notification restart or extended-fault profiles, see
[resilience-validation.md](resilience-validation.md).

## Docker Requirement

The Testcontainers integration tests require a working local Docker engine
because they start ephemeral PostgreSQL, Redis, Kafka and MongoDB containers
with dynamic ports. They do not use the developer `docker-compose.yml`
PostgreSQL, Redis, Kafka or MongoDB services, fixed host ports, shared business
databases, shared Redis databases, shared Kafka topics, shared Mongo databases
or checked-in credentials.

The full E2E runner also requires Docker, but intentionally uses the developer
Compose stack because it validates the real Gateway, Keycloak, Debezium, Kafka,
PostgreSQL, MongoDB and mock-provider wiring together.

The load-smoke runner also uses the developer Compose stack plus
`tests/load/docker-compose.load.yml` for bounded local JVM heaps. It starts the
Catalog/Gateway slice with existing images when needed, but it does not rebuild
images, delete volumes or reset databases. The resilience-smoke runner uses the
developer Compose stack plus `tests/resilience/docker-compose.resilience.yml`
for bounded local JVM heaps and temporarily stops only this project's targeted
Payment, Audit or Kafka service according to the selected scenarios.

If Docker is stopped, unavailable or cannot pull a required infrastructure
image, the integration phase fails before the affected `*IT` suite can connect.
Start Docker and rerun the same command. Routine validation must not use
`docker compose down -v`, remove Docker volumes or delete unrelated developer
containers.

## Current Coverage

ECOM-040 adds PostgreSQL Testcontainers coverage for the Catalog, Order and
Payment service-owned schemas. Each service starts its own isolated PostgreSQL
database and wires Spring datasource properties with `@DynamicPropertySource`.
The suites verify Flyway migrations, repository persistence, selected database
constraints and representative JSON/outbox or idempotency persistence flows.

ECOM-041 adds Catalog Redis Testcontainers coverage. The Catalog suite starts an
isolated Redis container with dynamic `spring.data.redis.*` properties and the
existing cache configuration, then verifies product-detail JSON serialization,
cache-aside population and cached reads, admin-write invalidation, and a bounded
two-request Redisson miss-lock path that performs one protected database load.

ECOM-042 adds Kafka Testcontainers coverage for the Payment, Order and Audit
Notification service consumers. Payment and Order pair isolated Kafka topics,
consumer groups and dead-letter topics with PostgreSQL containers to verify
valid deliveries, duplicate replay idempotency, persisted outcomes and bounded
dead-letter routing for malformed poison messages through the real listener
containers. Audit Notification pairs isolated Kafka with MongoDB for the
listener persistence path and verifies order/payment audit documents,
notification delivery ledger records, duplicate replay behavior and DLT routing.
All Kafka broker endpoints, topics and groups are test-scoped, and the suites
poll with deadlines rather than fixed sleeps.

ECOM-043 adds dedicated Audit Notification MongoDB Testcontainers coverage. The
Mongo suite starts an isolated database with a dynamic replica-set URL and
verifies real audit and notification indexes, unique audit event replay
boundaries, unique delivery event/channel ledger records, searchable audit
metadata, full structured BSON payload round trips, duplicate event handling,
and notification replay recovery for missing, failed and interrupted channel
deliveries without duplicating completed email or push records.

ECOM-046 adds producer-consumer event contract coverage for `OrderCreated`,
`PaymentSucceeded` and `PaymentFailed` version-1 JSON envelopes. The tests use
actual producer output as consumer input across the Payment, Order and Audit
Notification boundaries and reject unsupported event versions before full-stack
E2E execution.

ECOM-047 adds authenticated full-stack E2E coverage for the happy purchase path,
the deterministic payment-decline path and duplicate/replay safety across
Payment, Order, Audit Notification and the local mock provider.

ECOM-048 adds a bounded Catalog Gateway load-smoke harness with p95 latency,
throughput and error-rate thresholds, cache hit/miss and PostgreSQL
observation evidence for cold versus warm product-detail reads, and a Java 21
virtual-thread runtime probe.

ECOM-049 adds bounded restart/replay resilience validation for Payment service
restart, optional Audit Notification restart and Kafka interruption windows,
plus duplicate replay and malformed-message DLT evidence without deleting
developer Compose volumes.
