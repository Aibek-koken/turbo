# Sprint 3 Context Pack

Sprint 3 goal: implement the Order Service aggregate and publishable order
events with PostgreSQL, a transactional outbox, Debezium and Kafka.

Stories covered:

- US-11 Create Order with Product Snapshot
- US-12 Order State Machine
- US-13 Transactional Outbox Persistence
- US-14 Debezium CDC to Kafka
- US-15 Order Query & History API

## Existing Baseline

- Gateway routes `/api/orders/**` to Order Service and validates JWTs.
- Order Service validates JWTs locally. Customer routes allow `CUSTOMER` and
  `OPS_ADMIN`; operations routes require `OPS_ADMIN`.
- Catalog exposes active product details at
  `GET /api/catalog/products/{productId}` with product ID, SKU, name, price,
  currency and status.
- PostgreSQL already initializes an `orders` database and has logical
  replication settings enabled.
- Kafka and Debezium Connect are already defined in Docker Compose.

## Order Aggregate Contract

- The authenticated customer ID comes from the JWT `sub` claim. Never accept a
  customer ID from the request body.
- A create request contains product IDs and positive quantities only. Product
  names, SKUs, prices and currency come from Catalog Service.
- Store immutable item snapshots: product ID, SKU, name, quantity, unit price,
  currency and line total.
- Reject missing or inactive catalog products, duplicate product IDs, invalid
  quantities and mixed currencies. Do not trust client-supplied prices.
- Calculate money with `BigDecimal` and store the order subtotal/total and
  currency. V1 has no taxes, shipping, discounts or inventory reservation.
- Use UUID identifiers and UTC timestamps. Add optimistic locking to mutable
  order state.

## State Machine

V1 states are fixed by `DESIGN.md`:

- `CREATED`
- `PAYMENT_PENDING`
- `PAID`
- `PAYMENT_FAILED`
- `CANCELLED`

Keep transition rules explicit and centrally tested. Every accepted transition
must update the current state and append a timestamped history record in the
same transaction. Invalid transitions must leave both unchanged.

## Transactional Outbox

- Creating an order and its `OrderCreated` outbox event must use one database
  transaction. A forced rollback must leave neither record.
- Store a versioned JSON envelope with `eventId`, `eventType`, `eventVersion`,
  `aggregateId`, `occurredAt`, `traceId`, `correlationId` and `data`.
- Event version starts at `1`. Preserve trace/correlation values when present;
  absence must not break order creation.
- Keep outbox rows append-only. Do not add application code that publishes to
  Kafka directly or marks rows as published; Debezium owns CDC delivery.

## Debezium And Kafka

- Keep the connector definition under `infra/debezium/` and make it
  environment-neutral for the local Compose network.
- Capture only the Order Service outbox table from the `orders` database.
- Route order events to the stable topic `ecommerce.order.events`.
- Kafka values must retain the versioned event envelope, including event ID and
  aggregate ID. Configure keys so events for one order remain ordered.
- Provide an idempotent local connector-registration helper and a smoke-test
  procedure. Do not put credentials or real secrets in the repository.

## Query And Authorization

- Customers can list and inspect only orders whose customer ID matches their
  JWT subject.
- `OPS_ADMIN` can inspect an order by ID for support.
- Responses include item snapshots, totals, current status and ordered status
  history. Use bounded pagination for customer lists.
- Avoid exposing internal outbox rows, persistence internals or arbitrary
  customer lookup through customer endpoints.

## Non-Goals

- Do not implement payment consumption or payment-result transitions; that is
  Sprint 4.
- Do not implement audit persistence or notifications; that is Sprint 5.
- Do not add inventory, cart, shipping, tax, discount or refund behavior.
- Do not add a shared business database or shared JPA domain module.
- Do not add broad Testcontainers/E2E infrastructure from Sprint 6.

## Token Discipline

Use this context pack, the current task block and the listed Order Service or
infrastructure dependencies. Do not re-read the source PDF/XLSX unless these
compact requirements are insufficient.
