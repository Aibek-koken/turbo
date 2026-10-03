# Project State

Last updated: 2026-10-03

## Current Status

ECOM-009 is complete. The repository now has a Java 21 / Spring Boot 3.x Maven
monorepo scaffold, a local Docker Compose infrastructure baseline, an
importable Keycloak realm for local gateway/API testing, secure gateway
routing, service-level RBAC for the first downstream service boundaries and a
local observability baseline. Catalog Service now has its initial PostgreSQL
schema, Flyway migrations, JPA entities, repositories, admin management API and
customer-facing browse/detail API.

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

## Active Delivery Target

Night run target:

1. Sprint 1 completely.
2. The first practical slice of Sprint 2: catalog schema, admin API and customer
   browse/detail API.

Excluded from this first night unless explicitly added later:

- Redis/Redisson cache stampede implementation.
- Spring Batch supplier import.
- Order, payment, audit, notification and E2E flows.
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

ECOM-009 validation passed:

```bash
scripts/project-validate.sh catalog-validate
```

Catalog tests also passed with Java 21 selected explicitly:

```bash
JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.11/libexec/openjdk.jdk/Contents/Home mvn -q -pl services/catalog-service -am test
```

Running plain `mvn -q -pl services/catalog-service -am test` without setting
`JAVA_HOME` still picks up Java 17 and fails with `release version 21 not
supported`.

## Next Command

The first-night queued scope is complete. Do not start Redis, Spring Batch or
later service work unless the task queue is extended in a new agent session.

```bash
scripts/night-agent-runner.sh --agent codex --overnight --max-minutes 28800
```

Next task: none currently queued.
