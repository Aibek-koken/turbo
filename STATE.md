# Project State

Last updated: 2026-10-02

## Current Status

The repository currently contains source planning documents and a reference
runner from another project. The e-commerce source code has not been scaffolded
yet.

Prepared by this setup:

- Compact architecture baseline: `DESIGN.md`
- Agent operating guide: `AGENTS.md`
- Night runner queue: `docs/night-runner/task-queue.md`
- Night runner handoff: `docs/night-runner/handoff.md`
- Night runner scripts under `scripts/`

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

## Next Command

Check the queue and dry-run the first task:

```bash
scripts/night-agent-status.sh
scripts/night-agent-runner.sh --agent codex --dry-run
```

Start overnight work after reviewing the dry-run:

```bash
scripts/night-agent-runner.sh --agent codex --overnight --max-minutes 28800
```

