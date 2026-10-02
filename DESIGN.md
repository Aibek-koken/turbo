# Design Baseline

This is the compact architecture baseline for the e-commerce microservices
project. It keeps future agent sessions from re-reading the PDF every time.

## Source Scope

The source architecture defines a portfolio-grade e-commerce system focused on
distributed-system behavior, not a simple CRUD app.

Required stack:

- Java 21
- Spring Boot 3.x
- Spring Cloud Gateway
- Keycloak with OAuth2/OIDC and JWT validation
- Service-level RBAC
- Actuator, Micrometer Tracing, OpenTelemetry
- Prometheus metrics
- Jaeger or Zipkin traces
- Docker Compose for local infrastructure
- PostgreSQL and Flyway
- Redis cache-aside and Redisson locking
- Spring Batch supplier CSV import
- Kafka and Debezium
- Transactional Outbox in Order Service
- Payment idempotency with `processed_events`
- Mock external payment provider via Spring `RestClient`
- MongoDB audit store
- Email and push notification adapters

## Service Boundaries

Gateway:
Entry point, route matching, JWT validation, request logging and trace
propagation. It has no business database.

Catalog Service:
Products, categories, attributes, supplier CSV import, JPA reads, Redis cache.
Owns catalog PostgreSQL schema and Redis keys.

Order Service:
Order aggregate, order items, state machine, order history and outbox table.
Owns order PostgreSQL schema.

Payment Service:
Consumes order events, performs a mock payment attempt, stores payment records,
deduplicates events through `processed_events`, emits payment result events.
Owns payment PostgreSQL schema.

Audit Notification Service:
Consumes business events, stores flexible audit documents in MongoDB, triggers
mock email and push notifications.

## V1 State Model

Keep order lifecycle payment-focused:

- `CREATED`
- `PAYMENT_PENDING`
- `PAID`
- `PAYMENT_FAILED`
- `CANCELLED`

Do not add inventory, shipping, cart, recommendation or search services in the
first milestone.

## Event Envelope

Use a versioned JSON envelope:

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

Initial events:

- `OrderCreatedEvent`
- `PaymentSucceededEvent`
- `PaymentFailedEvent`

## Implementation Order

1. Repository and service skeleton.
2. Local infrastructure compose.
3. Keycloak realm, clients and roles.
4. Gateway routes and JWT validation.
5. Service-level RBAC.
6. Observability baseline.
7. Catalog schema and Flyway.
8. Catalog admin APIs.
9. Customer browse/detail APIs.

Sprint 2 items after this slice are Redis/Redisson and Spring Batch.

## Current Constraints

The current machine reports Java 17, while the project requires Java 21. Agents
must keep Java 21 as the target. If compile/test validation fails only because
Java 21 is unavailable, record that clearly in `STATE.md` and the handoff.

