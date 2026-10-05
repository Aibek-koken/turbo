# Sprint 5 Context Pack

Sprint 5 goal: complete the Audit Notification Service, persist business events
in MongoDB, deliver replay-safe mock email/push notifications, preserve
end-to-end correlation and expose useful operational metrics and alert signals.

Stories covered:

- US-21 Central Audit Event Store in MongoDB
- US-22 Email Notifications
- US-23 Push Notification Adapter
- US-24 End-to-End Correlation & Trace Propagation
- US-25 Operational Metrics & Alert Signals

## Existing Baseline

- Order Service publishes the complete version-1 `OrderCreated` envelope to
  `ecommerce.order.events` through its transactional outbox and Debezium.
- Payment Service consumes `OrderCreated`, handles it idempotently, calls a
  local mock provider and publishes terminal `PaymentSucceeded` or
  `PaymentFailed` envelopes through its own outbox to
  `ecommerce.payment.events`.
- Order Service consumes payment results idempotently and updates order status.
- Order and Payment consumers already have bounded retry/backoff and DLT
  configuration. Follow those local patterns where they fit; do not share
  their JPA domain or persistence classes.
- Audit Notification Service is currently a secured Spring Boot skeleton with
  actuator, Prometheus, OpenTelemetry and HTTP correlation logging, but no
  MongoDB persistence, Kafka consumers or notification implementation.
- Docker Compose already provides MongoDB, Kafka, Prometheus and Jaeger.
  MongoDB initializes the local `audit` database.

## Input Topics And Contracts

Audit Notification Service consumes as an independent consumer group from:

- `ecommerce.order.events`: `OrderCreated`, version 1.
- `ecommerce.payment.events`: `PaymentSucceeded` and `PaymentFailed`, version 1.

Every envelope contains:

- `eventId`
- `eventType`
- `eventVersion`
- `aggregateId` (the order ID)
- `occurredAt`
- `traceId`
- `correlationId`
- `data`

`OrderCreated.data` includes `orderId`, `customerId`, `status`, amounts,
currency, creation time and item snapshots.

Payment result `data` includes `paymentId`, `orderId`, `customerId`, amount,
currency, payment status, provider attempt outcome, safe provider/failure
details and the original `OrderCreated` event ID.

Define Audit-owned input records/parsers. Do not import Order or Payment JPA,
repository or application classes. Reject unsupported versions, inconsistent
aggregate/order IDs and incomplete required identifiers before side effects.

## MongoDB Audit Model

Persist one audit document per event ID with at least:

- event ID, type and version
- aggregate/order ID and customer ID when present
- payment ID when present
- occurred and received times
- trace ID and correlation ID
- source topic, partition and offset
- complete structured event payload

Use a unique event-ID index as the audit replay boundary. Add focused indexes
for event type, aggregate/order ID, correlation ID and occurred time. Store the
payload as structured BSON/JSON, not a flattened log string. Keep MongoDB owned
by Audit Notification Service.

Malformed events must create no audit or delivery records. Transient MongoDB
or Kafka failures remain retryable with bounded backoff and a service-specific
DLT. Duplicate delivery must converge on the existing audit document without
turning a uniqueness race into an endless retry.

## Notification Routing And Idempotency

Sprint 5 notifications cover:

- order created
- payment succeeded
- payment failed

Create separate `EMAIL` and `PUSH` delivery records for relevant events. A
compound unique key on event ID plus channel prevents duplicate completed
deliveries. Keep explicit pending/in-progress/sent/failed state, attempt count,
safe error detail and timestamps so interrupted or failed work can be retried.

Audit insertion and notification delivery are different idempotency concerns.
A replayed event may find the audit document already present but still needs to
recover a missing or failed channel delivery. A completed channel delivery must
not be sent again.

Email and push are local mock/test adapters. They may record invocations and
safe structured logs, but must not use real customer addresses, device tokens,
credentials, SMTP servers or production SDKs. Build payloads from customer,
order, event and status metadata already carried by the event.

## Correlation And Tracing

The purchase flow must remain inspectable as:

`Gateway -> Order -> Kafka -> Payment -> Kafka -> Audit/Notification`

Preserve an incoming `X-Correlation-Id`; generate one only when absent. Keep
`traceId`, `correlationId` and `eventId` in event envelopes, audit documents and
safe logs. Enable Kafka observation/consumer spans where needed and restore
envelope correlation/event metadata into MDC only for the active handling
scope, clearing it afterward.

The aggregate `observability-test` mode compiles Gateway and runs the remaining
service tests because the existing Gateway security suite binds a random local
port that restricted agent sandboxes do not permit. Run focused non-socket
Gateway unit tests for any Gateway code changed by the active task.

Identifiers are for traces, logs and audit queries. Never use event, order,
payment, customer, trace or correlation IDs as metric tags.

## Operational Metrics

Keep metric names and tags stable and low-cardinality. Sprint 5 needs signals
for:

- HTTP request latency and errors (Spring Boot meters are acceptable)
- Catalog cache hit/miss
- Kafka consumption success/failure/retry/DLT activity
- terminal payment failures
- audit persistence outcomes
- email/push delivery outcomes
- retained records/depth for the fixed configured DLT topics

Use only bounded tags such as service, topic, event type, channel and outcome.
Add Prometheus queries/rules for service availability, sustained HTTP errors,
payment failures, notification/consumer failures and positive DLT depth. Local
rules and inspection are in scope; production paging integrations and hosted
monitoring are not.

## Non-Goals

- Do not add real email, SMTP, push or device-provider integrations.
- Do not add real customer contact data or secrets.
- Do not add a new Auditor Keycloak role; operational endpoints remain under
  the existing `OPS_ADMIN` boundary if an endpoint is needed.
- Do not share MongoDB with Order or Payment business persistence.
- Do not change Order or Payment event schema unless a proven propagation bug
  requires a backward-compatible correction.
- Do not add Sprint 6 broad E2E/Testcontainers, load/resilience, deployment or
  CI work.
- Do not edit the source PDF/XLSX or `LiveAssist-download/`.

## Token Discipline

Use this context pack, the current task block and its listed dependencies. Do
not re-read the source PDF/XLSX unless these compact requirements are
insufficient.
