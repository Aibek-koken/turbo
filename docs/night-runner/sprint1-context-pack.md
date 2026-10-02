# Sprint 1 Context Pack

Sprint 1 goal: platform foundation, authentication, authorization, gateway and
observability.

Stories covered:

- US-01 Service Skeleton & Docker Compose
- US-02 Keycloak Realm, Clients & Roles
- US-03 Secure API Gateway Routing
- US-04 Service-Level RBAC
- US-05 Observability Baseline

## Required Outcomes

- Standard Java 21 / Spring Boot 3.x service layout.
- Monorepo with service boundaries preserved.
- Docker Compose can define infrastructure from a clean checkout.
- Keycloak realm has roles: `CUSTOMER`, `CATALOG_ADMIN`, `OPS_ADMIN`.
- Gateway validates JWTs and routes to downstream services.
- Each service enforces RBAC locally, not only at the gateway.
- Actuator health and Prometheus endpoints are available.
- Trace/correlation metadata is planned consistently across REST and Kafka.

## Non-Goals

- Do not implement catalog business logic beyond stubs required for security
  and routing.
- Do not implement order, payment, audit or notification business flows.
- Do not add Redis cache behavior yet.
- Do not add Spring Batch yet.
- Do not add real production secrets.

## Token Discipline

Read `DESIGN.md`, `STATE.md`, this context pack and the current task block.
Avoid opening the original PDF/XLSX unless a task explicitly needs source proof.

