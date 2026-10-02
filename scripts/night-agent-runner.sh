#!/usr/bin/env bash
set -euo pipefail

QUEUE_FILE="docs/night-runner/task-queue.md"
HANDOFF_FILE="docs/night-runner/handoff.md"
RUN_ROOT=".agent-runs"
AGENT_KIND="codex"
AGENT_BIN="${CODEX_BIN:-codex}"
MAX_TASKS=1
MAX_MINUTES=480
ALLOW_DIRTY=0
DRY_RUN=0
AUTO_COMMIT=0
AUTO_WAIT_LIMITS=0
CONTINUE_ON_PASS=0
PHASE_FILTERS=""
LIMIT_WAIT_BUFFER_SECONDS=90
LIMIT_FALLBACK_SLEEP_SECONDS=900

if [ -z "${JAVA_HOME:-}" ] && command -v /usr/libexec/java_home >/dev/null 2>&1; then
  if JAVA21_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null)"; then
    export JAVA_HOME="$JAVA21_HOME"
    export PATH="$JAVA_HOME/bin:$PATH"
  fi
fi

usage() {
  cat <<'USAGE'
Usage:
  scripts/night-agent-runner.sh [options]

Options:
  --agent codex|claude       Agent CLI kind. Default: codex.
  --agent-bin PATH           Agent executable path.
  --allow-dirty              Allow starting with existing dirty Git changes.
  --dry-run                  Print next task, prompt path and agent command only.
  --max-tasks N              Stop after N clean tasks. Default: 1.
  --max-minutes N            Stop after N minutes. Default: 480.
  --continue-on-pass         Continue after a clean task.
  --auto-wait-limits         Wait and retry if the agent hits a usage/session limit.
  --auto-commit              Commit approved task files after validation. Never pushes.
  --phase PHASE              Restrict to one phase, for example sprint1.
  --phases LIST              Restrict to comma-separated phases.
  --queue PATH               Queue file path.
  --handoff PATH             Handoff file path.
  --overnight                sprint1,sprint2; continue; max-tasks 99; max-minutes 28800; auto-wait.
  --help                     Show help.
USAGE
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --agent) AGENT_KIND="${2:?--agent requires a value}"; shift 2 ;;
    --agent-bin) AGENT_BIN="${2:?--agent-bin requires a path}"; shift 2 ;;
    --allow-dirty) ALLOW_DIRTY=1; shift ;;
    --dry-run) DRY_RUN=1; shift ;;
    --max-tasks) MAX_TASKS="${2:?--max-tasks requires a number}"; shift 2 ;;
    --max-minutes) MAX_MINUTES="${2:?--max-minutes requires a number}"; shift 2 ;;
    --continue-on-pass) CONTINUE_ON_PASS=1; shift ;;
    --auto-wait-limits) AUTO_WAIT_LIMITS=1; shift ;;
    --auto-commit) AUTO_COMMIT=1; shift ;;
    --phase) PHASE_FILTERS="${2:?--phase requires a value}"; shift 2 ;;
    --phases) PHASE_FILTERS="${2:?--phases requires a value}"; shift 2 ;;
    --queue) QUEUE_FILE="${2:?--queue requires a path}"; shift 2 ;;
    --handoff) HANDOFF_FILE="${2:?--handoff requires a path}"; shift 2 ;;
    --overnight)
      PHASE_FILTERS="sprint1,sprint2"
      CONTINUE_ON_PASS=1
      MAX_TASKS=99
      MAX_MINUTES=28800
      AUTO_WAIT_LIMITS=1
      shift
      ;;
    --help|-h) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage >&2; exit 2 ;;
  esac
done

case "$AGENT_KIND" in
  codex|claude) ;;
  *) echo "Unknown --agent: $AGENT_KIND" >&2; exit 2 ;;
esac

if [ "$AGENT_KIND" = "claude" ] && [ "${AGENT_BIN}" = "${CODEX_BIN:-codex}" ]; then
  AGENT_BIN="${CLAUDE_BIN:-claude}"
fi

resolve_agent_bin() {
  if command -v "$AGENT_BIN" >/dev/null 2>&1; then
    AGENT_BIN="$(command -v "$AGENT_BIN")"
    return 0
  fi

  if [ "$AGENT_KIND" = "codex" ] && [ "$AGENT_BIN" = "codex" ] && [ -d "$HOME/.vscode/extensions" ]; then
    local candidate
    while IFS= read -r candidate; do
      if [ -x "$candidate" ]; then
        AGENT_BIN="$candidate"
        return 0
      fi
    done < <(find "$HOME/.vscode/extensions" -path '*/bin/*/codex' -type f 2>/dev/null | sort -r)
  fi

  return 1
}

resolve_agent_bin || true

if [ "$DRY_RUN" -ne 1 ] && ! command -v "$AGENT_BIN" >/dev/null 2>&1; then
  echo "Agent CLI not found: $AGENT_BIN" >&2
  exit 2
fi

if [ ! -f "$QUEUE_FILE" ]; then
  echo "Task queue not found: $QUEUE_FILE" >&2
  exit 2
fi

task_phase() {
  case "$1" in
    ECOM-001|ECOM-002|ECOM-003|ECOM-004|ECOM-005|ECOM-006) echo "sprint1" ;;
    ECOM-007|ECOM-008|ECOM-009) echo "sprint2" ;;
    *) echo "unknown" ;;
  esac
}

phase_is_requested() {
  local candidate="$1"
  local raw="${PHASE_FILTERS:-sprint1,sprint2}"
  local old_ifs="$IFS"
  local phase
  IFS=','
  for phase in $raw; do
    if [ "$candidate" = "$phase" ]; then
      IFS="$old_ifs"
      return 0
    fi
  done
  IFS="$old_ifs"
  return 1
}

next_pending_task() {
  local task
  while IFS= read -r task; do
    [ -z "$task" ] && continue
    if phase_is_requested "$(task_phase "$task")"; then
      echo "$task"
      return 0
    fi
  done < <(sed -n 's/.*task:id=\([^ ]*\).*status=pending.*/\1/p' "$QUEUE_FILE")
}

set_task_status() {
  local task_id="$1"
  local status="$2"
  local tmp
  tmp="$(mktemp)"
  awk -v id="$task_id" -v status="$status" '
    /<!-- task:id=/ {
      in_task = ($0 ~ "task:id=" id " ")
      if (in_task) sub(/status=[^ ]+/, "status=" status)
    }
    in_task && /^Status:/ { print "Status: " status; next }
    { print }
    in_task && /<!-- \/task -->/ { in_task=0 }
  ' "$QUEUE_FILE" > "$tmp"
  mv "$tmp" "$QUEUE_FILE"
}

validation_commands() {
  local task_id="$1"
  scripts/night-agent-prompt-builder.sh "$task_id" --queue "$QUEUE_FILE" |
    awk '/^Required workflow:/ { exit } /^scripts\\/project-validate.sh/ { print }'
}

planned_validation_summary() {
  local task_id="$1"
  awk -v id="$task_id" '
    $0 ~ "<!-- task:id=" id " " { in_task=1 }
    in_task && /^Validation:/ { in_validation=1; next }
    in_validation && /^-/ { sub(/^- /, ""); gsub(/`/, ""); print; next }
    in_validation && /^$/ { next }
    in_validation && /^[A-Za-z]/ { exit }
    in_task && /<!-- \/task -->/ { exit }
  ' "$QUEUE_FILE" | paste -sd ';' - | sed 's/;/; /g'
}

git_available() {
  git rev-parse --is-inside-work-tree >/dev/null 2>&1
}

snapshot_files() {
  find . -type f \
    ! -path './.git/*' \
    ! -path './.agent-runs/*' \
    ! -path './.DS_Store' \
    -print | sort | while IFS= read -r file; do
      hash="$(shasum -a 256 "$file" | awk '{print $1}')"
      printf '%s\t%s\n' "$hash" "${file#./}"
    done
}

capture_baseline() {
  local output="$1"
  if git_available; then
    {
      git diff --name-only
      git diff --name-only --cached
      git ls-files --others --exclude-standard
    } | sort -u > "$output"
  else
    snapshot_files > "$output"
  fi
}

capture_changed_files() {
  local baseline="$1"
  local output="$2"
  if git_available; then
    {
      git diff --name-only
      git diff --name-only --cached
      git ls-files --others --exclude-standard
    } | sort -u > "${output}.all"
    comm -13 "$baseline" "${output}.all" > "$output" || cp "${output}.all" "$output"
  else
    local current
    current="$(mktemp)"
    snapshot_files > "$current"
    awk -F '\t' 'NR==FNR { base[$2]=$1; next } !($2 in base) || base[$2] != $1 { print $2 }' "$baseline" "$current" > "$output"
  fi
}

run_validation() {
  local task_id="$1"
  local log_file="$2"
  local command
  awk -v id="$task_id" '
    $0 ~ "<!-- task:id=" id " " { in_task=1 }
    in_task && /^Validation:/ { in_validation=1; next }
    in_validation && /^-/ { sub(/^- /, ""); gsub(/`/, ""); print; next }
    in_validation && /^$/ { next }
    in_validation && /^[A-Za-z]/ { exit }
    in_task && /<!-- \/task -->/ { exit }
  ' "$QUEUE_FILE" | while IFS= read -r command; do
    [ -z "$command" ] && continue
    echo "== validation: $command ==" | tee -a "$log_file"
    bash -lc "$command" 2>&1 | tee -a "$log_file"
  done
}

is_limit_log() {
  local log_file="$1"
  [ -f "$log_file" ] || return 1
  grep -Eiq '(session limit|usage limit|rate limit|try again after|resets[[:space:]]+[0-9]{1,2}:[0-9]{2}|reset at[[:space:]]+[0-9]{1,2}:[0-9]{2})' "$log_file"
}

sleep_for_limit() {
  local log_file="$1"
  local sleep_seconds="$LIMIT_FALLBACK_SLEEP_SECONDS"
  echo "Agent usage/session limit detected. Waiting ${sleep_seconds}s before retry. See $log_file"
  sleep "$sleep_seconds"
}

write_handoff() {
  local current_task="$1"
  local last_completed="$2"
  local status="$3"
  local log_file="$4"
  local files_file="$5"
  local tests="$6"
  local known_issue="$7"
  local next_task="$8"
  local next_display="${next_task:-none}"
  local prompt_file=""

  if [ "$status" = "blocked" ]; then
    next_display="none until current blocked task is resolved"
  elif [ -n "$next_task" ]; then
    prompt_file="${RUN_DIR}/${next_task}.next-prompt.md"
    scripts/night-agent-prompt-builder.sh "$next_task" --queue "$QUEUE_FILE" > "$prompt_file"
  fi

  {
    echo "# Night Agent Handoff"
    echo
    echo "Requested phases: ${PHASE_FILTERS:-sprint1,sprint2}"
    echo "Current task: $current_task"
    echo "Last completed task: ${last_completed:-none}"
    echo "Last status: $status"
    echo "Last log: $log_file"
    echo
    echo "## Files Changed"
    if [ -s "$files_file" ]; then sed 's/^/- /' "$files_file"; else echo "- none"; fi
    echo
    echo "## Tests Run"
    echo "$tests"
    echo
    echo "## Current Status"
    if git_available; then
      echo '```text'
      git status --short
      echo '```'
    else
      echo "Not a git repository; checksum snapshot tracking was used."
    fi
    echo
    echo "## Known Issues"
    echo "${known_issue:-none}"
    echo
    echo "## Next Task"
    echo "$next_display"
    echo
    echo "## Exact Next Agent Prompt"
    if [ -n "$prompt_file" ]; then
      echo '```text'
      cat "$prompt_file"
      echo '```'
    elif [ "$status" = "blocked" ]; then
      echo "No next prompt until the blocked task is resolved."
    else
      echo "No pending task."
    fi
    echo
    echo "## Protected Files Reminder"
    echo
    echo "- Do not edit the source PDF/XLSX."
    echo "- Do not edit LiveAssist-download/."
    echo "- Do not commit or push from inside the agent session."
    echo
    echo "## Recovery Notes"
    echo
    echo "Review the log and changed files. If not using Git, initialize Git before a long retry when possible."
  } > "$HANDOFF_FILE"
}

update_agent_status_block() {
  local task_id="$1"
  local status="$2"
  local log_file="$3"
  local next_task="$4"
  local tmp block
  block="$(mktemp)"
  {
    echo "<!-- night-agent-runner:start -->"
    echo "- Last update: $(date -u '+%Y-%m-%dT%H:%M:%SZ')"
    echo "- Last task: $task_id"
    echo "- Last status: $status"
    echo "- Next task: ${next_task:-none}"
    echo "- Last log: $log_file"
    echo "<!-- night-agent-runner:end -->"
  } > "$block"
  tmp="$(mktemp)"
  awk -v start="<!-- night-agent-runner:start -->" -v end="<!-- night-agent-runner:end -->" -v block="$block" '
    BEGIN { while ((getline line < block) > 0) replacement = replacement line ORS }
    $0 == start { printf "%s", replacement; skipping=1; replaced=1; next }
    skipping && $0 == end { skipping=0; next }
    !skipping { print }
    END { if (!replaced) printf "\n%s", replacement }
  ' AGENTS.md > "$tmp"
  mv "$tmp" AGENTS.md
}

run_agent() {
  local prompt_file="$1"
  local log_file="$2"
  if [ "$AGENT_KIND" = "codex" ]; then
    "$AGENT_BIN" exec -C "$PWD" --skip-git-repo-check --sandbox workspace-write --ask-for-approval never - < "$prompt_file" > "$log_file" 2>&1
  else
    "$AGENT_BIN" --print --permission-mode acceptEdits < "$prompt_file" > "$log_file" 2>&1
  fi
}

if [ "$DRY_RUN" -ne 1 ] && git_available && [ "$ALLOW_DIRTY" -ne 1 ] && [ -n "$(git status --porcelain)" ]; then
  echo "Refusing to start with dirty Git changes. Commit/stash or use --allow-dirty." >&2
  git status --short >&2
  exit 2
fi

start_epoch="$(date +%s)"
deadline_epoch="$((start_epoch + MAX_MINUTES * 60))"
run_date="$(date '+%Y-%m-%d')"
RUN_DIR="${RUN_ROOT}/${run_date}"
mkdir -p "$RUN_DIR"
completed=0
last_completed=""

while [ "$completed" -lt "$MAX_TASKS" ]; do
  now="$(date +%s)"
  if [ "$now" -ge "$deadline_epoch" ]; then
    echo "Stopping: max time reached."
    exit 0
  fi

  task_id="$(next_pending_task || true)"
  if [ -z "$task_id" ]; then
    echo "No pending tasks in requested phases."
    exit 0
  fi

  prompt_file="${RUN_DIR}/${task_id}.prompt.md"
  log_file="${RUN_DIR}/${task_id}.log"
  changed_file="${RUN_DIR}/${task_id}.changed-files.txt"
  baseline_file="${RUN_DIR}/${task_id}.baseline.txt"

  scripts/night-agent-prompt-builder.sh "$task_id" --queue "$QUEUE_FILE" > "$prompt_file"

  if [ "$DRY_RUN" -eq 1 ]; then
    echo "Dry run."
    echo "Next task: $task_id ($(task_phase "$task_id"))"
    echo "Prompt: $prompt_file"
    echo "Agent command:"
    if [ "$AGENT_KIND" = "codex" ]; then
      echo "$AGENT_BIN exec -C \"$PWD\" --skip-git-repo-check --sandbox workspace-write --ask-for-approval never - < $prompt_file"
    else
      echo "$AGENT_BIN --print --permission-mode acceptEdits < $prompt_file"
    fi
    echo "Phases: ${PHASE_FILTERS:-sprint1,sprint2}"
    echo "Auto wait limits: $AUTO_WAIT_LIMITS"
    echo "Auto commit: $AUTO_COMMIT"
    exit 0
  fi

  echo "Starting $task_id"
  echo "Prompt: $prompt_file"
  echo "Log: $log_file"

  capture_baseline "$baseline_file"
  set_task_status "$task_id" "in_progress"

  if ! run_agent "$prompt_file" "$log_file"; then
    capture_changed_files "$baseline_file" "$changed_file"
    if [ "$AUTO_WAIT_LIMITS" -eq 1 ] && is_limit_log "$log_file"; then
      set_task_status "$task_id" "pending"
      tests="not completed; usage/session limit hit; planned: $(planned_validation_summary "$task_id")"
      write_handoff "$task_id" "$last_completed" "pending" "$log_file" "$changed_file" "$tests" "Agent usage/session limit hit. Same task will retry." "$task_id"
      update_agent_status_block "$task_id" "pending" "$log_file" "$task_id"
      sleep_for_limit "$log_file"
      continue
    fi
    set_task_status "$task_id" "blocked"
    tests="not completed; planned: $(planned_validation_summary "$task_id")"
    write_handoff "$task_id" "$last_completed" "blocked" "$log_file" "$changed_file" "$tests" "Agent exited with a non-zero status." ""
    update_agent_status_block "$task_id" "blocked" "$log_file" ""
    echo "Blocked: agent exited non-zero. See $log_file" >&2
    exit 1
  fi

  capture_changed_files "$baseline_file" "$changed_file"

  if ! scripts/night-agent-safe-approve.sh scan-log "$log_file"; then
    set_task_status "$task_id" "blocked"
    tests="not completed; planned: $(planned_validation_summary "$task_id")"
    write_handoff "$task_id" "$last_completed" "blocked" "$log_file" "$changed_file" "$tests" "Dangerous command or live secret pattern found in log." ""
    update_agent_status_block "$task_id" "blocked" "$log_file" ""
    exit 1
  fi

  if ! scripts/night-agent-safe-approve.sh validate-files "$task_id" < "$changed_file"; then
    set_task_status "$task_id" "blocked"
    tests="not completed; planned: $(planned_validation_summary "$task_id")"
    write_handoff "$task_id" "$last_completed" "blocked" "$log_file" "$changed_file" "$tests" "File outside task scope changed." ""
    update_agent_status_block "$task_id" "blocked" "$log_file" ""
    exit 1
  fi

  tests="$(planned_validation_summary "$task_id")"
  if ! run_validation "$task_id" "$log_file"; then
    set_task_status "$task_id" "blocked"
    write_handoff "$task_id" "$last_completed" "blocked" "$log_file" "$changed_file" "$tests" "Validation failed. See log." ""
    update_agent_status_block "$task_id" "blocked" "$log_file" ""
    exit 1
  fi

  set_task_status "$task_id" "done"
  completed="$((completed + 1))"
  last_completed="$task_id"
  next_task="$(next_pending_task || true)"
  capture_changed_files "$baseline_file" "$changed_file"

  if ! scripts/night-agent-safe-approve.sh validate-files "$task_id" < "$changed_file"; then
    set_task_status "$task_id" "blocked"
    write_handoff "$task_id" "$last_completed" "blocked" "$log_file" "$changed_file" "completed validation, but final file-scope check failed" "Final changed files were outside task scope." ""
    update_agent_status_block "$task_id" "blocked" "$log_file" ""
    exit 1
  fi

  if [ "$AUTO_COMMIT" -eq 1 ]; then
    if ! git_available; then
      echo "Auto-commit requested, but this is not a Git repository." | tee -a "$log_file"
      set_task_status "$task_id" "blocked"
      write_handoff "$task_id" "$last_completed" "blocked" "$log_file" "$changed_file" "completed validation, but auto-commit unavailable" "Initialize Git or rerun without --auto-commit." ""
      update_agent_status_block "$task_id" "blocked" "$log_file" ""
      exit 1
    fi
    while IFS= read -r file; do
      [ -n "$file" ] && git add -- "$file"
    done < "$changed_file"
    git commit -m "Complete $task_id" 2>&1 | tee -a "$log_file"
  fi

  write_handoff "$task_id" "$task_id" "done" "$log_file" "$changed_file" "$tests" "none" "$next_task"
  update_agent_status_block "$task_id" "done" "$log_file" "$next_task"
  echo "Completed $task_id"

  if [ "$CONTINUE_ON_PASS" -ne 1 ]; then
    echo "Stopping after one clean pass. Use --continue-on-pass or --overnight to continue."
    exit 0
  fi
done

echo "Stopping: max tasks reached ($MAX_TASKS)."
