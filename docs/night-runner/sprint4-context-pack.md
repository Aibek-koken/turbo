# Sprint 4 Context Pack

Sprint 4 goal: complete the Payment Service event-driven payment flow with
idempotency, a mock provider, payment result events, Order Service status
updates and retry/dead-letter handling.

Stories covered:

- US-16 Consume OrderCreatedEvent & Create Payment
- US-17 Idempotent Payment Event Processing
- US-18 Mock Payment Provider via RestClient
- US-19 Payment Result Events & Order Update
- US-20 Retry, Backoff & Dead-Letter Handling

## Existing Baseline

- Order Service publishes `OrderCreated` through the transactional outbox and
  Debezium to Kafka topic `ecommerce.order.events`.
- The `OrderCreated` Kafka value is the complete versioned JSON envelope from
  the Order Service outbox payload.
- Payment Service is currently a secured Spring Boot service skeleton with
  actuator, resource-server RBAC and correlation logging, but no database,
  Kafka, provider or business persistence yet.
- PostgreSQL already initializes a local `payments` database.
- Kafka and Debezium Connect are already defined in Docker Compose.
- Order Service has a tested state machine for `CREATED`,
  `PAYMENT_PENDING`, `PAID`, `PAYMENT_FAILED` and `CANCELLED`.

## OrderCreated Input Contract

Payment Service consumes `OrderCreated` from `ecommerce.order.events`.

Expected envelope fields:

- `eventId`
- `eventType` = `OrderCreated`
- `eventVersion` = `1`
- `aggregateId` = order ID
- `occurredAt`
- `traceId`
- `correlationId`
- `data`

Expected `data` fields:

- `orderId`
- `customerId`
- `status` = `CREATED`
- `subtotalAmount`
- `totalAmount`
- `currency`
- `createdAt`
- `items`

Use `totalAmount` and `currency` as the payment amount. Do not call Catalog
from Payment Service and do not trust any client-supplied payment amount.

## Payment Persistence

Payment Service owns its PostgreSQL schema. Do not reuse Order Service JPA
entities or repositories.

Core persistence needs:

- `payments` for one local payment per order.
- `payment_attempts` for mock-provider calls and outcomes.
- `processed_events` for idempotent Kafka consumption.
- `outbox_events` for payment-result events.

Use UUID identifiers, UTC timestamps, `NUMERIC(19,4)` money columns, uppercase
three-letter currencies, optimistic locking for mutable payment state and
append-only outbox rows.

Recommended payment states:

- `PENDING`
- `SUCCEEDED`
- `FAILED`

Do not implement refunds, captures, multiple payment methods, manual review or
real payment-provider integrations in Sprint 4.

## Idempotency

Duplicate `OrderCreated` deliveries must not create duplicate payments or
duplicate provider attempts. The idempotency boundary is the source `eventId`
plus a stable consumer name.

Persist the processed-event marker in the same transaction as the business
effect or skip decision. Malformed payloads should not be marked as processed,
so corrected messages can be replayed.

Payment result consumption in Order Service also needs its own processed-event
marker. Duplicate `PaymentSucceeded` or `PaymentFailed` events must not append
duplicate order history rows.

## Mock Provider

Use Spring `RestClient` for the mock provider, with environment-driven base URL
and bounded timeout settings. Keep all provider settings as local placeholders;
do not add real credentials, tokens or production URLs.

Map provider outcomes explicitly:

- Success persists a provider reference and a terminal successful payment.
- Decline persists a terminal failed payment with a safe reason.
- Timeout and 5xx are transient/retryable until the retry/DLQ task defines the
  final retry behavior.
- Malformed provider responses are explicit failures and must not silently
  produce successful payments.

## Payment Result Events

Payment Service emits only terminal result events in Sprint 4:

- `PaymentSucceeded`
- `PaymentFailed`

Use the same event envelope shape as `OrderCreated`:

```json
{
  "eventId": "uuid",
  "eventType": "PaymentSucceeded",
  "eventVersion": 1,
  "aggregateId": "order-uuid",
  "occurredAt": "2026-10-02T00:00:00Z",
  "traceId": "...",
  "correlationId": "...",
  "data": {}
}
```

The `data` object should include at least payment ID, order ID, customer ID,
amount, currency, provider reference when available and the original
`OrderCreated` event ID. Use the order ID as the Kafka key or aggregate key so
events for one order remain ordered.

Persist terminal payment status and the result outbox row in one database
transaction. Do not publish directly to Kafka from application code.

## Order Result Handling

Order Service consumes payment results from `ecommerce.payment.events`.

On `PaymentSucceeded`, transition the order to `PAID`. On `PaymentFailed`,
transition it to `PAYMENT_FAILED`.

The current Sprint 3 create flow leaves a new order in `CREATED`. If a payment
result arrives while the order is still `CREATED`, transition through
`PAYMENT_PENDING` before the terminal state and preserve both history entries.
Invalid transitions must not corrupt the order.

## Retry And DLQ

Use bounded retries and dead-letter handling for Kafka consumers. Keep the
configuration local and deterministic, with environment-driven topic names,
retry count and backoff settings.

Retry transient provider, Kafka or database failures. Do not retry permanently
malformed business payloads forever; route or classify them according to the
task scope. Replay after retry or DLQ recovery must remain idempotent through
`processed_events` and database uniqueness.

## Non-Goals

- Do not implement audit persistence or notifications; that is Sprint 5.
- Do not add real payment-provider calls or real secrets.
- Do not implement refunds, inventory, shipping, tax, discounts or carts.
- Do not add broad Testcontainers/E2E infrastructure from Sprint 6.
- Do not introduce a shared business database or shared JPA domain module.

## Token Discipline

Use this context pack, the current task block and the listed Payment/Order or
infrastructure dependencies. Do not re-read the source PDF/XLSX unless these
compact requirements are insufficient.
