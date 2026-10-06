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
    need_file .github/workflows/ci.yml
    if ! grep -Eq "java-version:[[:space:]]*['\"]?21" .github/workflows/ci.yml; then
      echo "CI does not select Java 21." >&2
      exit 2
    fi
    if ! grep -Eq '(mvn|mvnw).*(test|verify)' .github/workflows/ci.yml; then
      echo "CI does not run Maven tests or verification." >&2
      exit 2
    fi
    if ! grep -Eq 'docker compose config' .github/workflows/ci.yml; then
      echo "CI does not validate Docker Compose configuration." >&2
      exit 2
    fi
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
    if ! grep -Eq 'task:id=ECOM-040 phase=sprint6 status=pending' docs/night-runner/task-queue.md; then
      echo "Sprint 6 first task is not pending in the queue." >&2
      exit 2
    fi
    scripts/night-agent-prompt-builder.sh ECOM-040 >/dev/null
    ;;
  release-check)
    "$0" structure
    "$0" compose-config
    "$0" full-stack-config
    "$0" docs-check
    "$0" ci-config
    mvn_cmd -q test
    ;;
  *)
    echo "Unknown validation mode: $MODE" >&2
    exit 2
    ;;
esac
