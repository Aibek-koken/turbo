# Night Agent Handoff

Requested phases: sprint3
Current task: queue preparation
Last completed task: ECOM-015
Last status: ready
Last log: none; Sprint 3 has not started

## Files Changed
- Sprint 3 context pack and ECOM-016 through ECOM-022 queue entries
- Runner phase, prompt dependency and file-scope mappings
- Order Service and Debezium validation modes
- Order Service Mockito configuration for this machine's Java 21 runtime
- Project state and design delivery order

## Tests Run
Runner syntax, all seven prompt builds, allowed-file scopes and Sprint 3 dry
run passed. `scripts/project-validate.sh order-test` passed on Java 21 after
selecting Mockito's subclass mock maker for this runtime.

## Current Status
Sprint 2 is complete and committed. Sprint 3 is prepared but no Sprint 3
implementation task has run yet.

## Known Issues
none

## Next Task
ECOM-016

## Exact Next Agent Prompt
Generate it with:

```bash
scripts/night-agent-prompt-builder.sh ECOM-016
```

## Protected Files Reminder

- Do not edit the source PDF/XLSX.
- Do not edit LiveAssist-download/.
- Do not commit or push from inside the agent session.

## Recovery Notes

Commit the queue preparation before starting because the runner rejects a dirty
working tree. Then run the Sprint 3 dry run and overnight command from `STATE.md`.
