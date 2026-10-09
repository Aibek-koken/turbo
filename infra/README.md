# Local Infrastructure

This directory supports the local Docker Compose baseline for the portfolio
microservices environment. Values in `.env.example` are local placeholders, not
production credentials.

## Services

| Service | Host port | Purpose |
| --- | ---: | --- |
| PostgreSQL | 5432 | Relational stores for catalog, order, payment and Keycloak data. |
| Redis | 6379 | Catalog product-detail cache-aside and Redisson lock backend. |
| Kafka | 9092 | Local event broker for order, payment and audit flows. |
| Debezium Connect | 8086 | CDC connector runtime for Order and Payment outbox publication. Container port remains 8083. |
| MongoDB | 27017 | Audit and notification document store. |
| Keycloak | 8085 | Local OIDC provider with imported `ecommerce` realm for API testing. |
| Keycloak management | 9000 | Keycloak health and metrics endpoint. |
| Prometheus | 9090 | Metrics collection for infrastructure and Spring actuator endpoints. |
| Jaeger UI | 16686 | Local trace viewer. |
| OTLP gRPC | 4317 | OpenTelemetry collector endpoint exposed by Jaeger. |
| OTLP HTTP | 4318 | OpenTelemetry collector endpoint exposed by Jaeger. |
| Gateway Service | 8080 | JWT-validating API entry point. |
| Catalog Service | 8081 | Catalog API, PostgreSQL owner and Redis cache client. |
| Order Service | 8082 | Order API, PostgreSQL owner and payment-result consumer. |
| Payment Service | 8083 | Payment event consumer, PostgreSQL owner and mock-provider client. |
| Audit Notification Service | 8084 | MongoDB audit/notification owner and Kafka consumer. |
| Mock Payment Provider | 8089 | Deterministic local HTTP payment-provider stub. |

## Usage

```bash
cp .env.example .env
docker compose up --build
scripts/verify-full-stack.sh --skip-up
docker compose ps
```

Prometheus scrapes the Spring services through `host.docker.internal` on ports
8080 through 8084. With the full Compose stack, those ports are the published
application container ports.

The Postgres container enables logical replication settings used by the Order
and Payment Debezium outbox connectors and initializes local databases named
`catalog`, `orders`, `payments` and `keycloak`.

Keycloak imports `infra/keycloak/ecommerce-realm.json` on startup. The imported
realm defines local testing clients and the `CUSTOMER`, `CATALOG_ADMIN` and
`OPS_ADMIN` realm roles.

## Application Images

Gateway, Catalog, Order, Payment and Audit Notification each have a service-local
multi-stage Dockerfile. The build stage uses Maven on Java 21, and the runtime
stage uses a Java 21 JRE with a non-root user. Docker Compose builds these local
images from the repository root so Maven can resolve the parent POM and the
`libs/security-support` module.

Application containers use internal Compose DNS for infrastructure and
downstream service calls:

- PostgreSQL: `postgres:5432`
- Redis: `redis:6379`
- Kafka: `kafka:9092`
- MongoDB: `mongodb:27017`
- Keycloak JWKs: `keycloak:8080`
- Catalog from Order: `catalog-service:8081`
- Mock payment provider from Payment: `mock-payment-provider:8089`

Host-facing ports are controlled through `.env.example` placeholders such as
`GATEWAY_SERVICE_HOST_PORT`, `CATALOG_SERVICE_HOST_PORT` and
`MOCK_PAYMENT_PROVIDER_HOST_PORT`.

## One-Command Startup And Smoke Check

The default Compose graph is intended to run from a clean clone with:

```bash
docker compose up --build
```

That command starts PostgreSQL, Redis, Kafka, Debezium Connect, MongoDB,
Keycloak, Prometheus, Jaeger, all five Spring services, the mock payment
provider and the Debezium connector bootstrap service. The bootstrap service
waits for PostgreSQL, Kafka, Connect, Order and Payment readiness before
idempotently registering:

- `ecommerce-order-outbox-v1`
- `ecommerce-payment-outbox-v1`

The helper below performs the bounded full-stack smoke validation without
deleting data:

```bash
scripts/verify-full-stack.sh --skip-up
```

Omit `--skip-up` to let the script start Compose in detached mode; it builds
local images when they are missing. Use `--rebuild` when you explicitly want a
fresh local image rebuild. Use `--cleanup` only when you want the script to stop
containers afterward; it does not remove volumes.

## Mock Payment Provider

The local mock provider exposes:

- `GET /actuator/health`
- `POST /api/mock-payments/authorize`

It approves requests by default and returns a deterministic provider reference
based on the incoming `providerRequestId`. Scenario triggers are local-only and
configurable:

- amount `400.00` returns HTTP 402 with `DECLINED`
- amount `408.00` sleeps for `MOCK_PAYMENT_PROVIDER_TIMEOUT_SECONDS`
- amount `500.00` returns HTTP 503

The provider also accepts an optional request field named `scenario` with
`approved`, `declined`, `timeout` or `5xx`, which is useful for direct manual
checks without changing application code.

## Order Outbox CDC

The Order Service outbox connector lives at
`infra/debezium/order-outbox-connector.json`. It captures only
`orders.public.outbox_events`, routes records to `ecommerce.order.events`, uses
`aggregate_id` as the Kafka key and emits the `payload` JSON as the Kafka value.
That payload is the complete versioned event envelope produced by the Order
Service.

Local connector credentials and logical-replication names are placeholders in
`.env.example`. Copy them to `.env` for local overrides; do not store real
credentials in the repository. The default full-stack Compose graph registers
this connector automatically. For manual CDC checks, use the same idempotent
bootstrap helper; it registers both outbox connectors with rendered local
placeholder values.

```bash
docker compose up -d postgres kafka debezium-connect order-service payment-service
scripts/bootstrap-debezium-connectors.sh
curl -fsS http://localhost:${DEBEZIUM_CONNECT_PORT:-8086}/connectors/ecommerce-order-outbox-v1/status | jq .
docker compose exec kafka kafka-topics.sh --bootstrap-server kafka:9092 --list
docker compose exec kafka kafka-topics.sh --bootstrap-server kafka:9092 --describe --topic ecommerce.order.events
docker compose exec kafka kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic ecommerce.order.events --from-beginning --max-messages 5 --property print.key=true --property key.separator='|'
```

For a bounded local smoke check, make sure the Order schema has been migrated in
the local `orders` database, then run:

```bash
scripts/verify-order-cdc.sh
```

The smoke helper inserts one synthetic outbox row and looks for its event ID and
aggregate ID on `ecommerce.order.events`. It does not delete or truncate any
existing data.

## Payment Outbox CDC

The Payment Service outbox connector lives at
`infra/debezium/payment-outbox-connector.json`. It captures only
`payments.public.outbox_events`, routes records to `ecommerce.payment.events`,
uses `aggregate_id` as the Kafka key and emits the stored `payload` JSON as the
Kafka value. That payload is the complete versioned `PaymentSucceeded` or
`PaymentFailed` event envelope produced by the Payment Service.

Local connector credentials and logical-replication names are placeholders in
`.env.example`. Copy them to `.env` for local overrides; do not store real
credentials in the repository. The default full-stack Compose graph registers
this connector automatically. For manual CDC checks, use the same idempotent
bootstrap helper; it registers both outbox connectors with rendered local
placeholder values.

```bash
docker compose up -d postgres kafka debezium-connect order-service payment-service
scripts/bootstrap-debezium-connectors.sh
curl -fsS http://localhost:${DEBEZIUM_CONNECT_PORT:-8086}/connectors/ecommerce-payment-outbox-v1/status | jq .
docker compose exec kafka kafka-topics.sh --bootstrap-server kafka:9092 --list
docker compose exec kafka kafka-topics.sh --bootstrap-server kafka:9092 --describe --topic ecommerce.payment.events
docker compose exec kafka kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic ecommerce.payment.events --from-beginning --max-messages 5 --property print.key=true --property key.separator='|'
```

For a bounded local smoke check, make sure the Payment schema has been migrated
in the local `payments` database, then run:

```bash
scripts/verify-payment-cdc.sh
```

The smoke helper inserts one synthetic payment result outbox row and looks for
its event ID and order ID on `ecommerce.payment.events`. It does not delete or
truncate any existing data.
