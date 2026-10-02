#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage:
  scripts/night-agent-safe-approve.sh policy
  scripts/night-agent-safe-approve.sh agent-args codex|claude
  scripts/night-agent-safe-approve.sh allowed-files TASK-ID
  scripts/night-agent-safe-approve.sh protected-files
  scripts/night-agent-safe-approve.sh classify-command "COMMAND"
  scripts/night-agent-safe-approve.sh validate-files TASK-ID [FILE...]
  scripts/night-agent-safe-approve.sh scan-log LOGFILE
USAGE
}

always_allowed_patterns() {
  cat <<'EOF'
AGENTS.md
DESIGN.md
STATE.md
README.md
docs/night-runner/**
.agent-runs/**
EOF
}

protected_patterns() {
  cat <<'EOF'
ECommerce_Microservices_Architecture_Package_v0.1.pdf
ECommerce_User_Stories_6_Sprints.xlsx
LiveAssist-download/**
.env
**/.env
**/.env.*
**/application-prod.yml
**/application-prod.yaml
scripts/night-agent-runner.sh
scripts/night-agent-prompt-builder.sh
scripts/night-agent-safe-approve.sh
scripts/night-agent-status.sh
EOF
}

allowed_patterns_for_task() {
  case "${1:-}" in
    ECOM-001)
      cat <<'EOF'
.gitignore
README.md
pom.xml
services/**
libs/**
docs/**
AGENTS.md
DESIGN.md
STATE.md
EOF
      ;;
    ECOM-002)
      cat <<'EOF'
docker-compose.yml
.env.example
infra/**
docs/**
README.md
STATE.md
EOF
      ;;
    ECOM-003)
      cat <<'EOF'
infra/keycloak/**
docker-compose.yml
.env.example
docs/**
README.md
STATE.md
EOF
      ;;
    ECOM-004)
      cat <<'EOF'
services/gateway-service/**
pom.xml
docs/**
README.md
STATE.md
EOF
      ;;
    ECOM-005)
      cat <<'EOF'
services/catalog-service/**
services/order-service/**
services/payment-service/**
services/audit-notification-service/**
libs/**
pom.xml
docs/**
README.md
STATE.md
EOF
      ;;
    ECOM-006)
      cat <<'EOF'
services/**
infra/observability/**
docker-compose.yml
pom.xml
docs/**
README.md
STATE.md
EOF
      ;;
    ECOM-007|ECOM-008|ECOM-009)
      cat <<'EOF'
services/catalog-service/**
pom.xml
docs/**
README.md
STATE.md
EOF
      ;;
    *)
      return 1
      ;;
  esac
}

normalize_path() {
  local path="$1"
  path="${path#./}"
  printf '%s\n' "$path"
}

matches_pattern() {
  local file="$1"
  local pattern="$2"
  pattern="${pattern#./}"
  case "$pattern" in
    **/*)
      local suffix="${pattern#**/}"
      case "$file" in *"/$suffix"|"$suffix") return 0 ;; esac
      ;;
    */**)
      local prefix="${pattern%/**}"
      case "$file" in "$prefix"|"$prefix"/*) return 0 ;; esac
      ;;
    *)
      case "$file" in $pattern) return 0 ;; esac
      ;;
  esac
  return 1
}

is_match_in_list() {
  local file="$1"
  local pattern
  while IFS= read -r pattern; do
    [ -z "$pattern" ] && continue
    if matches_pattern "$file" "$pattern"; then
      return 0
    fi
  done
  return 1
}

is_allowed_file() {
  local task_id="$1"
  local file
  file="$(normalize_path "$2")"

  if always_allowed_patterns | is_match_in_list "$file"; then
    return 0
  fi

  if allowed_patterns_for_task "$task_id" | is_match_in_list "$file"; then
    return 0
  fi

  return 1
}

policy() {
  cat <<'EOF'
Night agent policy

Allowed:
- Read, search and list files.
- Edit only files allowed by the active task.
- Run existing validation commands from the task block.
- Run Maven validation, Docker Compose config validation and read-only git diff/status.

Never allowed:
- Editing source PDF/XLSX files.
- Editing LiveAssist-download reference files.
- Printing or committing secrets.
- git push, git reset --hard, git clean -fd.
- rm -rf.
- Installing dependencies unless a task explicitly scopes it.
- Real production credentials, tokens or payment provider calls.

The agent must not ask the user questions during overnight runs. If unsafe, mark blocked.
EOF
}

agent_args() {
  case "${1:-}" in
    codex)
      cat <<'EOF'
exec
-C
.
--skip-git-repo-check
--sandbox
workspace-write
--ask-for-approval
never
-
EOF
      ;;
    claude)
      cat <<'EOF'
--print
--permission-mode
acceptEdits
--allowedTools
Read,LS,Glob,Grep,Edit,Write,Bash(git status),Bash(git diff),Bash(git diff --check),Bash(mvn *),Bash(docker compose config),Bash(scripts/project-validate.sh *),Bash(rg *),Bash(find *),Bash(ls *),Bash(cat *),Bash(sed *),Bash(grep *)
--disallowedTools
Bash(rm *),Bash(git push*),Bash(git reset*),Bash(git clean*),Bash(curl *),Bash(wget *),Bash(open *)
EOF
      ;;
    *)
      echo "Unknown agent kind: ${1:-}" >&2
      exit 2
      ;;
  esac
}

classify_command() {
  local command="${1:-}"
  case "$command" in
    *"rm -rf"*|*"git push"*|*"git reset --hard"*|*"git clean -fd"*|*"curl "*|*"wget "*|*"open "*)
      echo "deny"
      return 2
      ;;
    git\ status*|git\ diff*|mvn\ *|docker\ compose\ config|scripts/project-validate.sh\ *|ls*|cat*|sed*|grep*|rg*|find*)
      echo "allow"
      ;;
    *)
      echo "review"
      return 1
      ;;
  esac
}

validate_files() {
  local task_id="$1"
  shift || true
  local failed=0
  local file

  if [ "$#" -eq 0 ]; then
    while IFS= read -r file; do
      [ -n "$file" ] || continue
      set -- "$@" "$file"
    done
  fi

  for file in "$@"; do
    [ -n "$file" ] || continue
    file="$(normalize_path "$file")"
    if protected_patterns | is_match_in_list "$file"; then
      if ! is_allowed_file "$task_id" "$file"; then
        echo "DENY protected file changed outside task scope: $file" >&2
        failed=1
        continue
      fi
    fi
    if ! is_allowed_file "$task_id" "$file"; then
      echo "DENY file changed outside task scope: $file" >&2
      failed=1
    fi
  done

  if [ "$failed" -ne 0 ]; then
    return 2
  fi
}

scan_log() {
  local logfile="$1"
  [ -f "$logfile" ] || return 0

  if grep -E '(^|[[:space:]])(rm -rf|git push|git reset --hard|git clean -fd)([[:space:]]|$)' "$logfile" >/dev/null; then
    echo "Potential dangerous command found in log: $logfile" >&2
    return 2
  fi

  if grep -Eiq '(AKIA[0-9A-Z]{16}|sk-[A-Za-z0-9_-]{20,}|xox[baprs]-|Authorization:[[:space:]]*Bearer[[:space:]]+[A-Za-z0-9._-]{20,})' "$logfile"; then
    echo "Potential live secret found in log: $logfile" >&2
    return 2
  fi
}

main() {
  local command="${1:-help}"
  shift || true
  case "$command" in
    policy) policy ;;
    agent-args) agent_args "${1:?agent kind is required}" ;;
    allowed-files) allowed_patterns_for_task "${1:?TASK-ID is required}" ;;
    protected-files) protected_patterns ;;
    classify-command) classify_command "$*" ;;
    validate-files) validate_files "${1:?TASK-ID is required}" "${@:2}" ;;
    scan-log) scan_log "${1:?LOGFILE is required}" ;;
    help|--help|-h) usage ;;
    *) usage >&2; exit 2 ;;
  esac
}

main "$@"

