# Architecture

This project is a local, portfolio-grade e-commerce platform built around
service-owned data, JWT-protected HTTP APIs, transactional outbox publication,
Kafka consumers and observable failure boundaries. It is designed to run from a
fresh clone with `docker compose up --build`.

Related docs:

- [README](../README.md) for startup commands and validation entry points.
- [Data model](data-model.md) for table and document schemas.
- [Demo runbook](demo-runbook.md) for authenticated happy-path and failure
  evidence.
- [API documentation](api-documentation.md) for OpenAPI and Swagger UI URLs.
- [Observability runbook](observability-runbook.md) for health, metrics, traces
  and DLT inspection.
- [Infrastructure runbook](../infra/README.md) for Compose services and
  Debezium connector operations.

## Service And Data Ownership

| Boundary | Owns | Does not own |
| --- | --- | --- |
| Gateway | Edge routing, JWT validation, correlation header propagation. | Business data or service databases. |
| Catalog Service | `catalog` PostgreSQL tables, supplier import Batch metadata, Redis product-detail cache keys and Redisson locks. | Orders, payments, audit documents or customer ownership decisions outside catalog visibility. |
| Order Service | `orders` PostgreSQL tables, immutable order-item snapshots, status history, `OrderCreated` outbox rows and payment-result processed-event markers. | Catalog product records, payment attempts or audit documents. |
| Payment Service | `payments` PostgreSQL tables, provider attempts, `OrderCreated` processed-event markers and terminal payment-result outbox rows. | Order state transitions, catalog data or external real payment providers. |
| Audit Notification Service | MongoDB `audit_events` and `notification_deliveries`, audit Kafka consumers and mock email/push delivery state. | Source-of-truth order/payment state or real email/push providers. |

The services intentionally do not share JPA entities or a common business-domain
module. Shared code is limited to infrastructure support such as JWT role
mapping in `libs/security-support`.

## Synchronous Calls

```text
Client / script / Swagger UI
        |
        | bearer JWT, X-Correlation-Id
        v
Gateway Service :8080
        |
        +--> Catalog Service :8081
        |
        +--> Order Service :8082
        |        |
        |        +--> Catalog Service :8081
        |             product snapshots, inbound bearer token forwarded
        |
        +--> Payment Service :8083
        |
        +--> Audit Notification Service :8084

Payment Service :8083
        |
        +--> Mock Payment Provider :8089
             local deterministic approvals, declines, timeouts and 5xx responses
```

Runtime API traffic enters through Gateway on `/api/**`. OpenAPI JSON and
Swagger UI are served directly from Catalog, Order, Payment and Audit
Notification service ports so each service documents its owned API contract.

Order is the only business service that synchronously calls another business
service in the purchase path: it resolves Catalog product snapshots before the
order transaction begins. Payment calls only the local mock provider container.

## Outbox And CDC Paths

Order and Payment publish integration events by writing outbox rows in the same
database transaction as the state change. Debezium Connect reads those rows and
publishes the stored JSON envelope to Kafka. The application code does not
publish Kafka messages directly after commit.

```text
Order create request
  -> orders.orders / orders.order_items / orders.order_status_history
  -> orders.outbox_events (OrderCreated)
  -> Debezium connector ecommerce-order-outbox-v1
  -> Kafka topic ecommerce.order.events
  -> Payment Service OrderCreated consumer
  -> Audit Notification OrderCreated consumer
```

```text
Payment terminal result
  -> payments.payments / payments.payment_attempts
  -> payments.outbox_events (PaymentSucceeded or PaymentFailed)
  -> Debezium connector ecommerce-payment-outbox-v1
  -> Kafka topic ecommerce.payment.events
  -> Order Service payment-result consumer
  -> Audit Notification payment-result consumer
```

Both connectors are registered by the default Compose
`debezium-connector-bootstrap` service. Manual connector status and topic
inspection commands live in the [infrastructure runbook](../infra/README.md).

## Event Envelope

All business Kafka events use a versioned JSON envelope:

```json
{
  "eventId": "uuid",
  "eventType": "OrderCreated",
  "eventVersion": 1,
  "aggregateId": "order-uuid",
  "occurredAt": "2026-10-02T00:00:00Z",
  "traceId": "...",
  "correlationId": "...",
  "data": {}
}
```

V1 event types are:

- `OrderCreated`
- `PaymentSucceeded`
- `PaymentFailed`

Contract tests generate producer output from the real Order and Payment outbox
factories, then parse that output through the real Payment, Order and Audit
Notification consumers. Run them with:

```bash
scripts/project-validate.sh contract-test
```

## Kafka Consumers

| Consumer | Topic | Default group | Durable idempotency | Dead-letter topic |
| --- | --- | --- | --- | --- |
| Payment `OrderCreated` | `ecommerce.order.events` | `payment-service.order-created.v1` | `payments.processed_events` keyed by consumer and event ID. | `ecommerce.order.events.DLT` |
| Audit `OrderCreated` | `ecommerce.order.events` | `audit-notification-service.order-created.v1` | Mongo unique `audit_events.event_id`, plus unique delivery event/channel records. | `ecommerce.order.events.audit.DLT` |
| Order payment results | `ecommerce.payment.events` | `order-service.payment-results.v1` | `orders.processed_events` keyed by consumer and event ID. | `ecommerce.payment.events.DLT` |
| Audit payment results | `ecommerce.payment.events` | `audit-notification-service.payment-results.v1` | Mongo unique `audit_events.event_id`, plus unique delivery event/channel records. | `ecommerce.payment.events.audit.DLT` |

Malformed payloads are classified as non-retryable and sent to the configured
DLT. Transient database, provider or Kafka-side failures use bounded retry and
backoff. The Audit Notification service exposes
`ecommerce_kafka_dead_letter_topic_depth` for the fixed configured DLT topics.

## Observability Boundaries

- Every Spring service exposes `/actuator/health`, `/actuator/info` and
  `/actuator/prometheus`.
- Prometheus scrapes the five Spring services, Keycloak and Prometheus itself.
- Jaeger receives OTLP HTTP traces from the local services.
- Gateway accepts or generates `X-Correlation-Id`, forwards it downstream and
  returns it to the client.
- Order copies active `traceId` and `correlationId` into the `OrderCreated`
  envelope.
- Kafka consumers restore envelope trace, correlation, event and aggregate
  metadata into logging scope while handling the message.
- Metrics use bounded labels only. Event IDs, order IDs, payment IDs, customer
  IDs, trace IDs and correlation IDs stay in logs, payloads or persistence, not
  metric tags.

Operational checks:

| Check | Runbook |
| --- | --- |
| Service health and Prometheus endpoints | [observability-runbook.md](observability-runbook.md#endpoints) |
| Prometheus targets and alert rules | [observability-runbook.md](observability-runbook.md#local-metrics) |
| Jaeger traces and correlation flow | [observability-runbook.md](observability-runbook.md#local-trace-and-correlation-flow) |
| Debezium connector status | [Order CDC](../infra/README.md#order-outbox-cdc), [Payment CDC](../infra/README.md#payment-outbox-cdc) |
| DLT depth and DLT simulation | [observability-runbook.md](observability-runbook.md#bounded-dead-letter-signal-simulation) |
| Integration, contract and E2E checks | [integration-tests.md](integration-tests.md) |
| Load smoke | [load-validation.md](load-validation.md) |
| Restart/replay resilience | [resilience-validation.md](resilience-validation.md) |

## Security Boundaries

Keycloak is the local OIDC provider. Gateway validates bearer JWTs at the edge,
and each downstream business service repeats resource-server authorization so a
direct service-port request is not an authorization bypass.

| API family | Required roles |
| --- | --- |
| Catalog browse | `CUSTOMER`, `CATALOG_ADMIN`, `OPS_ADMIN` |
| Catalog admin | `CATALOG_ADMIN` |
| Order customer | `CUSTOMER` |
| Order operations | `OPS_ADMIN` |
| Payment customer | `CUSTOMER`, `OPS_ADMIN`, scoped by JWT subject |
| Payment operations | `OPS_ADMIN` |
| Audit Notification customer | `CUSTOMER`, `OPS_ADMIN`, scoped by JWT subject |
| Audit Notification operations | `OPS_ADMIN` |

Customer-owned Order, Payment and Audit Notification reads derive ownership
from the authenticated JWT subject. API clients do not provide authoritative
customer IDs, product names or prices in the purchase path.

No real payment, email or push credentials are used. Payment talks only to the
local mock payment provider, and notification adapters are local mocks.
