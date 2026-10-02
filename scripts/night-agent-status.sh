#!/usr/bin/env bash
set -euo pipefail

QUEUE_FILE="${1:-docs/night-runner/task-queue.md}"

task_phase() {
  case "$1" in
    ECOM-001|ECOM-002|ECOM-003|ECOM-004|ECOM-005|ECOM-006) echo "sprint1" ;;
    ECOM-007|ECOM-008|ECOM-009) echo "sprint2" ;;
    *) echo "unknown" ;;
  esac
}

echo "== E-Commerce night task queue =="
if [ -f "$QUEUE_FILE" ]; then
  sed -n 's/.*task:id=\([^ ]*\).*status=\([^ ]*\).*/\1 \2/p' "$QUEUE_FILE" | while read -r task status; do
    printf '%-9s %-8s %s\n' "$task" "$(task_phase "$task")" "$status"
  done
else
  echo "Missing: $QUEUE_FILE"
fi

echo
echo "== Pending runnable phases =="
if [ -f "$QUEUE_FILE" ]; then
  phases="$(sed -n 's/.*task:id=\([^ ]*\).*status=pending.*/\1/p' "$QUEUE_FILE" | while read -r task; do task_phase "$task"; done | sort -u)"
  [ -n "$phases" ] && printf '%s\n' "$phases" || echo "None."
else
  echo "Missing: $QUEUE_FILE"
fi

echo
echo "== Environment =="
if command -v /usr/libexec/java_home >/dev/null 2>&1; then
  java21_home="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
  [ -n "$java21_home" ] && echo "Java 21 home: $java21_home"
fi
if command -v java >/dev/null 2>&1; then
  java -version 2>&1 | head -1
else
  echo "Java: missing"
fi
if command -v mvn >/dev/null 2>&1; then
  mvn -version | head -1
else
  echo "Maven: missing"
fi
if command -v docker >/dev/null 2>&1; then
  docker --version
else
  echo "Docker: missing"
fi
if command -v codex >/dev/null 2>&1; then
  echo "Codex CLI: $(command -v codex)"
else
  codex_from_vscode="$(find "$HOME/.vscode/extensions" -path '*/bin/*/codex' -type f 2>/dev/null | sort -r | head -n 1 || true)"
  [ -n "$codex_from_vscode" ] && echo "Codex CLI: $codex_from_vscode"
  [ -z "$codex_from_vscode" ] && echo "Codex CLI: missing"
fi

echo
echo "== Last run log =="
last_log="$(find .agent-runs -type f -name '*.log' 2>/dev/null | sort | tail -n 1 || true)"
[ -n "$last_log" ] && echo "$last_log" || echo "No logs found."

echo
echo "== Git status =="
if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  git status --short
else
  echo "Not a git repository. Runner will use checksum snapshots."
fi

echo
echo "== Suggested commands =="
echo "scripts/night-agent-runner.sh --agent codex --dry-run"
echo "scripts/night-agent-runner.sh --agent codex --overnight --max-minutes 28800"
