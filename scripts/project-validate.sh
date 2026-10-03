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
  *)
    echo "Unknown validation mode: $MODE" >&2
    exit 2
    ;;
esac
