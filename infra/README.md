# Local Infrastructure

This directory supports the local Docker Compose baseline for the portfolio
microservices environment. Values in `.env.example` are local placeholders, not
production credentials.

## Services

| Service | Host port | Purpose |
| --- | ---: | --- |
| PostgreSQL | 5432 | Relational stores for catalog, order, payment and Keycloak data. |
| Redis | 6379 | Future cache-aside and Redisson lock backend. |
| Kafka | 9092 | Local event broker for order, payment and audit flows. |
| Debezium Connect | 8086 | Future CDC connector runtime. Container port remains 8083. |
| MongoDB | 27017 | Audit and notification document store. |
| Keycloak | 8085 | Local OIDC provider with imported `ecommerce` realm for API testing. |
| Keycloak management | 9000 | Keycloak health and metrics endpoint. |
| Prometheus | 9090 | Metrics collection for infrastructure and Spring actuator endpoints. |
| Jaeger UI | 16686 | Local trace viewer. |
| OTLP gRPC | 4317 | OpenTelemetry collector endpoint exposed by Jaeger. |
| OTLP HTTP | 4318 | OpenTelemetry collector endpoint exposed by Jaeger. |

## Usage

```bash
cp .env.example .env
docker compose up -d
docker compose ps
```

Prometheus scrapes the Spring services through `host.docker.internal` on ports
8080 through 8084. Start the services on the host with their default ports when
you want local application metrics to appear.

The Postgres container enables logical replication settings required by future
Debezium tasks and initializes local databases named `catalog`, `orders`,
`payments` and `keycloak`.

Keycloak imports `infra/keycloak/ecommerce-realm.json` on startup. The imported
realm defines local testing clients and the `CUSTOMER`, `CATALOG_ADMIN` and
`OPS_ADMIN` realm roles.
