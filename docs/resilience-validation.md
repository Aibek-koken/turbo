# Resilience Validation

This project treats restart and replay validation as bounded local evidence,
not as a production disaster-recovery claim. The checked-in scenario is
versioned as `Purchase restart/replay resilience scenario v1` and sends
authenticated Catalog and Order traffic through Gateway while temporarily
interrupting only services in this Compose project.

## Smoke Command

Run the required smoke validation with:

```bash
scripts/project-validate.sh resilience-smoke
```

The wrapper calls:

```bash
scripts/run-resilience-validation.sh --smoke
```

Smoke defaults are deliberately small. The runner starts or reuses the full
local Compose stack with the bounded resource override in
`tests/resilience/docker-compose.resilience.yml`, creates isolated local
Keycloak users, then runs:

- a payment-service restart window around an in-flight approved purchase
- a Kafka interruption window around an in-flight approved purchase
- malformed-message probes that must land in each configured DLT
- duplicate replay of captured `OrderCreated` and `PaymentSucceeded` payloads

The smoke profile uses short fault windows, does not rebuild images when the
needed local images already exist, does not delete volumes, does not reset
databases and does not disrupt unrelated Docker projects.

## Optional Scenarios

The local profile runs the Audit Notification restart scenario as well:

```bash
scripts/run-resilience-validation.sh --profile local
```

You can select scenarios explicitly:

```bash
scripts/run-resilience-validation.sh --smoke --scenario payment-restart
scripts/run-resilience-validation.sh --smoke --scenario kafka-interruption
scripts/run-resilience-validation.sh --smoke --scenario all
scripts/run-resilience-validation.sh --smoke --include-audit-restart
```

Longer fault windows are opt-in:

```bash
scripts/run-resilience-validation.sh --profile extended --allow-extended-faults
RESILIENCE_KAFKA_FAULT_SECONDS=20 scripts/run-resilience-validation.sh --smoke --allow-extended-faults
```

Without explicit opt-in, service restart windows above 10 seconds and Kafka
windows above 15 seconds are rejected. All local fault windows are capped at 60
seconds.

## What The Script Verifies

For each purchase recovery scenario, the runner prints an elapsed timeline and
then checks:

- the customer order eventually reaches `PAID`
- Payment PostgreSQL has one `SUCCEEDED` payment, one provider attempt and one
  terminal outbox row for the order
- the mock provider ledger has exactly one authorization for the order
- Audit MongoDB has exactly one `OrderCreated` and one `PaymentSucceeded` audit
  document
- notification deliveries have exactly one completed `EMAIL` and one completed
  `PUSH` record for each business event
- duplicate Kafka replay does not create another provider attempt, terminal
  outbox row, audit document or completed notification

The DLT probe publishes malformed local-only payloads to
`ecommerce.order.events` and `ecommerce.payment.events`, then requires the
Payment, Order and Audit Notification dead-letter topics to increase. These
messages are intentionally invalid and are only for local retry/DLT boundary
evidence.

## Recovery Expectations

During a service restart scenario, the touched service is stopped with
`docker compose stop` and restored with `docker compose up -d --no-deps`.
During the Kafka scenario, only this project's `kafka` service is stopped and
started. The runner then waits for Kafka clients, Debezium Connect and both
outbox connectors to recover before checking final state.

Expected successful output includes lines like:

```text
[payment-restart +12s] order reached PAID
[payment-restart +13s] payment reached SUCCEEDED with one terminal outbox row
[payment-restart +18s] duplicate replay remained idempotent
Final consistency for payment-restart order ...
```

Generated Keycloak users are deleted at the end. Catalog fixtures, orders,
payments, audit documents and local DLT messages remain in the developer
Compose volumes by design.

## Troubleshooting Output

On failure, the runner prints:

- the run ID, selected profile and scenarios
- `docker compose ps`
- Debezium connector status
- mock provider ledger rows for the generated customer
- Payment PostgreSQL rows for the generated customer
- Audit MongoDB rows for the generated customer
- DLT end offsets
- recent logs for Gateway, Catalog, Order, Payment, Audit Notification, the
  mock provider, Debezium Connect and Kafka

If a fault is interrupted by a timeout or shell exit, the cleanup path attempts
to start any touched `payment-service`, `audit-notification-service` or `kafka`
container before returning control to the developer.
