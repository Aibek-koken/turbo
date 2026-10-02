# E-Commerce Night Agent Task Queue

Statuses: `pending`, `in_progress`, `done`, `blocked`.

This queue is consumed by `scripts/night-agent-runner.sh`.

## Night Scope

Primary target:

- Sprint 1 complete.
- First part of Sprint 2: US-06, US-07 and US-08.

Do not continue into Redis, Spring Batch, Order, Payment, Audit, Notification or
CI unless the queue is explicitly extended.

## Shared Rules

- Do not ask the user questions overnight.
- If blocked, update `STATE.md` and `docs/night-runner/handoff.md`, then stop.
- Do not edit the source PDF/XLSX.
- Do not edit `LiveAssist-download/`.
- Do not commit or push from the agent session.
- Keep Java 21 as the target even if the local Java version is lower.
- Use compact context docs first. Do not repeatedly read the PDF/XLSX.

<!-- task:id=ECOM-001 phase=sprint1 status=pending -->
## ECOM-001: Scaffold the Java 21 Spring Boot monorepo

Status: pending

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

<!-- task:id=ECOM-002 phase=sprint1 status=pending -->
## ECOM-002: Add local infrastructure Docker Compose baseline

Status: pending

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

<!-- task:id=ECOM-003 phase=sprint1 status=pending -->
## ECOM-003: Configure Keycloak realm, clients and roles

Status: pending

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

<!-- task:id=ECOM-004 phase=sprint1 status=pending -->
## ECOM-004: Implement secure API Gateway routing

Status: pending

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

<!-- task:id=ECOM-005 phase=sprint1 status=pending -->
## ECOM-005: Add service-level RBAC baseline

Status: pending

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

<!-- task:id=ECOM-006 phase=sprint1 status=pending -->
## ECOM-006: Add observability baseline across services

Status: pending

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

<!-- task:id=ECOM-007 phase=sprint2 status=pending -->
## ECOM-007: Implement Catalog data model and Flyway migrations

Status: pending

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

<!-- task:id=ECOM-008 phase=sprint2 status=pending -->
## ECOM-008: Implement Catalog admin management API

Status: pending

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

<!-- task:id=ECOM-009 phase=sprint2 status=pending -->
## ECOM-009: Implement Catalog customer browse and detail API

Status: pending

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
