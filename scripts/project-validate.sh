#!/usr/bin/env bash
set -euo pipefail

MODE="${1:-structure}"

if command -v /usr/libexec/java_home >/dev/null 2>&1; then
  if JAVA21_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null)"; then
    export JAVA_HOME="$JAVA21_HOME"
    export PATH="$JAVA_HOME/bin:$PATH"
  fi
fi

need_file() {
  local file="$1"
  if [ ! -f "$file" ]; then
    echo "Missing required file: $file" >&2
    exit 2
  fi
}

need_dir() {
  local dir="$1"
  if [ ! -d "$dir" ]; then
    echo "Missing required directory: $dir" >&2
    exit 2
  fi
}

need_grep() {
  local pattern="$1"
  local file="$2"
  local message="$3"
  if ! grep -Eq "$pattern" "$file"; then
    echo "$message" >&2
    exit 2
  fi
}

reject_grep() {
  local pattern="$1"
  local file="$2"
  local message="$3"
  if grep -Eq "$pattern" "$file"; then
    echo "$message" >&2
    exit 2
  fi
}

java_major() {
  local version
  version="$(java -version 2>&1 | awk -F '"' '/version/ {print $2; exit}' || true)"
  case "$version" in
    1.*) printf '%s\n' "$version" | cut -d. -f2 ;;
    "") echo 0 ;;
    *) printf '%s\n' "$version" | cut -d. -f1 ;;
  esac
}

mvn_cmd() {
  if command -v mvn >/dev/null 2>&1; then
    mvn "$@"
  else
    echo "Maven is not available on PATH." >&2
    exit 2
  fi
}

run_bounded() {
  local duration="$1"
  shift
  if ! command -v timeout >/dev/null 2>&1; then
    echo "The timeout command is required for bounded release validation." >&2
    exit 2
  fi
  timeout "$duration" "$@"
}

prepare_release_docker() {
  local running_services

  if [ "${RELEASE_CHECK_STOP_COMPOSE:-1}" = "0" ]; then
    echo "Keeping the project Compose stack running because RELEASE_CHECK_STOP_COMPOSE=0."
    return 0
  fi

  running_services="$(docker compose ps --status running -q 2>/dev/null || true)"
  if [ -z "$running_services" ]; then
    return 0
  fi

  echo "Stopping project Compose services before Testcontainers validation to free local Docker resources."
  echo "Containers, volumes and application data will be preserved."
  run_bounded 5m docker compose stop
}

case "$MODE" in
  structure)
    need_file AGENTS.md
    need_file DESIGN.md
    need_file STATE.md
    need_file docs/night-runner/task-queue.md
    need_file pom.xml
    need_dir services/gateway-service
    need_dir services/catalog-service
    need_dir services/order-service
    need_dir services/payment-service
    need_dir services/audit-notification-service
    ;;
  compose-config)
    need_file docker-compose.yml
    if docker compose version >/dev/null 2>&1; then
      docker compose config >/dev/null
    else
      echo "Docker Compose is not available." >&2
      exit 2
    fi
    ;;
  keycloak-config)
    need_dir infra/keycloak
    if ! grep -R "CUSTOMER" infra/keycloak >/dev/null 2>&1; then
      echo "Keycloak config does not define CUSTOMER." >&2
      exit 2
    fi
    if ! grep -R "CATALOG_ADMIN" infra/keycloak >/dev/null 2>&1; then
      echo "Keycloak config does not define CATALOG_ADMIN." >&2
      exit 2
    fi
    if ! grep -R "OPS_ADMIN" infra/keycloak >/dev/null 2>&1; then
      echo "Keycloak config does not define OPS_ADMIN." >&2
      exit 2
    fi
    ;;
  maven-validate)
    need_file pom.xml
    mvn_cmd -q -DskipTests validate
    ;;
  java21)
    major="$(java_major)"
    if [ "$major" -lt 21 ]; then
      echo "Java 21 is required for compile/test validation. Current Java major: $major" >&2
      exit 2
    fi
    ;;
  catalog-validate)
    need_file pom.xml
    need_dir services/catalog-service
    mvn_cmd -q -pl services/catalog-service -am -DskipTests validate
    ;;
  catalog-test)
    need_file pom.xml
    need_dir services/catalog-service
    mvn_cmd -q -pl services/catalog-service -am test
    ;;
  order-validate)
    need_file pom.xml
    need_dir services/order-service
    mvn_cmd -q -pl services/order-service -am -DskipTests validate
    ;;
  order-test)
    need_file pom.xml
    need_dir services/order-service
    mvn_cmd -q -pl services/order-service -am test
    ;;
  payment-validate)
    need_file pom.xml
    need_dir services/payment-service
    mvn_cmd -q -pl services/payment-service -am -DskipTests validate
    ;;
  payment-test)
    need_file pom.xml
    need_dir services/payment-service
    mvn_cmd -q -pl services/payment-service -am test
    ;;
  audit-notification-validate)
    need_file pom.xml
    need_dir services/audit-notification-service
    mvn_cmd -q -pl services/audit-notification-service -am -DskipTests validate
    ;;
  audit-notification-test)
    need_file pom.xml
    need_dir services/audit-notification-service
    mvn_cmd -q -pl services/audit-notification-service -am test
    ;;
  observability-test)
    need_file pom.xml
    need_dir services/gateway-service
    need_dir services/catalog-service
    need_dir services/order-service
    need_dir services/payment-service
    need_dir services/audit-notification-service
    # Gateway's existing security tests bind a random local port, which is not
    # available in restricted agent sandboxes. Compile it here; focused tasks
    # should still run any non-socket Gateway tests they add.
    mvn_cmd -q -pl services/gateway-service -am -DskipTests validate
    mvn_cmd -q \
      -pl services/catalog-service,services/order-service,services/payment-service,services/audit-notification-service \
      -am test
    ;;
  integration-test)
    need_file pom.xml
    need_dir services/catalog-service
    need_dir services/order-service
    need_dir services/payment-service
    need_dir services/audit-notification-service
    mvn_cmd -q -Pintegration-tests verify
    ;;
  contract-test)
    need_file pom.xml
    mvn_cmd -q -Pcontract-tests verify
    ;;
  order-cdc-config)
    need_file docker-compose.yml
    need_file infra/debezium/order-outbox-connector.json
    if ! grep -Eq '"connector.class"[[:space:]]*:[[:space:]]*"io.debezium.connector.postgresql.PostgresConnector"' infra/debezium/order-outbox-connector.json; then
      echo "Order outbox connector does not use the PostgreSQL connector." >&2
      exit 2
    fi
    if ! grep -Eq 'ecommerce\.order\.events' infra/debezium/order-outbox-connector.json; then
      echo "Order outbox connector does not define ecommerce.order.events." >&2
      exit 2
    fi
    if command -v jq >/dev/null 2>&1; then
      jq -e '.name and .config["connector.class"] and .config["table.include.list"]' \
        infra/debezium/order-outbox-connector.json >/dev/null
    else
      echo "jq is not available; skipping strict connector JSON parsing." >&2
    fi
    if docker compose version >/dev/null 2>&1; then
      docker compose config >/dev/null
    else
      echo "Docker Compose is not available." >&2
      exit 2
    fi
    ;;
  payment-cdc-config)
    need_file docker-compose.yml
    need_file infra/debezium/payment-outbox-connector.json
    if ! grep -Eq '"connector.class"[[:space:]]*:[[:space:]]*"io.debezium.connector.postgresql.PostgresConnector"' infra/debezium/payment-outbox-connector.json; then
      echo "Payment outbox connector does not use the PostgreSQL connector." >&2
      exit 2
    fi
    if ! grep -Eq 'ecommerce\.payment\.events' infra/debezium/payment-outbox-connector.json; then
      echo "Payment outbox connector does not define ecommerce.payment.events." >&2
      exit 2
    fi
    if command -v jq >/dev/null 2>&1; then
      jq -e '.name and .config["connector.class"] and .config["table.include.list"]' \
        infra/debezium/payment-outbox-connector.json >/dev/null
    else
      echo "jq is not available; skipping strict connector JSON parsing." >&2
    fi
    if docker compose version >/dev/null 2>&1; then
      docker compose config >/dev/null
    else
      echo "Docker Compose is not available." >&2
      exit 2
    fi
    ;;
  observability-config)
    need_file docker-compose.yml
    need_file infra/observability/prometheus.yml
    need_file infra/observability/alert-rules.yml
    if ! grep -Eq 'rule_files:' infra/observability/prometheus.yml; then
      echo "Prometheus config does not load rule files." >&2
      exit 2
    fi
    if ! grep -Eq 'alert:' infra/observability/alert-rules.yml; then
      echo "Prometheus alert rules do not define any alerts." >&2
      exit 2
    fi
    if ! grep -Eiq 'dlq|dead.?letter' infra/observability/alert-rules.yml; then
      echo "Prometheus alert rules do not include a DLQ signal." >&2
      exit 2
    fi
    if docker compose version >/dev/null 2>&1; then
      docker compose config >/dev/null
    else
      echo "Docker Compose is not available." >&2
      exit 2
    fi
    ;;
  full-stack-config)
    need_file docker-compose.yml
    need_file services/gateway-service/Dockerfile
    need_file services/catalog-service/Dockerfile
    need_file services/order-service/Dockerfile
    need_file services/payment-service/Dockerfile
    need_file services/audit-notification-service/Dockerfile
    need_dir infra/mock-payment-provider
    for service in gateway-service catalog-service order-service payment-service audit-notification-service mock-payment-provider; do
      if ! grep -Eq "^[[:space:]]{2}${service}:" docker-compose.yml; then
        echo "Docker Compose does not define $service." >&2
        exit 2
      fi
    done
    if docker compose version >/dev/null 2>&1; then
      docker compose config >/dev/null
    else
      echo "Docker Compose is not available." >&2
      exit 2
    fi
    ;;
  full-stack-smoke)
    need_file scripts/verify-full-stack.sh
    bash scripts/verify-full-stack.sh
    ;;
  e2e-test)
    need_file scripts/run-e2e-tests.sh
    bash scripts/run-e2e-tests.sh
    ;;
  load-smoke)
    need_file scripts/run-load-validation.sh
    bash scripts/run-load-validation.sh --smoke
    ;;
  resilience-smoke)
    need_file scripts/run-resilience-validation.sh
    bash scripts/run-resilience-validation.sh --smoke
    ;;
  api-docs-test)
    need_file pom.xml
    mvn_cmd -q test
    ;;
  docs-check)
    need_file README.md
    need_file DESIGN.md
    need_file docs/architecture.md
    need_file docs/data-model.md
    need_file docs/demo-runbook.md
    for doc in docs/architecture.md docs/data-model.md docs/demo-runbook.md docs/observability-runbook.md; do
      if ! grep -Fq "${doc#docs/}" README.md && ! grep -Fq "$doc" README.md; then
        echo "README does not link $doc." >&2
        exit 2
      fi
    done
    ;;
  ci-config)
    workflow=.github/workflows/ci.yml
    need_file "$workflow"
    need_grep "permissions:" "$workflow" "CI does not declare workflow permissions."
    need_grep "contents:[[:space:]]*read" "$workflow" "CI does not use read-only contents permission."
    need_grep "concurrency:" "$workflow" "CI does not configure concurrency cancellation."
    need_grep "cancel-in-progress:[[:space:]]*true" "$workflow" "CI does not cancel superseded runs."
    need_grep "actions/checkout@v4" "$workflow" "CI does not pin checkout to a major action version."
    need_grep "persist-credentials:[[:space:]]*false" "$workflow" "CI checkout keeps write credentials available."
    need_grep "actions/setup-java@v4" "$workflow" "CI does not pin setup-java to a major action version."
    need_grep "java-version:[[:space:]]*['\"]?21['\"]?" "$workflow" "CI does not select Java 21."
    need_grep "cache:[[:space:]]*maven" "$workflow" "CI does not enable safe Maven dependency caching."
    need_grep "timeout-minutes:" "$workflow" "CI jobs do not define bounded timeouts."
    need_grep "scripts/project-validate\.sh[[:space:]]+api-docs-test" "$workflow" "CI does not run the unit/OpenAPI test suite."
    need_grep "scripts/project-validate\.sh[[:space:]]+contract-test" "$workflow" "CI does not run the event contract suite."
    need_grep "scripts/project-validate\.sh[[:space:]]+integration-test" "$workflow" "CI does not run the opt-in integration suite."
    need_grep "docker compose config" "$workflow" "CI does not validate Docker Compose configuration."
    need_grep "(docker compose build|scripts/project-validate\.sh[[:space:]]+compose-image-build)" "$workflow" "CI does not validate local image builds."
    need_grep "scripts/project-validate\.sh[[:space:]]+(full-stack-smoke|e2e-test)" "$workflow" "CI does not run bounded Docker smoke coverage."
    need_grep "actions/upload-artifact@v4" "$workflow" "CI does not upload diagnostics on failure."
    need_grep "if:[[:space:]]*failure\(\)" "$workflow" "CI diagnostics are not guarded by a failure condition."
    reject_grep "pull_request_target" "$workflow" "CI must not use pull_request_target."
    reject_grep "secrets\." "$workflow" "CI must not reference repository secrets."
    reject_grep "(docker[[:space:]]+login|docker[[:space:]]+push|gh[[:space:]]+release|git[[:space:]]+push|git[[:space:]]+tag)" "$workflow" "CI must not publish images, releases, tags or pushes."
    ;;
  compose-image-build)
    "$0" full-stack-config
    # Compose Bake can put the non-ASCII checkout path into a Buildx session
    # header on macOS. The regular Compose builder avoids that transport bug.
    run_bounded 30m env COMPOSE_BAKE="${COMPOSE_BAKE:-false}" docker compose build \
      gateway-service \
      catalog-service \
      order-service \
      payment-service \
      audit-notification-service \
      mock-payment-provider \
      debezium-connector-bootstrap
    ;;
  night-runner-config)
    need_file docs/night-runner/sprint6-context-pack.md
    need_file docs/night-runner/task-queue.md
    need_file scripts/night-agent-prompt-builder.sh
    need_file scripts/night-agent-runner.sh
    need_file scripts/night-agent-safe-approve.sh
    need_file scripts/night-agent-status.sh
    bash -n scripts/night-agent-prompt-builder.sh
    bash -n scripts/night-agent-runner.sh
    bash -n scripts/night-agent-safe-approve.sh
    bash -n scripts/night-agent-status.sh
    if ! grep -Eq 'task:id=ECOM-040 phase=sprint6 status=(pending|in_progress|done|blocked)' docs/night-runner/task-queue.md; then
      echo "Sprint 6 first task is missing or has an invalid status." >&2
      exit 2
    fi
    scripts/night-agent-status.sh >/dev/null
    ;;
  release-check)
    "$0" java21
    "$0" structure
    "$0" docs-check
    "$0" ci-config
    "$0" compose-config
    "$0" full-stack-config
    run_bounded 20m "$0" api-docs-test
    run_bounded 15m "$0" contract-test
    prepare_release_docker
    run_bounded 30m "$0" integration-test
    run_bounded 30m "$0" compose-image-build
    ;;
  *)
    echo "Unknown validation mode: $MODE" >&2
    exit 2
    ;;
esac
