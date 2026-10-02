#!/usr/bin/env bash
set -euo pipefail

QUEUE_FILE="docs/night-runner/task-queue.md"
TASK_ID=""

usage() {
  cat <<'USAGE'
Usage:
  scripts/night-agent-prompt-builder.sh TASK-ID [--queue PATH]
USAGE
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --queue)
      QUEUE_FILE="${2:?--queue requires a path}"
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      TASK_ID="${1:-}"
      shift
      ;;
  esac
done

if [ -z "$TASK_ID" ]; then
  usage >&2
  exit 2
fi

if [ ! -f "$QUEUE_FILE" ]; then
  echo "Task queue not found: $QUEUE_FILE" >&2
  exit 2
fi

task_block() {
  awk -v id="$TASK_ID" '
    $0 ~ "<!-- task:id=" id " " { print; in_task=1; next }
    in_task { print }
    in_task && /<!-- \/task -->/ { exit }
  ' "$QUEUE_FILE"
}

task_phase() {
  case "$1" in
    ECOM-001|ECOM-002|ECOM-003|ECOM-004|ECOM-005|ECOM-006) echo "sprint1" ;;
    ECOM-007|ECOM-008|ECOM-009) echo "sprint2" ;;
    *) echo "unknown"; return 1 ;;
  esac
}

context_pack_for_task() {
  case "$(task_phase "$1")" in
    sprint1) echo "docs/night-runner/sprint1-context-pack.md" ;;
    sprint2) echo "docs/night-runner/sprint2-context-pack.md" ;;
    *) return 1 ;;
  esac
}

dependency_files_for_task() {
  case "$1" in
    ECOM-001)
      cat <<'EOF'
AGENTS.md
DESIGN.md
STATE.md
EOF
      ;;
    ECOM-002|ECOM-003)
      cat <<'EOF'
pom.xml
README.md
services/gateway-service
services/catalog-service
services/order-service
services/payment-service
services/audit-notification-service
EOF
      ;;
    ECOM-004)
      cat <<'EOF'
services/gateway-service
infra/keycloak
docs/night-runner/sprint1-context-pack.md
EOF
      ;;
    ECOM-005|ECOM-006)
      cat <<'EOF'
services/gateway-service
services/catalog-service
services/order-service
services/payment-service
services/audit-notification-service
infra
EOF
      ;;
    ECOM-007|ECOM-008|ECOM-009)
      cat <<'EOF'
services/catalog-service
docs/night-runner/sprint2-context-pack.md
EOF
      ;;
  esac
}

TASK_BLOCK="$(task_block)"
if [ -z "$TASK_BLOCK" ]; then
  echo "Task not found in queue: $TASK_ID" >&2
  exit 2
fi

CONTEXT_PACK="$(context_pack_for_task "$TASK_ID")"
if [ ! -f "$CONTEXT_PACK" ]; then
  echo "Context pack not found: $CONTEXT_PACK" >&2
  exit 2
fi

VALIDATION="$(printf '%s\n' "$TASK_BLOCK" | awk '
  /^Validation:/ { in_validation=1; next }
  in_validation && /^-/ {
    sub(/^- /, "")
    gsub(/`/, "")
    print
    next
  }
  in_validation && /^$/ { next }
  in_validation && /^[A-Za-z]/ { exit }
')"

ALLOWED_FILES="$(scripts/night-agent-safe-approve.sh allowed-files "$TASK_ID")"
DEPENDENCIES="$(dependency_files_for_task "$TASK_ID" || true)"

cat <<EOF
Project: E-Commerce Microservices Portfolio.

You are running one task under the local night-agent runner.

Execute only current task id: $TASK_ID

Read first, in this order:
1. AGENTS.md
2. STATE.md
3. DESIGN.md
4. $CONTEXT_PACK
5. The current task block below
6. Dependency files/directories listed below only if needed

Token discipline:
- Do not re-read the source PDF or XLSX unless the compact docs are insufficient.
- Do not summarize the whole project back to the user.
- Do not start the next task in this same session.

Current task block:

$TASK_BLOCK

Dependency files/directories to inspect only if needed:
$(if [ -n "$DEPENDENCIES" ]; then printf '%s\n' "$DEPENDENCIES" | sed 's/^/- /'; else echo "- none"; fi)

Allowed files/patterns for this task:
$ALLOWED_FILES

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
$VALIDATION
4. Update STATE.md with what changed, validation run and next task.
5. Update docs/night-runner/handoff.md with current task, files changed if known, tests run, known issues and next task.
6. Stop. Do not begin the next pending task.

Low-token/context-limit rule:
- Stop at a checkpoint instead of stretching into another task.
- Keep the task recoverable through STATE.md and docs/night-runner/handoff.md.

Safety rule:
- Prefer asking for no user input overnight. If something is unsafe, mark blocked instead of guessing.
EOF

