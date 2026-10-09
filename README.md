# E-Commerce Microservices Portfolio

Java 21 / Spring Boot 3.x monorepo for a portfolio-grade e-commerce platform.
The project is organized as independently deployable service modules with
service-owned data boundaries.

## Services

- `services/gateway-service` - API entry point, JWT validation, routing and correlation propagation.
- `services/catalog-service` - Catalog API, supplier CSV import, PostgreSQL ownership and Redis/Redisson product-detail caching.
- `services/order-service` - Order API, product snapshots, state history, PostgreSQL ownership, transactional outbox and payment-result consumption.
- `services/payment-service` - Payment event consumer, idempotent processing, mock-provider client, PostgreSQL ownership and payment-result outbox.
- `services/audit-notification-service` - Kafka audit consumers, MongoDB audit/notification persistence and local mock email/push delivery.

No shared business domain module exists. Cross-service business contracts stay
at the HTTP/OpenAPI and versioned event-envelope boundaries; shared code is
limited to infrastructure helpers such as `libs/security-support`.

## Local Setup

Prerequisites:

- Java 21
- Maven 3.9+
- Docker with Docker Compose
- `curl` and `jq` for manual API and evidence checks
- Bash-compatible shell for the validation scripts

One-command startup from a fresh clone:

```bash
cp .env.example .env
docker compose up --build
```

The `.env` copy is optional when you accept the checked-in local placeholders,
but it is the normal place for local port or password overrides. The Compose
graph starts PostgreSQL, Redis, Kafka, Debezium Connect, MongoDB, Keycloak,
Prometheus, Jaeger, Gateway, Catalog, Order, Payment, Audit Notification, the
mock payment provider and idempotent Debezium connector bootstrap.

Validate a foreground stack from another shell:

```bash
scripts/verify-full-stack.sh --skip-up
```

Useful validation commands:

| Purpose | Command |
| --- | --- |
| Repository shape | `scripts/project-validate.sh structure` |
| Compose configuration | `scripts/project-validate.sh compose-config` |
| Full-stack smoke | `scripts/project-validate.sh full-stack-smoke` |
| Maven compile/package validation | `mvn -DskipTests validate` |
| Default unit tests | `mvn test` |
| Testcontainers integration suites | `scripts/project-validate.sh integration-test` |
| Producer/consumer event contracts | `scripts/project-validate.sh contract-test` |
| Authenticated full-stack E2E | `scripts/project-validate.sh e2e-test` |
| API documentation tests | `scripts/project-validate.sh api-docs-test` |
| CI workflow configuration | `scripts/project-validate.sh ci-config` |
| Compose image build validation | `scripts/project-validate.sh compose-image-build` |
| Final release-readiness gate | `scripts/project-validate.sh release-check` |
| Bounded load smoke | `scripts/project-validate.sh load-smoke` |
| Bounded restart/replay resilience smoke | `scripts/project-validate.sh resilience-smoke` |
| Documentation presence checks | `scripts/project-validate.sh docs-check` |

For a single host-run service during development:

```bash
mvn -pl services/catalog-service -am spring-boot:run
```

Default service ports:

| Service | Port |
| --- | ---: |
| gateway-service | 8080 |
| catalog-service | 8081 |
| order-service | 8082 |
| payment-service | 8083 |
| audit-notification-service | 8084 |
| mock-payment-provider | 8089 |

Each service exposes actuator health on `/actuator/health` and Prometheus
metrics on `/actuator/prometheus`. Local tracing exports to Jaeger through OTLP
HTTP by default, and request correlation uses `X-Correlation-Id`.

Business OpenAPI documents are published directly by the four HTTP business
services:

| Service | OpenAPI JSON | Swagger UI |
| --- | --- | --- |
| Catalog | `http://localhost:8081/v3/api-docs` | `http://localhost:8081/swagger-ui/index.html` |
| Order | `http://localhost:8082/v3/api-docs` | `http://localhost:8082/swagger-ui/index.html` |
| Payment | `http://localhost:8083/v3/api-docs` | `http://localhost:8083/swagger-ui/index.html` |
| Audit Notification | `http://localhost:8084/v3/api-docs` | `http://localhost:8084/swagger-ui/index.html` |

The gateway routes protected API traffic to the local service ports by default:

| External path | Target env var | Default target |
| --- | --- | --- |
| `/api/catalog/**` | `ECOMMERCE_CATALOG_SERVICE_URI` | `http://localhost:8081` |
| `/api/orders/**` | `ECOMMERCE_ORDER_SERVICE_URI` | `http://localhost:8082` |
| `/api/payments/**` | `ECOMMERCE_PAYMENT_SERVICE_URI` | `http://localhost:8083` |
| `/api/audit-notifications/**` | `ECOMMERCE_AUDIT_NOTIFICATION_SERVICE_URI` | `http://localhost:8084` |

JWT validation defaults to the local Keycloak realm issuer
`http://localhost:8085/realms/ecommerce`. Override it with
`ECOMMERCE_SECURITY_ISSUER_URI` and `ECOMMERCE_SECURITY_JWK_SET_URI` when
running against a different realm.

## Authenticated Demo

The shortest demo is the black-box E2E harness:

```bash
scripts/project-validate.sh e2e-test
```

It creates local Keycloak users, obtains JWTs, creates Catalog fixtures through
Gateway, runs an approved order to `PAID`, runs a `400.00` order to
`PAYMENT_FAILED`, verifies Payment PostgreSQL, Audit MongoDB, mock email/push
delivery evidence and duplicate replay safety, then removes only the generated
Keycloak users.

For a manual curl walkthrough with the same happy-path and payment-failure
evidence, see [docs/demo-runbook.md](docs/demo-runbook.md).

## Local Infrastructure

`docker-compose.yml` defines the local infrastructure baseline:

- PostgreSQL on 5432
- Redis on 6379
- Kafka on 9092
- Debezium Connect on 8086
- MongoDB on 27017
- Keycloak on 8085
- Prometheus on 9090
- Jaeger UI on 16686, with OTLP on 4317/4318
- Gateway, Catalog, Order, Payment and Audit Notification application services
- A deterministic local mock payment provider on 8089

Copy `.env.example` to `.env` for local-only overrides. The checked-in values
are placeholders and must not be reused as real credentials.

The checked-in Compose model builds the five Spring services from Java 21
multi-stage Dockerfiles and runs the application containers with internal DNS
targets such as `postgres`, `kafka`, `catalog-service` and
`mock-payment-provider`. Host ports remain configurable through `.env`.

`docker compose up --build` starts the complete local platform, including the
five application services, mock payment provider, observability stack and a
bounded idempotent Debezium connector bootstrap service. To validate a running
foreground stack from another shell, run:

```bash
scripts/verify-full-stack.sh --skip-up
```

For an unattended smoke run, `scripts/verify-full-stack.sh` starts the same
Compose stack in detached mode, builds local images when they are missing,
waits with a deadline, verifies service readiness, both outbox connectors,
Prometheus and Jaeger, and leaves volumes and application data intact. Use
`--rebuild` when you explicitly want the smoke script to rebuild local images.

The mock payment provider approves requests by default. For local scenario
testing it deterministically declines an authorization whose amount is
`400.00`, sleeps long enough to trigger the Payment Service timeout path at
`408.00`, and returns HTTP 503 at `500.00`. It also exposes a local-only
request ledger at `/api/mock-payments/requests` so the E2E harness can prove a
purchase flow was not charged twice.

## CI And Release Readiness

GitHub Actions runs `.github/workflows/ci.yml` with Java 21, read-only
repository permissions, concurrency cancellation and Maven dependency caching.
The workflow runs repository checks, unit/OpenAPI tests, event contracts,
opt-in Testcontainers integration suites when Docker is available, local
Compose image builds and a bounded full-stack smoke. Failed Maven or Docker
runs upload bounded diagnostics as workflow artifacts.

Before treating a local checkout as release-ready, run:

```bash
scripts/project-validate.sh release-check
```

That command checks required docs and CI wiring, validates Compose
configuration, runs the OpenAPI/unit, contract and integration Maven suites,
and builds the local Compose images without publishing artifacts. Before the
Testcontainers suite it stops only this project's running Compose services to
free local Docker resources; containers, volumes and application data are
preserved. Set `RELEASE_CHECK_STOP_COMPOSE=0` only when Docker has enough
memory to run both stacks concurrently. Longer load and restart/replay
resilience runs remain explicit manual evidence commands:
`scripts/project-validate.sh load-smoke` and
`scripts/project-validate.sh resilience-smoke`.

## Documentation And Runbooks

| Topic | Document |
| --- | --- |
| Architecture, ownership, CDC/Kafka flows, observability and security boundaries | [docs/architecture.md](docs/architecture.md) |
| Catalog, Order, Payment and Mongo audit/notification data model | [docs/data-model.md](docs/data-model.md) |
| Authenticated happy path, payment failure and evidence checks | [docs/demo-runbook.md](docs/demo-runbook.md) |
| OpenAPI JSON, Swagger UI, roles, pagination and Problem Details | [docs/api-documentation.md](docs/api-documentation.md) |
| Fresh-clone setup and one-command startup | [docs/developer-setup.md](docs/developer-setup.md) |
| Infrastructure ports, connector bootstrap and CDC inspection | [infra/README.md](infra/README.md) |
| Local Keycloak realm, roles and placeholder token retrieval | [docs/keycloak-local.md](docs/keycloak-local.md) |
| Service-level RBAC and customer ownership rules | [docs/service-rbac.md](docs/service-rbac.md) |
| Health, metrics, traces, alerts, DLT inspection and correlation checks | [docs/observability-runbook.md](docs/observability-runbook.md) |
| Integration, contract and full-stack E2E validation | [docs/integration-tests.md](docs/integration-tests.md) |
| Bounded load, cache-effect proof and virtual-thread observation | [docs/load-validation.md](docs/load-validation.md) |
| Restart/replay resilience, DLT probes and duplicate replay checks | [docs/resilience-validation.md](docs/resilience-validation.md) |
| Catalog supplier CSV import contract | [docs/catalog/supplier-import.md](docs/catalog/supplier-import.md) |
