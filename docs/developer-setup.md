# Developer Setup

## Toolchain

Use Java 21 for builds and runtime. The Maven parent config targets Java 21 even
if the local shell points at an older JDK.

Check local versions:

```bash
java -version
mvn -version
```

## Project Layout

The root `pom.xml` is an aggregator and dependency-management parent. Service
modules live under `services/` and are designed to produce independent Spring
Boot applications.

Current modules:

- `services/gateway-service`
- `services/catalog-service`
- `services/order-service`
- `services/payment-service`
- `services/audit-notification-service`

Shared business-domain libraries are intentionally absent. Prefer service-local
domain code; the existing `libs/security-support` module is limited to common
resource-server role mapping.

## Validation

Run the structure validation after scaffold changes:

```bash
scripts/project-validate.sh structure
```

Run Maven validation when Java 21 is available:

```bash
mvn -DskipTests validate
```

If Maven validation fails because the active JDK is older than Java 21, switch
`JAVA_HOME` to a Java 21 installation and rerun.

## One-Command Local Stack

For the complete local platform, copy the placeholder environment file if you
need overrides, then start Compose:

```bash
cp .env.example .env
docker compose up --build
```

This starts infrastructure, Keycloak, Prometheus, Jaeger, Gateway, Catalog,
Order, Payment, Audit Notification, the local mock payment provider and the
Debezium connector bootstrap service. The bootstrap is idempotent and registers
both outbox connectors after the dependent services are ready.

From another shell, verify the foreground stack without deleting volumes or
application data:

```bash
scripts/verify-full-stack.sh --skip-up
```

For a CI-style local smoke run, use:

```bash
scripts/project-validate.sh full-stack-smoke
```

That command runs `scripts/verify-full-stack.sh`, which starts the same stack in
detached mode if needed, builds local images when they are missing and waits
with a bounded deadline. Add `--rebuild` when running the script directly if
you want to force a fresh local image rebuild.

Run the opt-in PostgreSQL Testcontainers integration suites when Docker is
available:

```bash
scripts/project-validate.sh integration-test
```

Run the authenticated full-stack purchase-flow E2E harness when you want to
exercise Keycloak, Gateway, Debezium, Kafka, Payment, Audit Notification and the
mock provider together:

```bash
scripts/project-validate.sh e2e-test
```

See [integration-tests.md](integration-tests.md) for direct Maven commands,
E2E script options, Docker prerequisite notes and current coverage.
