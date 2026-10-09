#!/usr/bin/env bash
set -euo pipefail

START_STACK=true
CLEANUP_ON_EXIT=false
BUILD_MODE="${FULL_STACK_SMOKE_BUILD_MODE:-missing}"
TIMEOUT_SECONDS="${FULL_STACK_SMOKE_TIMEOUT_SECONDS:-1200}"
POLL_SECONDS="${FULL_STACK_SMOKE_POLL_SECONDS:-5}"
CURL_TIMEOUT_SECONDS="${FULL_STACK_SMOKE_CURL_TIMEOUT_SECONDS:-5}"

usage() {
  cat <<'USAGE'
Usage: scripts/verify-full-stack.sh [options]

Options:
  --skip-up             Verify the already-running Compose stack.
  --up                 Run docker compose up -d before checks (default).
  --rebuild            Force docker compose up -d --build before checks.
  --no-build           Start Compose without building local images.
  --cleanup            Stop Compose services when the script exits. Does not remove volumes.
  --timeout SECONDS    Override the full smoke deadline.
  --poll SECONDS       Override readiness polling interval.
  -h, --help           Show this help.
USAGE
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --skip-up)
      START_STACK=false
      shift
      ;;
    --up)
      START_STACK=true
      shift
      ;;
    --rebuild)
      BUILD_MODE=always
      shift
      ;;
    --no-build)
      BUILD_MODE=never
      shift
      ;;
    --cleanup)
      CLEANUP_ON_EXIT=true
      shift
      ;;
    --timeout)
      TIMEOUT_SECONDS="$2"
      shift 2
      ;;
    --poll)
      POLL_SECONDS="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

need_command() {
  local command_name="$1"
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "Missing required command: $command_name" >&2
    exit 2
  fi
}

load_env_file() {
  local env_file=".env"
  local line key value

  if [ ! -f "$env_file" ]; then
    return
  fi

  while IFS= read -r line || [ -n "$line" ]; do
    case "$line" in
      ""|\#*) continue ;;
    esac
    key="${line%%=*}"
    value="${line#*=}"
    case "$key" in
      ""|*[!A-Za-z0-9_]*)
        continue
        ;;
    esac
    if [ -z "${!key+x}" ]; then
      case "$value" in
        \"*\") value="${value#\"}"; value="${value%\"}" ;;
        \'*\') value="${value#\'}"; value="${value%\'}" ;;
      esac
      export "$key=$value"
    fi
  done < "$env_file"
}

now_seconds() {
  date +%s
}

time_left() {
  local now
  now="$(now_seconds)"
  echo $((DEADLINE_SECONDS - now))
}

curl_json() {
  curl -fsS --connect-timeout "$CURL_TIMEOUT_SECONDS" --max-time "$CURL_TIMEOUT_SECONDS" "$@"
}

curl_ok() {
  curl -fsS --connect-timeout "$CURL_TIMEOUT_SECONDS" --max-time "$CURL_TIMEOUT_SECONDS" "$1" >/dev/null
}

wait_until() {
  local label="$1"
  shift

  echo "Waiting for ${label}..."
  until "$@"; do
    if [ "$(time_left)" -le 0 ]; then
      echo "Timed out waiting for ${label}." >&2
      print_diagnostics >&2
      exit 1
    fi
    sleep "$POLL_SECONDS"
  done
  echo "OK: ${label}"
}

compose_service_healthy() {
  local service="$1"
  local container_id inspect_json state health exit_code

  container_id="$(docker compose ps -q "$service" 2>/dev/null || true)"
  if [ -z "$container_id" ]; then
    return 1
  fi

  inspect_json="$(docker inspect "$container_id" 2>/dev/null || true)"
  if [ -z "$inspect_json" ]; then
    return 1
  fi

  state="$(printf '%s\n' "$inspect_json" | jq -r '.[0].State.Status // ""')"
  health="$(printf '%s\n' "$inspect_json" | jq -r '.[0].State.Health.Status // "none"')"
  exit_code="$(printf '%s\n' "$inspect_json" | jq -r '.[0].State.ExitCode // 0')"

  if [ "$state" = "exited" ] || [ "$state" = "dead" ]; then
    echo "Service ${service} is ${state} with exit code ${exit_code}." >&2
    return 1
  fi
  if [ "$health" = "unhealthy" ]; then
    echo "Service ${service} is unhealthy." >&2
    return 1
  fi
  if [ "$health" = "healthy" ]; then
    return 0
  fi
  [ "$state" = "running" ] && [ "$health" = "none" ]
}

connector_name() {
  local connector_file="$1"
  jq -r '.name // empty' "$connector_file"
}

connector_running() {
  local connector_name="$1"
  curl_json "$DEBEZIUM_CONNECT_URL/connectors/$connector_name/status" \
    | jq -e '.connector.state == "RUNNING" and (.tasks | length > 0) and all(.tasks[]; .state == "RUNNING")' >/dev/null
}

prometheus_query_ok() {
  curl_json --get --data-urlencode 'query=up' "${PROMETHEUS_URL}/api/v1/query" \
    | jq -e '.status == "success"' >/dev/null
}

jaeger_api_ok() {
  curl_json "${JAEGER_URL}/api/services" | jq -e '.data != null' >/dev/null
}

required_images_available() {
  local image
  for image in \
    ecommerce/gateway-service:local \
    ecommerce/catalog-service:local \
    ecommerce/order-service:local \
    ecommerce/payment-service:local \
    ecommerce/audit-notification-service:local \
    ecommerce/mock-payment-provider:local \
    ecommerce/debezium-connector-bootstrap:local
  do
    if ! docker image inspect "$image" >/dev/null 2>&1; then
      return 1
    fi
  done
}

start_compose_stack() {
  case "$BUILD_MODE" in
    always)
      echo "Starting the full local stack with docker compose up -d --build..."
      docker compose up -d --build
      ;;
    never)
      echo "Starting the full local stack with docker compose up -d..."
      docker compose up -d
      ;;
    missing)
      if required_images_available; then
        echo "Starting the full local stack with existing local images..."
        docker compose up -d
      else
        echo "Local images are missing; starting the full local stack with docker compose up -d --build..."
        docker compose up -d --build
      fi
      ;;
    *)
      echo "Unsupported FULL_STACK_SMOKE_BUILD_MODE: $BUILD_MODE" >&2
      exit 2
      ;;
  esac
}

print_connector_statuses() {
  local connector
  for connector in "$ORDER_CONNECTOR_NAME" "$PAYMENT_CONNECTOR_NAME"; do
    echo "--- connector status: ${connector} ---"
    curl_json "$DEBEZIUM_CONNECT_URL/connectors/$connector/status" | jq . || true
  done
}

print_diagnostics() {
  echo "--- docker compose ps ---"
  docker compose ps || true
  print_connector_statuses || true
  for service in postgres redis kafka debezium-connect debezium-connector-bootstrap mongodb keycloak mock-payment-provider catalog-service order-service payment-service audit-notification-service gateway-service prometheus jaeger; do
    echo "--- logs: ${service} ---"
    docker compose logs --no-color --tail 80 "$service" || true
  done
}

cleanup() {
  if [ "$CLEANUP_ON_EXIT" = true ]; then
    echo "Stopping Compose services because --cleanup was requested. Volumes are left intact."
    docker compose stop || true
  fi
}

need_command docker
need_command curl
need_command jq
load_env_file
trap cleanup EXIT

export COMPOSE_BAKE="${COMPOSE_BAKE:-false}"
export COMPOSE_PARALLEL_LIMIT="${COMPOSE_PARALLEL_LIMIT:-1}"

KEYCLOAK_PORT="${KEYCLOAK_PORT:-8085}"
KEYCLOAK_REALM="${KEYCLOAK_REALM:-ecommerce}"
GATEWAY_SERVICE_HOST_PORT="${GATEWAY_SERVICE_HOST_PORT:-8080}"
CATALOG_SERVICE_HOST_PORT="${CATALOG_SERVICE_HOST_PORT:-8081}"
ORDER_SERVICE_HOST_PORT="${ORDER_SERVICE_HOST_PORT:-8082}"
PAYMENT_SERVICE_HOST_PORT="${PAYMENT_SERVICE_HOST_PORT:-8083}"
AUDIT_NOTIFICATION_SERVICE_HOST_PORT="${AUDIT_NOTIFICATION_SERVICE_HOST_PORT:-8084}"
MOCK_PAYMENT_PROVIDER_HOST_PORT="${MOCK_PAYMENT_PROVIDER_HOST_PORT:-8089}"
DEBEZIUM_CONNECT_PORT="${DEBEZIUM_CONNECT_PORT:-8086}"
PROMETHEUS_PORT="${PROMETHEUS_PORT:-9090}"
JAEGER_UI_PORT="${JAEGER_UI_PORT:-16686}"

KEYCLOAK_URL="http://localhost:${KEYCLOAK_PORT}"
GATEWAY_URL="http://localhost:${GATEWAY_SERVICE_HOST_PORT}"
CATALOG_URL="http://localhost:${CATALOG_SERVICE_HOST_PORT}"
ORDER_URL="http://localhost:${ORDER_SERVICE_HOST_PORT}"
PAYMENT_URL="http://localhost:${PAYMENT_SERVICE_HOST_PORT}"
AUDIT_NOTIFICATION_URL="http://localhost:${AUDIT_NOTIFICATION_SERVICE_HOST_PORT}"
MOCK_PAYMENT_PROVIDER_URL="http://localhost:${MOCK_PAYMENT_PROVIDER_HOST_PORT}"
DEBEZIUM_CONNECT_URL="${DEBEZIUM_CONNECT_URL:-http://localhost:${DEBEZIUM_CONNECT_PORT}}"
PROMETHEUS_URL="http://localhost:${PROMETHEUS_PORT}"
JAEGER_URL="http://localhost:${JAEGER_UI_PORT}"
ORDER_CONNECTOR_FILE="${ORDER_OUTBOX_CONNECTOR_FILE:-infra/debezium/order-outbox-connector.json}"
PAYMENT_CONNECTOR_FILE="${PAYMENT_OUTBOX_CONNECTOR_FILE:-infra/debezium/payment-outbox-connector.json}"
ORDER_CONNECTOR_NAME="$(connector_name "$ORDER_CONNECTOR_FILE")"
PAYMENT_CONNECTOR_NAME="$(connector_name "$PAYMENT_CONNECTOR_FILE")"
DEADLINE_SECONDS=$(( $(now_seconds) + TIMEOUT_SECONDS ))

if [ -z "$ORDER_CONNECTOR_NAME" ] || [ -z "$PAYMENT_CONNECTOR_NAME" ]; then
  echo "Both connector definitions must include a top-level name." >&2
  exit 2
fi

if [ "$START_STACK" = true ]; then
  start_compose_stack
else
  echo "Verifying an already-running Compose stack."
fi

for service in postgres redis kafka debezium-connect mongodb keycloak mock-payment-provider catalog-service order-service payment-service audit-notification-service gateway-service prometheus jaeger; do
  wait_until "Compose service ${service}" compose_service_healthy "$service"
done

wait_until "Keycloak realm ${KEYCLOAK_REALM}" curl_ok "${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/.well-known/openid-configuration"
wait_until "mock payment provider health" curl_ok "${MOCK_PAYMENT_PROVIDER_URL}/actuator/health"
wait_until "Catalog Service readiness" curl_ok "${CATALOG_URL}/actuator/health/readiness"
wait_until "Order Service readiness" curl_ok "${ORDER_URL}/actuator/health/readiness"
wait_until "Payment Service readiness" curl_ok "${PAYMENT_URL}/actuator/health/readiness"
wait_until "Audit Notification Service readiness" curl_ok "${AUDIT_NOTIFICATION_URL}/actuator/health/readiness"
wait_until "Gateway readiness" curl_ok "${GATEWAY_URL}/actuator/health/readiness"
wait_until "Debezium Connect REST API" curl_ok "${DEBEZIUM_CONNECT_URL}/connectors"
wait_until "Debezium connector ${ORDER_CONNECTOR_NAME}" connector_running "$ORDER_CONNECTOR_NAME"
wait_until "Debezium connector ${PAYMENT_CONNECTOR_NAME}" connector_running "$PAYMENT_CONNECTOR_NAME"
wait_until "Prometheus readiness" curl_ok "${PROMETHEUS_URL}/-/ready"
wait_until "Prometheus query API" prometheus_query_ok
wait_until "Jaeger query API" jaeger_api_ok

echo "Full-stack smoke validation passed without deleting Compose volumes or application data."
