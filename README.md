# E-Commerce Microservices Portfolio

Java 21 / Spring Boot 3.x monorepo for a portfolio-grade e-commerce platform.
The project is organized as independently deployable service modules with
service-owned data boundaries.

## Services

- `services/gateway-service` - API entry point and JWT-validating Spring Cloud Gateway.
- `services/catalog-service` - catalog API boundary and future catalog PostgreSQL owner.
- `services/order-service` - order API boundary and future order/outbox PostgreSQL owner.
- `services/payment-service` - payment boundary and future idempotent payment event consumer.
- `services/audit-notification-service` - audit and notification boundary for future MongoDB/Kafka work.

No shared business domain module exists yet. Add a shared `libs` module only
when event contracts or infrastructure helpers create real duplication.

## Local Setup

Prerequisites:

- Java 21
- Maven 3.9+
- Docker with Docker Compose

Useful commands:

```bash
scripts/project-validate.sh structure
scripts/project-validate.sh compose-config
docker compose up -d
mvn -DskipTests validate
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

Each service exposes actuator health on `/actuator/health` and Prometheus
metrics on `/actuator/prometheus`. Local tracing exports to Jaeger through OTLP
HTTP by default, and request correlation uses `X-Correlation-Id`.

The gateway routes protected API traffic to the local service ports by default:

| External path | Target env var | Default target |
| --- | --- | --- |
| `/api/catalog/**` | `ECOMMERCE_CATALOG_SERVICE_URI` | `http://localhost:8081` |
| `/api/orders/**` | `ECOMMERCE_ORDER_SERVICE_URI` | `http://localhost:8082` |
| `/api/payments/**` | `ECOMMERCE_PAYMENT_SERVICE_URI` | `http://localhost:8083` |
| `/api/audit-notifications/**` | `ECOMMERCE_AUDIT_NOTIFICATION_SERVICE_URI` | `http://localhost:8084` |

JWT validation defaults to the local Keycloak realm issuer
`http://localhost:8085/realms/ecommerce`. Override it with
`ECOMMERCE_SECURITY_ISSUER_URI` and `ECOMMERCE_SECURITY_JWK_SET_URI` when running
against a different realm.

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

Copy `.env.example` to `.env` for local-only overrides. The checked-in values
are placeholders and must not be reused as real credentials.

See [infra/README.md](infra/README.md) for port and service-purpose notes.
See [docs/developer-setup.md](docs/developer-setup.md) for setup notes.
See [docs/keycloak-local.md](docs/keycloak-local.md) for the imported realm,
local clients and placeholder token retrieval flow.
See [docs/service-rbac.md](docs/service-rbac.md) for downstream
resource-server role rules and customer-scoped follow-up constraints.
See [docs/observability-runbook.md](docs/observability-runbook.md) for local
metrics, tracing and correlation checks.
See [docs/catalog/supplier-import.md](docs/catalog/supplier-import.md) for the
Catalog Service supplier CSV import contract.
