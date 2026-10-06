# Sprint 6 Context Pack

Sprint 6 goal: prove the completed system against real infrastructure, make the
entire local platform reproducible with one Compose command, add bounded
load/recovery evidence, and finish API, architecture, runbook and CI release
documentation.

Stories covered:

- US-26 Integration Tests with Testcontainers
- US-27 Contract & Full End-to-End Tests
- US-28 Load & Resilience Validation
- US-29 One-Command Full Environment
- US-30 API, Architecture Documentation & CI Release

## Existing Baseline

- Java 21 / Spring Boot 3.3.5 multi-module Maven monorepo with Gateway,
  Catalog, Order, Payment and Audit Notification services.
- Catalog owns PostgreSQL and Redis cache-aside/Redisson locking.
- Order owns PostgreSQL, a transactional outbox and Debezium publication to
  `ecommerce.order.events`.
- Payment consumes `OrderCreated` idempotently, calls a local HTTP provider
  contract, owns PostgreSQL/outbox and publishes terminal results to
  `ecommerce.payment.events` through Debezium.
- Order consumes payment results idempotently. Audit Notification consumes both
  topics, stores MongoDB audit/delivery records and uses replay-safe mock email
  and push adapters.
- Retry/backoff/DLT handling, correlation/tracing, low-cardinality metrics,
  Prometheus rules and fixed-topic DLT depth signals are already implemented.
- Current Docker Compose starts infrastructure only. Application images, a
  runnable mock payment provider, connector bootstrap and full-stack readiness
  orchestration are not yet present.
- Existing tests are mostly H2/unit/configuration tests. There is no
  Testcontainers profile, contract suite, full E2E harness, load/resilience
  harness, Springdoc setup or CI workflow yet.

## Test Layering

Keep the default `mvn test` loop deterministic and reasonably fast. Real
infrastructure tests must be opt-in through a documented Maven profile and use
Testcontainers-managed dynamic endpoints with no fixed host ports.

Integration coverage must exercise the real boundary it claims:

- PostgreSQL: Flyway, constraints, repositories and representative service
  persistence for Catalog, Order and Payment.
- Redis: serialization, cache-aside population/invalidation and bounded
  Redisson stampede protection.
- Kafka: listener containers, valid/duplicate/malformed delivery, retry and DLT
  behavior for Payment, Order and Audit consumers.
- MongoDB: BSON round trips, indexes, unique replay boundaries and notification
  delivery-ledger recovery.

Use deterministic polling with deadlines rather than fixed long sleeps. Tests
must clean up their own containers/resources and must never delete developer
Compose volumes or unrelated Docker resources.

## Contracts And End-To-End

Contract tests cover the actual version-1 JSON emitted and accepted for:

- `OrderCreated`
- `PaymentSucceeded`
- `PaymentFailed`

They must verify envelope IDs, type/version, aggregate/order consistency,
timestamps, money/currency, trace/correlation metadata and required payload
fields. Representative producer output must be parsed by each real consumer
contract so drift is detected before E2E execution. Do not create a shared
business-domain module merely to make producer and consumer tests identical.

The black-box E2E harness uses the full Compose stack, obtains local JWTs from
Keycloak and sends external REST traffic through Gateway. It must cover:

1. Authorized catalog fixture creation.
2. Customer browse/detail and order creation.
3. Order outbox -> Debezium -> Kafka -> Payment.
4. Payment outbox -> Debezium -> Kafka -> Order and Audit Notification.
5. Happy path ending in `PAID` with audit plus one email and one push delivery.
6. Provider-decline path ending in `PAYMENT_FAILED` with consistent audit and
   notification state.
7. Duplicate/replay evidence without a second payment attempt or completed
   notification.

Use unique run identifiers and bounded waits with diagnostics. No real tokens,
contact data, provider credentials or external services belong in the repo.

## One-Command Environment

The release target is a fresh clone runnable with:

```bash
docker compose up --build
```

The Compose model must include infrastructure, Keycloak, observability, all
five application services, a deterministic local mock payment provider and
idempotent registration of both Debezium connectors. Application containers
use internal DNS names, environment-driven safe local defaults, health checks
and explicit readiness dependencies. Container builds should be reproducible,
run on Java 21 and avoid root execution where practical.

Smoke scripts must be bounded and diagnostic. Cleanup is explicit and
non-destructive by default; never run `docker compose down -v`, remove volumes
or reset databases as part of routine validation.

## Load And Resilience

Load work is evidence, not a production capacity claim. Provide a small smoke
profile and an explicit opt-in higher-load profile. Record latency percentiles,
throughput and errors, and demonstrate warm Catalog cache behavior with
existing metrics or another bounded database-read signal. Keep defaults safe
for a developer laptop.

Resilience scenarios may temporarily restart only this project's Payment or
Audit service and temporarily interrupt only this project's Kafka service.
They must verify eventual consistency and absence of duplicate payment
attempts, terminal events and completed notifications. Use deadlines, restore
the touched service and leave developer data intact.

## OpenAPI, Documentation And CI

Expose OpenAPI JSON/Swagger UI for externally useful REST APIs without
weakening JWT/RBAC. Document bearer security, roles, request/response schemas,
pagination and Problem Details. Keep actuator and internal implementation
details out of the public contract.

The final documentation set must link:

- fresh-clone setup and one-command startup
- authenticated demo flow
- OpenAPI endpoints
- service/data ownership architecture
- PostgreSQL and MongoDB ERD/schema views
- CDC/Kafka flows
- health, metrics, tracing, connector and DLT operations
- integration, contract, E2E, load and resilience commands

CI uses Java 21, least-privilege permissions, bounded timeouts and safe Maven
caching. It runs unit, integration and contract checks and, where Docker is
available, a bounded image/full-stack or E2E smoke. It must not publish images,
create releases, expose secrets, commit or push.

## Non-Goals And Safety

- No Kubernetes, Terraform, cloud deployment or production registry work.
- No real payment, email, push or customer contact integration.
- No unbounded stress tests or production performance claims.
- No destructive Docker cleanup, database resets or unrelated process control.
- No event-schema redesign unless an executable contract test proves a
  compatibility defect; prefer backward-compatible fixes.
- Do not edit the source PDF/XLSX or `LiveAssist-download/`.

## Token Discipline

Use this context pack, the current task block and its listed dependencies. Do
not re-read the source PDF/XLSX unless these compact requirements are
insufficient. Complete one task, update state/handoff, validate and stop.
