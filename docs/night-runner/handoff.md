# Night Agent Handoff

Requested phases: sprint3
Current task: ECOM-021
Last completed task: ECOM-020
Last status: pending
Last log: .agent-runs/2026-10-03/ECOM-021.log

## Files Changed
- none

## Tests Run
not completed; usage/session limit hit; planned: scripts/project-validate.sh order-test; scripts/project-validate.sh order-cdc-config

## Current Status
```text
 M .env.example
 M AGENTS.md
 M STATE.md
 M docs/night-runner/handoff.md
 M docs/night-runner/task-queue.md
 M services/order-service/pom.xml
 M services/order-service/src/main/java/com/kora/ecommerce/order/observability/CorrelationIdFilter.java
 M services/order-service/src/main/resources/application.yml
 M services/order-service/src/test/java/com/kora/ecommerce/order/security/OrderSecurityConfigurationTest.java
?? services/order-service/src/main/java/com/kora/ecommerce/order/api/CreateOrderRequest.java
?? services/order-service/src/main/java/com/kora/ecommerce/order/api/CreateOrderResponse.java
?? services/order-service/src/main/java/com/kora/ecommerce/order/api/CustomerOrderController.java
?? services/order-service/src/main/java/com/kora/ecommerce/order/api/OpsOrderTransitionController.java
?? services/order-service/src/main/java/com/kora/ecommerce/order/api/OrderApiExceptionHandler.java
?? services/order-service/src/main/java/com/kora/ecommerce/order/api/TransitionOrderRequest.java
?? services/order-service/src/main/java/com/kora/ecommerce/order/api/TransitionOrderResponse.java
?? services/order-service/src/main/java/com/kora/ecommerce/order/application/
?? services/order-service/src/main/java/com/kora/ecommerce/order/catalog/
?? services/order-service/src/main/java/com/kora/ecommerce/order/config/
?? services/order-service/src/main/java/com/kora/ecommerce/order/persistence/
?? services/order-service/src/main/resources/db/
?? services/order-service/src/test/java/com/kora/ecommerce/order/api/
?? services/order-service/src/test/java/com/kora/ecommerce/order/application/
?? services/order-service/src/test/java/com/kora/ecommerce/order/catalog/
?? services/order-service/src/test/java/com/kora/ecommerce/order/persistence/
?? services/order-service/src/test/resources/application-test.yml
```

## Known Issues
Agent usage/session limit hit. Same task will retry.

## Next Task
ECOM-021

## Exact Next Agent Prompt
```text
Project: E-Commerce Microservices Portfolio.

You are running one task under the local night-agent runner.

Execute only current task id: ECOM-021

Read first, in this order:
1. AGENTS.md
2. STATE.md
3. DESIGN.md
4. docs/night-runner/sprint3-context-pack.md
5. The current task block below
6. Dependency files/directories listed below only if needed

Token discipline:
- Do not re-read the source PDF or XLSX unless the compact docs are insufficient.
- Do not summarize the whole project back to the user.
- Do not start the next task in this same session.

Current task block:

<!-- task:id=ECOM-021 phase=sprint3 status=pending -->
## ECOM-021: Route Order outbox events through Debezium and Kafka

Status: pending

Story coverage:
- US-14 Debezium CDC to Kafka

Scope:
- Add a versioned Debezium PostgreSQL connector definition that captures only the Order Service outbox table.
- Configure the outbox event router for `ecommerce.order.events`, keyed by aggregate ID, while preserving the complete versioned event envelope.
- Add environment placeholders required for local logical replication without checking in real credentials.
- Add an idempotent connector registration helper and concise local runbook with connector/topic inspection commands.
- Add a bounded smoke helper that can insert or create a test outbox event and verify event ID/aggregate ID on the expected Kafka topic without deleting existing data.
- Validate JSON/config structure and Docker Compose wiring; do not implement an application-side Kafka publisher.

Allowed files:
- `infra/debezium/**`
- `infra/README.md`
- `docker-compose.yml`
- `.env.example`
- `scripts/register-order-outbox-connector.sh`
- `scripts/verify-order-cdc.sh`
- `services/order-service/**`
- `docs/**`
- `README.md`
- `STATE.md`

Validation:
- `scripts/project-validate.sh order-test`
- `scripts/project-validate.sh order-cdc-config`
<!-- /task -->

Dependency files/directories to inspect only if needed:
- docker-compose.yml
- .env.example
- infra/README.md
- infra/postgres/init/01-create-databases.sql
- services/order-service/src/main/resources/db/migration
- docs/night-runner/sprint3-context-pack.md

Allowed files/patterns for this task:
infra/debezium/**
infra/README.md
docker-compose.yml
.env.example
scripts/register-order-outbox-connector.sh
scripts/verify-order-cdc.sh
services/order-service/**
docs/**
README.md
STATE.md

Hard rules:
- Do not ask the user for input during the overnight run.
- If a required decision is unsafe or outside this task, stop cleanly and mark blocked in STATE.md and docs/night-runner/handoff.md.
- Do not edit ECommerce_Microservices_Architecture_Package_v0.1.pdf.
- Do not edit ECommerce_User_Stories_6_Sprints.xlsx.
- Do not edit LiveAssist-download/.
- Do not commit or push.
- Do not add real secrets, tokens, production credentials or real payment provider calls.
- Keep Java 21 as the project target. If local Java is not 21 and validation is blocked by that, record it clearly.

Required workflow:
1. Inspect relevant files before editing.
2. Make only scoped changes.
3. Run validation for this task:
scripts/project-validate.sh order-test
scripts/project-validate.sh order-cdc-config
4. Update STATE.md with what changed, validation run and next task.
5. Update docs/night-runner/handoff.md with current task, files changed if known, tests run, known issues and next task.
6. Stop. Do not begin the next pending task.

Low-token/context-limit rule:
- Stop at a checkpoint instead of stretching into another task.
- Keep the task recoverable through STATE.md and docs/night-runner/handoff.md.

Safety rule:
- Prefer asking for no user input overnight. If something is unsafe, mark blocked instead of guessing.
```

## Protected Files Reminder

- Do not edit the source PDF/XLSX.
- Do not edit LiveAssist-download/.
- Do not commit or push from inside the agent session.

## Recovery Notes

Review the log and changed files. If not using Git, initialize Git before a long retry when possible.
