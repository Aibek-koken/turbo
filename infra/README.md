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

## Order Outbox CDC

The Order Service outbox connector lives at
`infra/debezium/order-outbox-connector.json`. It captures only
`orders.public.outbox_events`, routes records to `ecommerce.order.events`, uses
`aggregate_id` as the Kafka key and emits the `payload` JSON as the Kafka value.
That payload is the complete versioned event envelope produced by the Order
Service.

Local connector credentials and logical-replication names are placeholders in
`.env.example`. Copy them to `.env` for local overrides; do not store real
credentials in the repository.

```bash
docker compose up -d postgres kafka debezium-connect
scripts/register-order-outbox-connector.sh
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
credentials in the repository.

```bash
docker compose up -d postgres kafka debezium-connect
scripts/register-payment-outbox-connector.sh
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
