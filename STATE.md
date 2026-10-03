# Project State

Last updated: 2026-10-03

## Current Status

ECOM-001 is complete. The repository now has a Java 21 / Spring Boot 3.x Maven
monorepo scaffold with five independent service modules:

- `services/gateway-service`
- `services/catalog-service`
- `services/order-service`
- `services/payment-service`
- `services/audit-notification-service`

Each service has its own Maven module, Spring Boot application class and
actuator-ready `application.yml`. No shared business domain or shared database
model was introduced.

Prepared by this setup:

- Compact architecture baseline: `DESIGN.md`
- Agent operating guide: `AGENTS.md`
- Night runner queue: `docs/night-runner/task-queue.md`
- Night runner handoff: `docs/night-runner/handoff.md`
- Night runner scripts under `scripts/`
- Root Maven parent: `pom.xml`
- Developer setup notes: `README.md` and `docs/developer-setup.md`

## Active Delivery Target

Night run target:

1. Sprint 1 completely.
2. The first practical slice of Sprint 2: catalog schema, admin API and customer
   browse/detail API.

Excluded from this first night unless explicitly added later:

- Redis/Redisson cache stampede implementation.
- Spring Batch supplier import.
- Order, payment, audit, notification and E2E flows.
- CI release pipeline.

## Environment Notes

- Maven is available on this machine.
- Docker is available on this machine.
- Local Java currently appears to be Java 17, but the project target is Java 21.
- The runner does not require a Git repository, but Git is recommended before
  long overnight work because it improves rollback and changed-file tracking.

## Latest Validation

ECOM-001 validation passed:

```bash
scripts/project-validate.sh structure
```

Maven compile/validate was not required for ECOM-001. Keep using Java 21 for
future Maven compile/test validation.

## Next Command

Continue with the next queued task only in a new agent session:

```bash
scripts/night-agent-runner.sh --agent codex --overnight --max-minutes 28800
```

Next task: ECOM-002.
