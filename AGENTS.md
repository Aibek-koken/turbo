# Agent Guide

This repository is being prepared as an e-commerce microservices portfolio project.
The authoritative project inputs are:

- `ECommerce_Microservices_Architecture_Package_v0.1.pdf`
- `ECommerce_User_Stories_6_Sprints.xlsx`
- `DESIGN.md`
- `STATE.md`
- `docs/night-runner/task-queue.md`

## Read Order

Night agents must read only the compact context needed for the active task:

1. `AGENTS.md`
2. `STATE.md`
3. `DESIGN.md`
4. The phase context pack named by `scripts/night-agent-prompt-builder.sh`
5. The current task block from `docs/night-runner/task-queue.md`
6. Existing source files listed as task dependencies

Do not re-read the PDF or XLSX during every task unless the compact docs are
insufficient. They are source documents, not working memory.

## Project Direction

- Java 21 and Spring Boot 3.x.
- Monorepo, with independently deployable service boundaries.
- Services: gateway, catalog, order, payment, audit-notification.
- Infrastructure: PostgreSQL, Redis, Kafka, Debezium, MongoDB, Keycloak,
  Prometheus and Jaeger or Zipkin.
- Core patterns: service-owned data, Gateway JWT validation, service-level RBAC,
  Transactional Outbox, Debezium CDC, idempotent Kafka consumers, Redis
  cache-aside with Redisson locking, OpenTelemetry tracing.

## Night Runner Rules

- Execute one task per agent session.
- Do not start the next task inside the same session.
- Do not ask the user questions during overnight work.
- If a decision is unsafe or outside scope, stop and mark the task blocked.
- Update `STATE.md` before stopping.
- Update `docs/night-runner/handoff.md` before stopping.
- Keep task changes inside the allowed files/patterns.
- Do not edit the source PDF/XLSX.
- Do not edit `LiveAssist-download/`; it is only a reference implementation for
  the runner workflow.
- Do not commit or push from inside the agent session. The runner owns optional
  auto-commit, and push is always manual.

## Managed Status

<!-- night-agent-runner:start -->
- Last update: 2026-10-05T20:12:00Z
- Last task: ECOM-039
- Last status: done
- Next task: none
- Last log: .agent-runs/2026-10-05/ECOM-039.log
<!-- night-agent-runner:end -->
