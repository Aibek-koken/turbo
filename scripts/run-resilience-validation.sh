#!/usr/bin/env bash
set -euo pipefail

PROFILE="${RESILIENCE_PROFILE:-local}"
SCENARIOS="${RESILIENCE_SCENARIOS:-}"
START_STACK="${RESILIENCE_START_STACK:-auto}"
CLEANUP_ON_EXIT=false
BUILD_MODE="${RESILIENCE_STACK_BUILD_MODE:-missing}"
ALLOW_EXTENDED_FAULTS="${RESILIENCE_ALLOW_EXTENDED_FAULTS:-false}"
BASE_FAULT_SECONDS="${RESILIENCE_FAULT_SECONDS:-}"
PAYMENT_FAULT_SECONDS="${RESILIENCE_PAYMENT_FAULT_SECONDS:-}"
AUDIT_FAULT_SECONDS="${RESILIENCE_AUDIT_FAULT_SECONDS:-}"
KAFKA_FAULT_SECONDS="${RESILIENCE_KAFKA_FAULT_SECONDS:-}"
SCENARIO_TIMEOUT_SECONDS="${RESILIENCE_SCENARIO_TIMEOUT_SECONDS:-}"
STACK_TIMEOUT_SECONDS="${RESILIENCE_STACK_TIMEOUT_SECONDS:-1200}"
DLT_TIMEOUT_SECONDS="${RESILIENCE_DLT_TIMEOUT_SECONDS:-120}"
POLL_SECONDS="${RESILIENCE_POLL_SECONDS:-2}"
CURL_TIMEOUT_SECONDS="${RESILIENCE_CURL_TIMEOUT_SECONDS:-120}"
FAULT_STOP_GRACE_SECONDS="${RESILIENCE_FAULT_STOP_GRACE_SECONDS:-5}"
COMPOSE_OVERRIDE_FILE="${RESILIENCE_COMPOSE_OVERRIDE_FILE:-tests/resilience/docker-compose.resilience.yml}"
KEEP_KEYCLOAK_USERS="${RESILIENCE_KEEP_KEYCLOAK_USERS:-false}"

usage() {
  cat <<'USAGE'
Usage: scripts/run-resilience-validation.sh [options]

Runs bounded local restart/replay resilience scenarios against the Compose
stack and verifies final order, payment, audit, provider-attempt, notification
and DLT/replay consistency.

Options:
  --smoke                   Use developer-machine smoke defaults.
  --profile NAME            One of smoke, local or extended (default: local).
  --scenario NAME           payment-restart, audit-restart, kafka-interruption or all.
                            Can be passed more than once.
  --include-audit-restart   Add the Audit Notification restart scenario.
  --fault-seconds SECONDS   Common service restart fault window.
  --payment-fault SECONDS   Payment restart fault window.
  --audit-fault SECONDS     Audit restart fault window.
  --kafka-fault SECONDS     Kafka interruption fault window.
  --allow-extended-faults   Required for the extended profile or long overrides.
  --up                      Start the Compose stack if needed (default).
  --skip-up                 Require an already-running stack.
  --rebuild                 Force docker compose up -d --build.
  --no-build                Start Compose without building local images.
  --cleanup                 Stop Compose services on exit. Volumes are left intact.
  --timeout SECONDS         Per-scenario recovery deadline.
  --stack-timeout SECONDS   Full-stack readiness deadline.
  --poll SECONDS            Readiness polling interval.
  -h, --help                Show this help.

Smoke defaults run payment-service restart and Kafka interruption scenarios
with short fault windows. Longer windows and the extended profile are explicit
opt-in only; this script never removes volumes or resets databases.
USAGE
}

append_scenario() {
  local scenario="$1"

  case ",${SCENARIOS}," in
    *",${scenario},"*) ;;
    *) SCENARIOS="${SCENARIOS:+${SCENARIOS},}${scenario}" ;;
  esac
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --smoke)
      PROFILE=smoke
      shift
      ;;
    --profile)
      PROFILE="$2"
      shift 2
      ;;
    --scenario)
      if [ "$2" = all ]; then
        append_scenario payment-restart
        append_scenario audit-restart
        append_scenario kafka-interruption
      else
        append_scenario "$2"
      fi
      shift 2
      ;;
    --include-audit-restart)
      append_scenario audit-restart
      shift
      ;;
    --fault-seconds)
      BASE_FAULT_SECONDS="$2"
      shift 2
      ;;
    --payment-fault)
      PAYMENT_FAULT_SECONDS="$2"
      shift 2
      ;;
    --audit-fault)
      AUDIT_FAULT_SECONDS="$2"
      shift 2
      ;;
    --kafka-fault)
      KAFKA_FAULT_SECONDS="$2"
      shift 2
      ;;
    --allow-extended-faults)
      ALLOW_EXTENDED_FAULTS=true
      shift
      ;;
    --up)
      START_STACK=true
      shift
      ;;
    --skip-up)
      START_STACK=false
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
      SCENARIO_TIMEOUT_SECONDS="$2"
      shift 2
      ;;
    --stack-timeout)
      STACK_TIMEOUT_SECONDS="$2"
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
    echo "Missing required command: ${command_name}" >&2
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

configure_compose_files() {
  local separator="${COMPOSE_PATH_SEPARATOR:-:}"

  if [ -z "$COMPOSE_OVERRIDE_FILE" ]; then
    return
  fi
  if [ ! -f "$COMPOSE_OVERRIDE_FILE" ]; then
    echo "Missing resilience Compose override: ${COMPOSE_OVERRIDE_FILE}" >&2
    exit 2
  fi
  if [ -n "${COMPOSE_FILE:-}" ]; then
    export COMPOSE_FILE="${COMPOSE_FILE}${separator}${COMPOSE_OVERRIDE_FILE}"
  else
    export COMPOSE_FILE="docker-compose.yml${separator}${COMPOSE_OVERRIDE_FILE}"
  fi
}

is_non_negative_int() {
  case "$1" in
    ''|*[!0-9]*)
      return 1
      ;;
    *)
      return 0
      ;;
  esac
}

is_positive_int() {
  case "$1" in
    ''|*[!0-9]*)
      return 1
      ;;
    0)
      return 1
      ;;
    *)
      return 0
      ;;
  esac
}

apply_profile_defaults() {
  case "$PROFILE" in
    smoke)
      SCENARIOS="${SCENARIOS:-payment-restart,kafka-interruption}"
      BASE_FAULT_SECONDS="${BASE_FAULT_SECONDS:-2}"
      KAFKA_FAULT_SECONDS="${KAFKA_FAULT_SECONDS:-3}"
      SCENARIO_TIMEOUT_SECONDS="${SCENARIO_TIMEOUT_SECONDS:-300}"
      ;;
    local)
      SCENARIOS="${SCENARIOS:-payment-restart,audit-restart,kafka-interruption}"
      BASE_FAULT_SECONDS="${BASE_FAULT_SECONDS:-5}"
      KAFKA_FAULT_SECONDS="${KAFKA_FAULT_SECONDS:-8}"
      SCENARIO_TIMEOUT_SECONDS="${SCENARIO_TIMEOUT_SECONDS:-600}"
      ;;
    extended)
      if [ "$ALLOW_EXTENDED_FAULTS" != true ]; then
        echo "The extended resilience profile requires --allow-extended-faults or RESILIENCE_ALLOW_EXTENDED_FAULTS=true." >&2
        exit 2
      fi
      SCENARIOS="${SCENARIOS:-payment-restart,audit-restart,kafka-interruption}"
      BASE_FAULT_SECONDS="${BASE_FAULT_SECONDS:-15}"
      KAFKA_FAULT_SECONDS="${KAFKA_FAULT_SECONDS:-20}"
      SCENARIO_TIMEOUT_SECONDS="${SCENARIO_TIMEOUT_SECONDS:-900}"
      ;;
    *)
      echo "Unsupported resilience profile: ${PROFILE}" >&2
      exit 2
      ;;
  esac

  PAYMENT_FAULT_SECONDS="${PAYMENT_FAULT_SECONDS:-${BASE_FAULT_SECONDS}}"
  AUDIT_FAULT_SECONDS="${AUDIT_FAULT_SECONDS:-${BASE_FAULT_SECONDS}}"
  KAFKA_FAULT_SECONDS="${KAFKA_FAULT_SECONDS:-${BASE_FAULT_SECONDS}}"
}

enforce_bounds() {
  local value

  for value in "$PAYMENT_FAULT_SECONDS" "$AUDIT_FAULT_SECONDS" "$KAFKA_FAULT_SECONDS"; do
    if ! is_non_negative_int "$value"; then
      echo "Fault windows must be non-negative integer seconds." >&2
      exit 2
    fi
  done
  for value in "$SCENARIO_TIMEOUT_SECONDS" "$STACK_TIMEOUT_SECONDS" "$DLT_TIMEOUT_SECONDS" "$POLL_SECONDS" "$CURL_TIMEOUT_SECONDS" "$FAULT_STOP_GRACE_SECONDS"; do
    if ! is_positive_int "$value"; then
      echo "Timeouts, polling and stop grace values must be positive integer seconds." >&2
      exit 2
    fi
  done

  if [ "$ALLOW_EXTENDED_FAULTS" != true ]; then
    if [ "$PAYMENT_FAULT_SECONDS" -gt 10 ] || [ "$AUDIT_FAULT_SECONDS" -gt 10 ] || [ "$KAFKA_FAULT_SECONDS" -gt 15 ]; then
      echo "Fault windows above 10s for services or 15s for Kafka require --allow-extended-faults." >&2
      exit 2
    fi
  fi
  if [ "$PAYMENT_FAULT_SECONDS" -gt 60 ] || [ "$AUDIT_FAULT_SECONDS" -gt 60 ] || [ "$KAFKA_FAULT_SECONDS" -gt 60 ]; then
    echo "Fault windows are capped at 60 seconds for local resilience validation." >&2
    exit 2
  fi
  if [ "$SCENARIO_TIMEOUT_SECONDS" -gt 1800 ]; then
    echo "Per-scenario timeout is capped at 1800 seconds." >&2
    exit 2
  fi
}

now_seconds() {
  date +%s
}

time_left() {
  local now
  now="$(now_seconds)"
  echo $((DEADLINE_SECONDS - now))
}

random_suffix() {
  if command -v uuidgen >/dev/null 2>&1; then
    uuidgen | tr '[:upper:]' '[:lower:]' | tr -d '-' | cut -c1-12
    return
  fi
  printf '%s%s' "$(date -u +%s)" "$$" | cut -c1-12
}

curl_json() {
  curl -fsS --connect-timeout "$CURL_TIMEOUT_SECONDS" --max-time "$CURL_TIMEOUT_SECONDS" "$@"
}

curl_keycloak_json() {
  curl -fsS --connect-timeout "$KEYCLOAK_CURL_TIMEOUT_SECONDS" --max-time "$KEYCLOAK_CURL_TIMEOUT_SECONDS" "$@"
}

curl_json_body() {
  local method="$1"
  local url="$2"
  local token="$3"
  local body="$4"

  curl_json \
    -X "$method" \
    -H "Authorization: Bearer ${token}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    --data "$body" \
    "$url"
}

curl_get_json() {
  local url="$1"
  local token="$2"

  curl_json \
    -H "Authorization: Bearer ${token}" \
    -H "Accept: application/json" \
    "$url"
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

record_timeline() {
  local label="$1"
  local elapsed

  elapsed=$(( $(now_seconds) - SCENARIO_STARTED_AT ))
  echo "[${CURRENT_SCENARIO} +${elapsed}s] ${label}"
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

compose_up_with_readiness_fallback() {
  local output_file status

  output_file="$(mktemp "${TMPDIR:-/tmp}/ecommerce-resilience-compose-up.XXXXXX")"
  set +e
  "$@" >"$output_file" 2>&1
  status=$?
  set -e

  cat "$output_file"
  if [ "$status" -eq 0 ]; then
    rm -f "$output_file"
    return 0
  fi

  if grep -Eiq 'dependency failed to start|is unhealthy' "$output_file"; then
    echo "Docker Compose reported an unhealthy dependency during startup; continuing to bounded readiness checks."
    rm -f "$output_file"
    return 0
  fi

  rm -f "$output_file"
  return "$status"
}

start_compose_stack() {
  export COMPOSE_BAKE="${COMPOSE_BAKE:-false}"
  export COMPOSE_PARALLEL_LIMIT="${COMPOSE_PARALLEL_LIMIT:-1}"

  case "$BUILD_MODE" in
    always)
      echo "Starting the full local stack with docker compose up -d --build..."
      compose_up_with_readiness_fallback docker compose up -d --build
      ;;
    never)
      echo "Starting the full local stack with docker compose up -d..."
      compose_up_with_readiness_fallback docker compose up -d
      ;;
    missing)
      if required_images_available; then
        echo "Starting the full local stack with existing local images..."
        compose_up_with_readiness_fallback docker compose up -d
      else
        echo "Local images are missing; starting the full local stack with docker compose up -d --build..."
        compose_up_with_readiness_fallback docker compose up -d --build
      fi
      ;;
    *)
      echo "Unsupported RESILIENCE_STACK_BUILD_MODE: ${BUILD_MODE}" >&2
      exit 2
      ;;
  esac
}

ensure_compose_service_started() {
  local service="$1"

  echo "Ensuring Compose service ${service} is started..."
  docker compose up -d --no-build --no-deps "$service"
}

mark_service_touched() {
  case "$1" in
    payment-service) TOUCHED_PAYMENT=true ;;
    audit-notification-service) TOUCHED_AUDIT=true ;;
    kafka) TOUCHED_KAFKA=true ;;
  esac
}

stop_compose_service() {
  local service="$1"

  mark_service_touched "$service"
  echo "Stopping Compose service ${service} for a bounded fault window..."
  docker compose stop -t "$FAULT_STOP_GRACE_SECONDS" "$service"
  wait_until "Compose service ${service} stopped" compose_service_stopped "$service"
}

start_touched_compose_service() {
  local service="$1"

  echo "Starting Compose service ${service} after the fault window..."
  docker compose up -d --no-build --no-deps "$service"
  wait_until "Compose service ${service} healthy after restart" compose_service_healthy "$service"
}

compose_service_stopped() {
  local service="$1"
  local container_id inspect_json state

  container_id="$(docker compose ps -a -q "$service" 2>/dev/null || true)"
  if [ -z "$container_id" ]; then
    return 1
  fi

  inspect_json="$(docker inspect "$container_id" 2>/dev/null || true)"
  if [ -z "$inspect_json" ]; then
    return 1
  fi

  state="$(printf '%s\n' "$inspect_json" | jq -r '.[0].State.Status // ""')"
  [ "$state" = exited ] || [ "$state" = dead ]
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

  if [ "$state" = exited ] || [ "$state" = dead ]; then
    echo "Service ${service} is ${state} with exit code ${exit_code}." >&2
    return 1
  fi
  if [ "$health" = unhealthy ]; then
    echo "Service ${service} is unhealthy." >&2
    return 1
  fi
  if [ "$health" = healthy ]; then
    return 0
  fi
  [ "$state" = running ] && [ "$health" = none ]
}

connector_name() {
  local connector_file="$1"
  jq -r '.name // empty' "$connector_file"
}

apply_connector_defaults() {
  export DEBEZIUM_ORDER_DB_HOST="${DEBEZIUM_ORDER_DB_HOST:-postgres}"
  export DEBEZIUM_ORDER_DB_PORT="${DEBEZIUM_ORDER_DB_PORT:-5432}"
  export DEBEZIUM_ORDER_DB_USER="${DEBEZIUM_ORDER_DB_USER:-ecommerce}"
  export DEBEZIUM_ORDER_DB_PASSWORD="${DEBEZIUM_ORDER_DB_PASSWORD:-change-me-local-postgres}"
  export DEBEZIUM_ORDER_DB_NAME="${DEBEZIUM_ORDER_DB_NAME:-orders}"
  export DEBEZIUM_ORDER_TOPIC_PREFIX="${DEBEZIUM_ORDER_TOPIC_PREFIX:-ecommerce.order.outbox.v1}"
  export DEBEZIUM_ORDER_SLOT_NAME="${DEBEZIUM_ORDER_SLOT_NAME:-order_outbox_slot_v1}"
  export DEBEZIUM_ORDER_PUBLICATION_NAME="${DEBEZIUM_ORDER_PUBLICATION_NAME:-order_outbox_publication_v1}"
  export DEBEZIUM_PAYMENT_DB_HOST="${DEBEZIUM_PAYMENT_DB_HOST:-postgres}"
  export DEBEZIUM_PAYMENT_DB_PORT="${DEBEZIUM_PAYMENT_DB_PORT:-5432}"
  export DEBEZIUM_PAYMENT_DB_USER="${DEBEZIUM_PAYMENT_DB_USER:-ecommerce}"
  export DEBEZIUM_PAYMENT_DB_PASSWORD="${DEBEZIUM_PAYMENT_DB_PASSWORD:-change-me-local-postgres}"
  export DEBEZIUM_PAYMENT_DB_NAME="${DEBEZIUM_PAYMENT_DB_NAME:-payments}"
  export DEBEZIUM_PAYMENT_TOPIC_PREFIX="${DEBEZIUM_PAYMENT_TOPIC_PREFIX:-ecommerce.payment.outbox.v1}"
  export DEBEZIUM_PAYMENT_SLOT_NAME="${DEBEZIUM_PAYMENT_SLOT_NAME:-payment_outbox_slot_v1}"
  export DEBEZIUM_PAYMENT_PUBLICATION_NAME="${DEBEZIUM_PAYMENT_PUBLICATION_NAME:-payment_outbox_publication_v1}"
}

render_resilience_connector_config() {
  local connector_file="$1"
  local config_file="$2"

  jq -e '
    def subst_env:
      reduce ([scan("\\$\\{env:([A-Za-z_][A-Za-z0-9_]*)\\}")][].[0]) as $name
        (. ; gsub("\\$\\{env:" + $name + "\\}"; env[$name] // error("Missing environment variable " + $name)));
    def render:
      if type == "string" then subst_env
      elif type == "array" then map(render)
      elif type == "object" then with_entries(.value |= render)
      else .
      end;
    .config
    | render
    | del(.["transforms.outbox.table.field.event.timestamp"])
    | .["snapshot.mode"] = "no_data"
  ' "$connector_file" > "$config_file"
}

put_connector_config() {
  local connector_file="$1"
  local connector_name="$2"
  local config_file response_file status_code

  config_file="$(mktemp)"
  response_file="$(mktemp)"
  render_resilience_connector_config "$connector_file" "$config_file"

  status_code="$(
    curl -sS \
      --connect-timeout "$CURL_TIMEOUT_SECONDS" \
      --max-time "$CURL_TIMEOUT_SECONDS" \
      -o "$response_file" \
      -w "%{http_code}" \
      -X PUT \
      -H "Content-Type: application/json" \
      --data-binary @"$config_file" \
      "$DEBEZIUM_CONNECT_URL/connectors/$connector_name/config"
  )"

  rm -f "$config_file"

  case "$status_code" in
    200|201)
      echo "Registered resilience connector ${connector_name} from ${connector_file}."
      ;;
    *)
      echo "Failed to register resilience connector ${connector_name} at ${DEBEZIUM_CONNECT_URL} (HTTP ${status_code})." >&2
      cat "$response_file" >&2
      rm -f "$response_file"
      exit 1
      ;;
  esac

  rm -f "$response_file"
}

reconcile_resilience_connectors() {
  apply_connector_defaults
  put_connector_config "$ORDER_CONNECTOR_FILE" "$ORDER_CONNECTOR_NAME"
  put_connector_config "$PAYMENT_CONNECTOR_FILE" "$PAYMENT_CONNECTOR_NAME"
}

connector_running() {
  local connector_name="$1"

  curl_json "$DEBEZIUM_CONNECT_URL/connectors/$connector_name/status" \
    | jq -e '.connector.state == "RUNNING" and (.tasks | length > 0) and all(.tasks[]; .state == "RUNNING")' >/dev/null
}

verify_stack_ready() {
  local service

  for service in postgres redis kafka mongodb keycloak mock-payment-provider prometheus jaeger debezium-connect catalog-service order-service payment-service audit-notification-service gateway-service; do
    ensure_compose_service_started "$service"
    wait_until "Compose service ${service}" compose_service_healthy "$service"
  done

  wait_until "Keycloak realm ${KEYCLOAK_REALM}" curl_ok "${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/.well-known/openid-configuration"
  wait_until "Keycloak admin token endpoint" keycloak_admin_token_available
  wait_until "mock payment provider health" curl_ok "${MOCK_PAYMENT_PROVIDER_URL}/actuator/health"
  wait_until "Catalog Service readiness" curl_ok "${CATALOG_URL}/actuator/health/readiness"
  wait_until "Order Service readiness" curl_ok "${ORDER_URL}/actuator/health/readiness"
  wait_until "Payment Service readiness" curl_ok "${PAYMENT_URL}/actuator/health/readiness"
  wait_until "Audit Notification Service readiness" curl_ok "${AUDIT_NOTIFICATION_URL}/actuator/health/readiness"
  wait_until "Gateway readiness" curl_ok "${GATEWAY_URL}/actuator/health/readiness"
  wait_until "Debezium Connect REST API" curl_ok "${DEBEZIUM_CONNECT_URL}/connectors"
  reconcile_resilience_connectors
  wait_until "Debezium connector ${ORDER_CONNECTOR_NAME}" connector_running "$ORDER_CONNECTOR_NAME"
  wait_until "Debezium connector ${PAYMENT_CONNECTOR_NAME}" connector_running "$PAYMENT_CONNECTOR_NAME"
}

keycloak_admin_token() {
  curl_keycloak_json \
    -X POST "${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    --data-urlencode "grant_type=password" \
    --data-urlencode "client_id=admin-cli" \
    --data-urlencode "username=${KEYCLOAK_ADMIN_USER}" \
    --data-urlencode "password=${KEYCLOAK_ADMIN_PASSWORD_VALUE}" \
    | jq -r '.access_token'
}

keycloak_admin_token_available() {
  local token
  token="$(keycloak_admin_token 2>/dev/null || true)"
  [ -n "$token" ] && [ "$token" != null ]
}

keycloak_role_json() {
  local admin_token="$1"
  local role="$2"

  curl_keycloak_json \
    -H "Authorization: Bearer ${admin_token}" \
    "${KEYCLOAK_URL}/admin/realms/${KEYCLOAK_REALM}/roles/${role}"
}

keycloak_user_id_by_username() {
  local admin_token="$1"
  local username="$2"

  curl_keycloak_json \
    -H "Authorization: Bearer ${admin_token}" \
    --get \
    --data-urlencode "username=${username}" \
    --data-urlencode "exact=true" \
    "${KEYCLOAK_URL}/admin/realms/${KEYCLOAK_REALM}/users" \
    | jq -r '.[0].id // empty'
}

keycloak_client_id_by_client_id() {
  local admin_token="$1"
  local client_id="$2"

  curl_keycloak_json \
    -H "Authorization: Bearer ${admin_token}" \
    --get \
    --data-urlencode "clientId=${client_id}" \
    "${KEYCLOAK_URL}/admin/realms/${KEYCLOAK_REALM}/clients" \
    | jq -r '.[0].id // empty'
}

keycloak_client_mappers() {
  local admin_token="$1"
  local client_uuid="$2"

  curl_keycloak_json \
    -H "Authorization: Bearer ${admin_token}" \
    "${KEYCLOAK_URL}/admin/realms/${KEYCLOAK_REALM}/clients/${client_uuid}/protocol-mappers/models"
}

ensure_keycloak_mapper() {
  local admin_token="$1"
  local client_uuid="$2"
  local mapper_name="$3"
  local mapper_json="$4"

  if keycloak_client_mappers "$admin_token" "$client_uuid" \
      | jq -e --arg name "$mapper_name" 'any(.[]; .name == $name)' >/dev/null; then
    return
  fi

  curl_keycloak_json \
    -X POST \
    -H "Authorization: Bearer ${admin_token}" \
    -H "Content-Type: application/json" \
    --data "$mapper_json" \
    "${KEYCLOAK_URL}/admin/realms/${KEYCLOAK_REALM}/clients/${client_uuid}/protocol-mappers/models" >/dev/null
}

ensure_local_test_client_mappers() {
  local admin_token="$1"
  local client_uuid realm_roles_mapper audience_mapper

  client_uuid="$(keycloak_client_id_by_client_id "$admin_token" "$KEYCLOAK_LOCAL_TEST_CLIENT")"
  if [ -z "$client_uuid" ]; then
    echo "Could not find Keycloak client ${KEYCLOAK_LOCAL_TEST_CLIENT}." >&2
    exit 1
  fi

  realm_roles_mapper="$(jq -n '{
    name:"realm-roles",
    protocol:"openid-connect",
    protocolMapper:"oidc-usermodel-realm-role-mapper",
    consentRequired:false,
    config:{
      "access.token.claim":"true",
      "claim.name":"realm_access.roles",
      "id.token.claim":"false",
      "jsonType.label":"String",
      "multivalued":"true",
      "userinfo.token.claim":"true"
    }
  }')"
  ensure_keycloak_mapper "$admin_token" "$client_uuid" realm-roles "$realm_roles_mapper"

  audience_mapper="$(jq -n '{
    name:"gateway-audience",
    protocol:"openid-connect",
    protocolMapper:"oidc-audience-mapper",
    consentRequired:false,
    config:{
      "access.token.claim":"true",
      "id.token.claim":"false",
      "included.client.audience":"ecommerce-gateway",
      "included.custom.audience":"",
      "userinfo.token.claim":"false"
    }
  }')"
  ensure_keycloak_mapper "$admin_token" "$client_uuid" gateway-audience "$audience_mapper"
}

create_keycloak_user() {
  local admin_token="$1"
  local username="$2"
  local password="$3"
  local role="$4"
  local body user_id role_json

  body="$(jq -n \
    --arg username "$username" \
    --arg email "${username}@example.invalid" \
    --arg runId "$RUN_ID" \
    '{
      username:$username,
      email:$email,
      firstName:"Local",
      lastName:"Resilience",
      enabled:true,
      emailVerified:true,
      requiredActions:[],
      attributes:{resilienceRunId:[$runId]}
    }')"

  curl_keycloak_json \
    -X POST \
    -H "Authorization: Bearer ${admin_token}" \
    -H "Content-Type: application/json" \
    --data "$body" \
    "${KEYCLOAK_URL}/admin/realms/${KEYCLOAK_REALM}/users" >/dev/null

  user_id="$(keycloak_user_id_by_username "$admin_token" "$username")"
  if [ -z "$user_id" ]; then
    echo "Created Keycloak user ${username}, but could not read its ID." >&2
    exit 1
  fi

  body="$(jq -n --arg password "$password" '{type:"password",temporary:false,value:$password}')"
  curl_keycloak_json \
    -X PUT \
    -H "Authorization: Bearer ${admin_token}" \
    -H "Content-Type: application/json" \
    --data "$body" \
    "${KEYCLOAK_URL}/admin/realms/${KEYCLOAK_REALM}/users/${user_id}/reset-password" >/dev/null

  role_json="$(keycloak_role_json "$admin_token" "$role")"
  curl_keycloak_json \
    -X POST \
    -H "Authorization: Bearer ${admin_token}" \
    -H "Content-Type: application/json" \
    --data "[$role_json]" \
    "${KEYCLOAK_URL}/admin/realms/${KEYCLOAK_REALM}/users/${user_id}/role-mappings/realm" >/dev/null

  echo "$user_id"
}

delete_keycloak_users() {
  local admin_token user_id

  if [ "$KEEP_KEYCLOAK_USERS" = true ] || [ "${#CREATED_KEYCLOAK_USER_IDS[@]}" -eq 0 ]; then
    return
  fi

  admin_token="$(keycloak_admin_token || true)"
  if [ -z "$admin_token" ] || [ "$admin_token" = null ]; then
    echo "Could not obtain a Keycloak admin token for resilience user cleanup." >&2
    return
  fi

  for user_id in "${CREATED_KEYCLOAK_USER_IDS[@]}"; do
    curl -fsS \
      --connect-timeout "$KEYCLOAK_CURL_TIMEOUT_SECONDS" \
      --max-time "$KEYCLOAK_CURL_TIMEOUT_SECONDS" \
      -X DELETE \
      -H "Authorization: Bearer ${admin_token}" \
      "${KEYCLOAK_URL}/admin/realms/${KEYCLOAK_REALM}/users/${user_id}" >/dev/null || true
  done
}

user_token() {
  local username="$1"
  local password="$2"

  curl_keycloak_json \
    -X POST "${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    --data-urlencode "grant_type=password" \
    --data-urlencode "client_id=${KEYCLOAK_LOCAL_TEST_CLIENT}" \
    --data-urlencode "username=${username}" \
    --data-urlencode "password=${password}" \
    --data-urlencode "scope=openid profile" \
    | jq -r '.access_token'
}

create_category() {
  local token="$1"
  local slug="$2"
  local body

  body="$(jq -n \
    --arg name "Resilience ${slug}" \
    --arg slug "$slug" \
    '{name:$name,slug:$slug,description:"Local resilience fixture category"}')"

  curl_json_body POST "${GATEWAY_URL}/api/catalog/admin/categories" "$token" "$body"
}

create_product() {
  local token="$1"
  local category_id="$2"
  local sku="$3"
  local name="$4"
  local price="$5"
  local body

  body="$(jq -n \
    --arg categoryId "$category_id" \
    --arg sku "$sku" \
    --arg name "$name" \
    --arg price "$price" \
    '{
      categoryId:$categoryId,
      sku:$sku,
      name:$name,
      description:"Local resilience fixture product",
      priceAmount:($price | tonumber),
      currency:"USD",
      status:"ACTIVE",
      attributes:[{attributeKey:"resilience-run",attributeValue:$sku}]
    }')"

  curl_json_body POST "${GATEWAY_URL}/api/catalog/admin/products" "$token" "$body"
}

assert_catalog_browse_and_detail() {
  local token="$1"
  local product_id="$2"
  local sku="$3"
  local encoded_sku list detail

  encoded_sku="$(jq -rn --arg value "$sku" '$value | @uri')"
  list="$(curl_get_json "${GATEWAY_URL}/api/catalog/products?q=${encoded_sku}&size=10" "$token")"
  echo "$list" | jq -e --arg productId "$product_id" '.content | any(.id == $productId)' >/dev/null

  detail="$(curl_get_json "${GATEWAY_URL}/api/catalog/products/${product_id}" "$token")"
  echo "$detail" | jq -e --arg sku "$sku" '.sku == $sku and .status == "ACTIVE"' >/dev/null
}

create_order() {
  local token="$1"
  local product_id="$2"
  local body

  body="$(jq -n --arg productId "$product_id" '{items:[{productId:$productId,quantity:1}]}')"
  curl_json_body POST "${GATEWAY_URL}/api/orders/customer/orders" "$token" "$body"
}

order_has_status() {
  local token="$1"
  local order_id="$2"
  local expected_status="$3"
  local response status

  response="$(curl_get_json "${GATEWAY_URL}/api/orders/customer/orders/${order_id}" "$token" 2>/dev/null || true)"
  if [ -z "$response" ]; then
    return 1
  fi
  status="$(echo "$response" | jq -r '.status // empty')"
  echo "Order ${order_id} status: ${status}"
  [ "$status" = "$expected_status" ]
}

psql_query() {
  local database="$1"
  local sql="$2"

  docker compose exec -T postgres \
    psql \
    -v ON_ERROR_STOP=1 \
    -U "$POSTGRES_USER" \
    -d "$database" \
    -Atc "$sql"
}

mongo_eval() {
  local js="$1"

  docker compose exec -T mongodb \
    mongosh \
    --quiet \
    --username "$MONGO_INITDB_ROOT_USERNAME" \
    --password "$MONGO_INITDB_ROOT_PASSWORD" \
    --authenticationDatabase "$AUDIT_NOTIFICATION_MONGODB_AUTHENTICATION_DATABASE" \
    "$AUDIT_NOTIFICATION_MONGODB_DATABASE" \
    --eval "$js"
}

payment_json_for_order() {
  local order_id="$1"

  psql_query payments "
    SELECT row_to_json(payment_row)
    FROM (
      SELECT
        p.id::text AS id,
        p.order_id::text AS order_id,
        p.customer_id,
        p.status,
        p.amount::text AS amount,
        p.currency,
        p.provider_reference,
        p.failure_reason,
        p.source_event_id::text AS source_event_id,
        (
          SELECT count(*)
          FROM payment_attempts pa
          WHERE pa.payment_id = p.id
        ) AS attempt_count,
        (
          SELECT count(*)
          FROM outbox_events oe
          WHERE oe.aggregate_id = p.order_id
        ) AS result_outbox_count
      FROM payments p
      WHERE p.order_id = '${order_id}'
    ) payment_row;"
}

payment_matches() {
  local order_id="$1"
  local expected_status="$2"
  local expected_attempts="$3"
  local expected_outbox="$4"
  local payment

  payment="$(payment_json_for_order "$order_id")"
  if [ -z "$payment" ]; then
    return 1
  fi

  echo "$payment" | jq -e \
    --arg status "$expected_status" \
    --argjson attempts "$expected_attempts" \
    --argjson outbox "$expected_outbox" \
    '.status == $status and .attempt_count == $attempts and .result_outbox_count == $outbox' >/dev/null
}

payment_id_for_order() {
  local order_id="$1"
  payment_json_for_order "$order_id" | jq -r '.id'
}

provider_attempt_count() {
  local order_id="$1"
  local encoded_order_id

  encoded_order_id="$(jq -rn --arg value "$order_id" '$value | @uri')"
  curl_json "${MOCK_PAYMENT_PROVIDER_URL}/api/mock-payments/requests?orderId=${encoded_order_id}" \
    | jq -r '.count'
}

provider_attempt_count_is() {
  local order_id="$1"
  local expected_count="$2"
  local count

  count="$(provider_attempt_count "$order_id")"
  echo "Mock provider attempts for ${order_id}: ${count}"
  [ "$count" = "$expected_count" ]
}

audit_event_count() {
  local order_id="$1"
  local event_type="$2"

  mongo_eval "db.audit_events.countDocuments({order_id:'${order_id}',event_type:'${event_type}'})"
}

delivery_count() {
  local order_id="$1"
  local event_type="$2"
  local channel="$3"

  mongo_eval "db.notification_deliveries.countDocuments({order_id:'${order_id}',event_type:'${event_type}',channel:'${channel}',status:'SENT',attempt_count:1})"
}

audit_and_deliveries_match() {
  local order_id="$1"
  local terminal_event="$2"
  local order_created_count terminal_count order_email order_push terminal_email terminal_push

  order_created_count="$(audit_event_count "$order_id" OrderCreated)"
  terminal_count="$(audit_event_count "$order_id" "$terminal_event")"
  order_email="$(delivery_count "$order_id" OrderCreated EMAIL)"
  order_push="$(delivery_count "$order_id" OrderCreated PUSH)"
  terminal_email="$(delivery_count "$order_id" "$terminal_event" EMAIL)"
  terminal_push="$(delivery_count "$order_id" "$terminal_event" PUSH)"

  echo "Audit counts for ${order_id}: OrderCreated=${order_created_count}, ${terminal_event}=${terminal_count}"
  echo "Delivery counts for ${order_id}: OrderCreated EMAIL/PUSH=${order_email}/${order_push}, ${terminal_event} EMAIL/PUSH=${terminal_email}/${terminal_push}"

  [ "$order_created_count" = 1 ] \
    && [ "$terminal_count" = 1 ] \
    && [ "$order_email" = 1 ] \
    && [ "$order_push" = 1 ] \
    && [ "$terminal_email" = 1 ] \
    && [ "$terminal_push" = 1 ]
}

outbox_payload() {
  local database="$1"
  local order_id="$2"
  local event_type="$3"

  psql_query "$database" "
    SELECT payload::text
    FROM outbox_events
    WHERE aggregate_id = '${order_id}'
      AND event_type = '${event_type}'
    ORDER BY occurred_at DESC
    LIMIT 1;"
}

publish_kafka_value() {
  local topic="$1"
  local payload="$2"

  printf '%s\n' "$payload" \
    | docker compose exec -T kafka \
        /opt/kafka/bin/kafka-console-producer.sh \
        --bootstrap-server localhost:9092 \
        --topic "$topic" >/dev/null
}

topic_end_offset_sum() {
  local topic="$1"
  local output

  output="$(docker compose exec -T kafka \
    /opt/kafka/bin/kafka-get-offsets.sh \
    --bootstrap-server localhost:9092 \
    --topic "$topic" 2>/dev/null || true)"

  if [ -z "$output" ]; then
    echo 0
    return
  fi

  echo "$output" | awk -F: '{sum += $3} END {print sum + 0}'
}

topic_offset_exceeds() {
  local topic="$1"
  local previous="$2"
  local current

  current="$(topic_end_offset_sum "$topic")"
  echo "Topic ${topic} end offset: ${current} (was ${previous})"
  [ "$current" -gt "$previous" ]
}

consumer_group_lag_zero() {
  local group="$1"
  local topic="$2"
  local output

  output="$(docker compose exec -T kafka \
    /opt/kafka/bin/kafka-consumer-groups.sh \
    --bootstrap-server localhost:9092 \
    --describe \
    --group "$group" 2>/dev/null || true)"

  echo "$output" | awk -v topic="$topic" '
    $2 == topic {
      found = 1
      if ($6 != "0" && $6 != "-") {
        bad = 1
      }
    }
    END {
      exit !(found && !bad)
    }'
}

wait_for_replay_consumers() {
  wait_until "Payment duplicate replay consumed" \
    consumer_group_lag_zero "$PAYMENT_SERVICE_ORDER_CREATED_GROUP_ID" "$ORDER_OUTBOX_TOPIC"
  wait_until "Audit order duplicate replay consumed" \
    consumer_group_lag_zero "$AUDIT_NOTIFICATION_ORDER_CREATED_GROUP_ID" "$ORDER_OUTBOX_TOPIC"
  wait_until "Order payment-result duplicate replay consumed" \
    consumer_group_lag_zero "$ORDER_SERVICE_PAYMENT_RESULT_GROUP_ID" "$PAYMENT_OUTBOX_TOPIC"
  wait_until "Audit payment-result duplicate replay consumed" \
    consumer_group_lag_zero "$AUDIT_NOTIFICATION_PAYMENT_RESULTS_GROUP_ID" "$PAYMENT_OUTBOX_TOPIC"
}

assert_duplicate_replay_safe() {
  local order_id="$1"
  local terminal_event="$2"
  local expected_payment_status="$3"
  local order_payload payment_payload

  order_payload="$(outbox_payload orders "$order_id" OrderCreated)"
  payment_payload="$(outbox_payload payments "$order_id" "$terminal_event")"
  if [ -z "$order_payload" ] || [ -z "$payment_payload" ]; then
    echo "Missing outbox payloads for replay safety check on order ${order_id}." >&2
    exit 1
  fi

  record_timeline "publishing duplicate OrderCreated and ${terminal_event} events"
  publish_kafka_value "$ORDER_OUTBOX_TOPIC" "$order_payload"
  publish_kafka_value "$PAYMENT_OUTBOX_TOPIC" "$payment_payload"

  wait_for_replay_consumers
  wait_until "Payment remains ${expected_payment_status} with one attempt after replay" \
    payment_matches "$order_id" "$expected_payment_status" 1 1
  wait_until "Mock provider was not called again after replay" \
    provider_attempt_count_is "$order_id" 1
  wait_until "Audit and notification delivery records remain exactly once after replay" \
    audit_and_deliveries_match "$order_id" "$terminal_event"
  record_timeline "duplicate replay remained idempotent"
}

print_consistency_snapshot() {
  local order_id="$1"
  local terminal_event="$2"
  local payment provider_count order_created_count terminal_count order_email order_push terminal_email terminal_push

  payment="$(payment_json_for_order "$order_id")"
  provider_count="$(provider_attempt_count "$order_id")"
  order_created_count="$(audit_event_count "$order_id" OrderCreated)"
  terminal_count="$(audit_event_count "$order_id" "$terminal_event")"
  order_email="$(delivery_count "$order_id" OrderCreated EMAIL)"
  order_push="$(delivery_count "$order_id" OrderCreated PUSH)"
  terminal_email="$(delivery_count "$order_id" "$terminal_event" EMAIL)"
  terminal_push="$(delivery_count "$order_id" "$terminal_event" PUSH)"

  echo "Final consistency for ${CURRENT_SCENARIO} order ${order_id}:"
  echo "$payment" | jq -c '{paymentStatus:.status, paymentAttemptCount:.attempt_count, terminalOutboxCount:.result_outbox_count}'
  echo "providerAttempts=${provider_count}"
  echo "auditEvents=OrderCreated:${order_created_count},${terminal_event}:${terminal_count}"
  echo "sentDeliveries=OrderCreated.EMAIL:${order_email},OrderCreated.PUSH:${order_push},${terminal_event}.EMAIL:${terminal_email},${terminal_event}.PUSH:${terminal_push}"
}

wait_for_final_consistency() {
  local order_id="$1"
  local expected_order_status="$2"
  local expected_payment_status="$3"
  local terminal_event="$4"

  wait_until "Order ${order_id} reaches ${expected_order_status}" \
    order_has_status "$CUSTOMER_TOKEN" "$order_id" "$expected_order_status"
  record_timeline "order reached ${expected_order_status}"
  wait_until "Payment state for ${order_id} is ${expected_payment_status}" \
    payment_matches "$order_id" "$expected_payment_status" 1 1
  record_timeline "payment reached ${expected_payment_status} with one terminal outbox row"
  wait_until "Mock provider has one authorization for ${order_id}" \
    provider_attempt_count_is "$order_id" 1
  wait_until "Audit and notification delivery state for ${order_id}" \
    audit_and_deliveries_match "$order_id" "$terminal_event"
  record_timeline "audit events and completed notifications reached exactly once"
}

create_scenario_product() {
  local scenario="$1"
  local sku product_json product_id

  sku="${RUN_SLUG}-${scenario}"
  product_json="$(create_product "$CATALOG_ADMIN_TOKEN" "$CATEGORY_ID" "$sku" "Resilience ${scenario}" "42.00")"
  product_id="$(echo "$product_json" | jq -r '.id')"
  if [ -z "$product_id" ] || [ "$product_id" = null ]; then
    echo "Product creation did not return an id for ${scenario}." >&2
    exit 1
  fi
  assert_catalog_browse_and_detail "$CUSTOMER_TOKEN" "$product_id" "$sku"
  echo "$product_id"
}

scenario_order_id_from_product() {
  local product_id="$1"
  local order_response order_id

  order_response="$(create_order "$CUSTOMER_TOKEN" "$product_id")"
  order_id="$(echo "$order_response" | jq -r '.orderId')"
  if [ -z "$order_id" ] || [ "$order_id" = null ]; then
    echo "Order creation did not return an orderId for ${CURRENT_SCENARIO}." >&2
    exit 1
  fi
  echo "$order_id"
}

sleep_fault_window() {
  local seconds="$1"

  if [ "$seconds" -gt 0 ]; then
    record_timeline "holding fault window for ${seconds}s"
    sleep "$seconds"
  fi
}

run_payment_restart_scenario() {
  local product_id order_id payment_id

  CURRENT_SCENARIO=payment-restart
  SCENARIO_STARTED_AT="$(now_seconds)"
  DEADLINE_SECONDS=$((SCENARIO_STARTED_AT + SCENARIO_TIMEOUT_SECONDS))
  echo "--- Scenario: payment-service restart during purchase ---"
  product_id="$(create_scenario_product "$CURRENT_SCENARIO")"
  record_timeline "catalog fixture ready"
  stop_compose_service payment-service
  record_timeline "payment-service stopped before order creation"
  order_id="$(scenario_order_id_from_product "$product_id")"
  record_timeline "order ${order_id} created while payment-service was unavailable"
  sleep_fault_window "$PAYMENT_FAULT_SECONDS"
  start_touched_compose_service payment-service
  record_timeline "payment-service restarted and healthy"
  wait_for_final_consistency "$order_id" PAID SUCCEEDED PaymentSucceeded
  payment_id="$(payment_id_for_order "$order_id")"
  record_timeline "payment ${payment_id} recovered successfully"
  assert_duplicate_replay_safe "$order_id" PaymentSucceeded SUCCEEDED
  print_consistency_snapshot "$order_id" PaymentSucceeded
}

run_audit_restart_scenario() {
  local product_id order_id payment_id

  CURRENT_SCENARIO=audit-restart
  SCENARIO_STARTED_AT="$(now_seconds)"
  DEADLINE_SECONDS=$((SCENARIO_STARTED_AT + SCENARIO_TIMEOUT_SECONDS))
  echo "--- Scenario: audit-notification-service restart during purchase ---"
  product_id="$(create_scenario_product "$CURRENT_SCENARIO")"
  record_timeline "catalog fixture ready"
  stop_compose_service audit-notification-service
  record_timeline "audit-notification-service stopped before order creation"
  order_id="$(scenario_order_id_from_product "$product_id")"
  record_timeline "order ${order_id} created while audit-notification-service was unavailable"
  sleep_fault_window "$AUDIT_FAULT_SECONDS"
  start_touched_compose_service audit-notification-service
  record_timeline "audit-notification-service restarted and healthy"
  wait_for_final_consistency "$order_id" PAID SUCCEEDED PaymentSucceeded
  payment_id="$(payment_id_for_order "$order_id")"
  record_timeline "payment ${payment_id} and audit recovery completed"
  assert_duplicate_replay_safe "$order_id" PaymentSucceeded SUCCEEDED
  print_consistency_snapshot "$order_id" PaymentSucceeded
}

run_kafka_interruption_scenario() {
  local product_id order_id payment_id

  CURRENT_SCENARIO=kafka-interruption
  SCENARIO_STARTED_AT="$(now_seconds)"
  DEADLINE_SECONDS=$((SCENARIO_STARTED_AT + SCENARIO_TIMEOUT_SECONDS))
  echo "--- Scenario: Kafka interruption during purchase ---"
  product_id="$(create_scenario_product "$CURRENT_SCENARIO")"
  record_timeline "catalog fixture ready"
  stop_compose_service kafka
  record_timeline "kafka stopped before order creation"
  order_id="$(scenario_order_id_from_product "$product_id")"
  record_timeline "order ${order_id} created while kafka was unavailable"
  sleep_fault_window "$KAFKA_FAULT_SECONDS"
  start_touched_compose_service kafka
  record_timeline "kafka restarted and healthy"
  wait_until "Debezium Connect recovered after Kafka interruption" compose_service_healthy debezium-connect
  wait_until "Order Service recovered after Kafka interruption" compose_service_healthy order-service
  wait_until "Payment Service recovered after Kafka interruption" compose_service_healthy payment-service
  wait_until "Audit Notification Service recovered after Kafka interruption" compose_service_healthy audit-notification-service
  wait_until "Debezium Connect REST API recovered" curl_ok "${DEBEZIUM_CONNECT_URL}/connectors"
  reconcile_resilience_connectors
  wait_until "Debezium connector ${ORDER_CONNECTOR_NAME} recovered" connector_running "$ORDER_CONNECTOR_NAME"
  wait_until "Debezium connector ${PAYMENT_CONNECTOR_NAME} recovered" connector_running "$PAYMENT_CONNECTOR_NAME"
  record_timeline "Kafka clients and connectors recovered"
  wait_for_final_consistency "$order_id" PAID SUCCEEDED PaymentSucceeded
  payment_id="$(payment_id_for_order "$order_id")"
  record_timeline "payment ${payment_id} recovered after Kafka interruption"
  assert_duplicate_replay_safe "$order_id" PaymentSucceeded SUCCEEDED
  print_consistency_snapshot "$order_id" PaymentSucceeded
}

exercise_dlt_boundaries() {
  local before_order_payment before_order_audit before_payment_order before_payment_audit
  local poison_order poison_payment

  CURRENT_SCENARIO=dlt-boundaries
  SCENARIO_STARTED_AT="$(now_seconds)"
  DEADLINE_SECONDS=$((SCENARIO_STARTED_AT + DLT_TIMEOUT_SECONDS))
  echo "--- Scenario: malformed replay DLT boundaries ---"

  before_order_payment="$(topic_end_offset_sum "$PAYMENT_SERVICE_ORDER_CREATED_DLT_TOPIC")"
  before_order_audit="$(topic_end_offset_sum "$AUDIT_NOTIFICATION_ORDER_CREATED_DLT_TOPIC")"
  before_payment_order="$(topic_end_offset_sum "$ORDER_SERVICE_PAYMENT_RESULT_DLT_TOPIC")"
  before_payment_audit="$(topic_end_offset_sum "$AUDIT_NOTIFICATION_PAYMENT_RESULTS_DLT_TOPIC")"

  poison_order="resilience-poison-order-created-${RUN_ID}-$(random_suffix)"
  poison_payment="resilience-poison-payment-result-${RUN_ID}-$(random_suffix)"
  publish_kafka_value "$ORDER_OUTBOX_TOPIC" "$poison_order"
  publish_kafka_value "$PAYMENT_OUTBOX_TOPIC" "$poison_payment"
  record_timeline "published malformed order and payment-result events"

  wait_until "Payment OrderCreated DLT offset increased" \
    topic_offset_exceeds "$PAYMENT_SERVICE_ORDER_CREATED_DLT_TOPIC" "$before_order_payment"
  wait_until "Audit OrderCreated DLT offset increased" \
    topic_offset_exceeds "$AUDIT_NOTIFICATION_ORDER_CREATED_DLT_TOPIC" "$before_order_audit"
  wait_until "Order payment-result DLT offset increased" \
    topic_offset_exceeds "$ORDER_SERVICE_PAYMENT_RESULT_DLT_TOPIC" "$before_payment_order"
  wait_until "Audit payment-result DLT offset increased" \
    topic_offset_exceeds "$AUDIT_NOTIFICATION_PAYMENT_RESULTS_DLT_TOPIC" "$before_payment_audit"
  record_timeline "all malformed-message DLT offsets increased"

  echo "DLT offsets after malformed probes:"
  echo "paymentOrderCreatedDlt=$(topic_end_offset_sum "$PAYMENT_SERVICE_ORDER_CREATED_DLT_TOPIC")"
  echo "auditOrderCreatedDlt=$(topic_end_offset_sum "$AUDIT_NOTIFICATION_ORDER_CREATED_DLT_TOPIC")"
  echo "orderPaymentResultDlt=$(topic_end_offset_sum "$ORDER_SERVICE_PAYMENT_RESULT_DLT_TOPIC")"
  echo "auditPaymentResultDlt=$(topic_end_offset_sum "$AUDIT_NOTIFICATION_PAYMENT_RESULTS_DLT_TOPIC")"
}

run_selected_scenarios() {
  local scenario_list scenario

  scenario_list="$(printf '%s' "$SCENARIOS" | tr ',' ' ')"
  for scenario in $scenario_list; do
    case "$scenario" in
      payment-restart) run_payment_restart_scenario ;;
      audit-restart) run_audit_restart_scenario ;;
      kafka-interruption) run_kafka_interruption_scenario ;;
      "")
        ;;
      *)
        echo "Unsupported resilience scenario: ${scenario}" >&2
        exit 2
        ;;
    esac
  done
}

print_diagnostics() {
  echo "--- Resilience run ---"
  echo "runId=${RUN_ID:-unknown}"
  echo "profile=${PROFILE:-unknown}"
  echo "scenarios=${SCENARIOS:-unknown}"
  echo "--- docker compose ps ---"
  docker compose ps || true
  echo "--- Debezium connector status ---"
  if [ -n "${ORDER_CONNECTOR_NAME:-}" ]; then
    curl_json "${DEBEZIUM_CONNECT_URL}/connectors/${ORDER_CONNECTOR_NAME}/status" | jq . || true
  fi
  if [ -n "${PAYMENT_CONNECTOR_NAME:-}" ]; then
    curl_json "${DEBEZIUM_CONNECT_URL}/connectors/${PAYMENT_CONNECTOR_NAME}/status" | jq . || true
  fi
  echo "--- mock provider ledger for run customer ---"
  if [ -n "${CUSTOMER_USER_ID:-}" ]; then
    curl_json "${MOCK_PAYMENT_PROVIDER_URL}/api/mock-payments/requests?customerId=${CUSTOMER_USER_ID}" | jq . || true
  fi
  echo "--- payment rows for run customer ---"
  if [ -n "${CUSTOMER_USER_ID:-}" ]; then
    psql_query payments "SELECT id::text, order_id::text, status, amount::text, failure_reason FROM payments WHERE customer_id = '${CUSTOMER_USER_ID}' ORDER BY created_at;" || true
  fi
  echo "--- audit rows for run customer ---"
  if [ -n "${CUSTOMER_USER_ID:-}" ]; then
    mongo_eval "EJSON.stringify(db.audit_events.find({customer_id:'${CUSTOMER_USER_ID}'},{_id:0,event_id:1,event_type:1,order_id:1,payment_id:1}).sort({event_type:1}).toArray())" || true
  fi
  echo "--- DLT end offsets ---"
  if [ -n "${PAYMENT_SERVICE_ORDER_CREATED_DLT_TOPIC:-}" ]; then
    echo "${PAYMENT_SERVICE_ORDER_CREATED_DLT_TOPIC}=$(topic_end_offset_sum "$PAYMENT_SERVICE_ORDER_CREATED_DLT_TOPIC")" || true
  fi
  if [ -n "${AUDIT_NOTIFICATION_ORDER_CREATED_DLT_TOPIC:-}" ]; then
    echo "${AUDIT_NOTIFICATION_ORDER_CREATED_DLT_TOPIC}=$(topic_end_offset_sum "$AUDIT_NOTIFICATION_ORDER_CREATED_DLT_TOPIC")" || true
  fi
  if [ -n "${ORDER_SERVICE_PAYMENT_RESULT_DLT_TOPIC:-}" ]; then
    echo "${ORDER_SERVICE_PAYMENT_RESULT_DLT_TOPIC}=$(topic_end_offset_sum "$ORDER_SERVICE_PAYMENT_RESULT_DLT_TOPIC")" || true
  fi
  if [ -n "${AUDIT_NOTIFICATION_PAYMENT_RESULTS_DLT_TOPIC:-}" ]; then
    echo "${AUDIT_NOTIFICATION_PAYMENT_RESULTS_DLT_TOPIC}=$(topic_end_offset_sum "$AUDIT_NOTIFICATION_PAYMENT_RESULTS_DLT_TOPIC")" || true
  fi
  for service in gateway-service catalog-service order-service payment-service audit-notification-service mock-payment-provider debezium-connect kafka; do
    echo "--- logs: ${service} ---"
    docker compose logs --no-color --tail 100 "$service" || true
  done
}

restore_touched_services() {
  if [ "${TOUCHED_KAFKA:-false}" = true ]; then
    docker compose up -d --no-build --no-deps kafka || true
  fi
  if [ "${TOUCHED_PAYMENT:-false}" = true ]; then
    docker compose up -d --no-build --no-deps payment-service || true
  fi
  if [ "${TOUCHED_AUDIT:-false}" = true ]; then
    docker compose up -d --no-build --no-deps audit-notification-service || true
  fi
}

cleanup() {
  local status="$1"

  if [ "$status" -ne 0 ]; then
    print_diagnostics >&2 || true
  fi
  restore_touched_services || true
  delete_keycloak_users || true
  if [ "$CLEANUP_ON_EXIT" = true ]; then
    echo "Stopping Compose services because --cleanup was requested. Volumes are left intact."
    docker compose stop || true
  fi
}

need_command docker
need_command curl
need_command jq
need_command awk
load_env_file
configure_compose_files
apply_profile_defaults
enforce_bounds

KEYCLOAK_PORT="${KEYCLOAK_PORT:-8085}"
KEYCLOAK_REALM="${KEYCLOAK_REALM:-ecommerce}"
KEYCLOAK_LOCAL_TEST_CLIENT="${KEYCLOAK_LOCAL_TEST_CLIENT:-ecommerce-local-test}"
KEYCLOAK_ADMIN_USER="${KEYCLOAK_ADMIN:-admin}"
KEYCLOAK_ADMIN_PASSWORD_VALUE="${KEYCLOAK_ADMIN_PASSWORD:-change-me-local-keycloak}"
KEYCLOAK_CURL_TIMEOUT_SECONDS="${RESILIENCE_KEYCLOAK_CURL_TIMEOUT_SECONDS:-20}"
GATEWAY_SERVICE_HOST_PORT="${GATEWAY_SERVICE_HOST_PORT:-8080}"
CATALOG_SERVICE_HOST_PORT="${CATALOG_SERVICE_HOST_PORT:-8081}"
ORDER_SERVICE_HOST_PORT="${ORDER_SERVICE_HOST_PORT:-8082}"
PAYMENT_SERVICE_HOST_PORT="${PAYMENT_SERVICE_HOST_PORT:-8083}"
AUDIT_NOTIFICATION_SERVICE_HOST_PORT="${AUDIT_NOTIFICATION_SERVICE_HOST_PORT:-8084}"
MOCK_PAYMENT_PROVIDER_HOST_PORT="${MOCK_PAYMENT_PROVIDER_HOST_PORT:-8089}"
DEBEZIUM_CONNECT_PORT="${DEBEZIUM_CONNECT_PORT:-8086}"
POSTGRES_USER="${POSTGRES_USER:-ecommerce}"
MONGO_INITDB_ROOT_USERNAME="${MONGO_INITDB_ROOT_USERNAME:-ecommerce}"
MONGO_INITDB_ROOT_PASSWORD="${MONGO_INITDB_ROOT_PASSWORD:-change-me-local-mongo}"
AUDIT_NOTIFICATION_MONGODB_DATABASE="${AUDIT_NOTIFICATION_MONGODB_DATABASE:-audit}"
AUDIT_NOTIFICATION_MONGODB_AUTHENTICATION_DATABASE="${AUDIT_NOTIFICATION_MONGODB_AUTHENTICATION_DATABASE:-admin}"
ORDER_OUTBOX_TOPIC="${ORDER_OUTBOX_TOPIC:-ecommerce.order.events}"
PAYMENT_OUTBOX_TOPIC="${PAYMENT_OUTBOX_TOPIC:-ecommerce.payment.events}"
PAYMENT_SERVICE_ORDER_CREATED_GROUP_ID="${PAYMENT_SERVICE_ORDER_CREATED_GROUP_ID:-payment-service.order-created.v1}"
AUDIT_NOTIFICATION_ORDER_CREATED_GROUP_ID="${AUDIT_NOTIFICATION_ORDER_CREATED_GROUP_ID:-audit-notification-service.order-created.v1}"
ORDER_SERVICE_PAYMENT_RESULT_GROUP_ID="${ORDER_SERVICE_PAYMENT_RESULT_GROUP_ID:-order-service.payment-results.v1}"
AUDIT_NOTIFICATION_PAYMENT_RESULTS_GROUP_ID="${AUDIT_NOTIFICATION_PAYMENT_RESULTS_GROUP_ID:-audit-notification-service.payment-results.v1}"
PAYMENT_SERVICE_ORDER_CREATED_DLT_TOPIC="${PAYMENT_SERVICE_ORDER_CREATED_DLT_TOPIC:-ecommerce.order.events.DLT}"
AUDIT_NOTIFICATION_ORDER_CREATED_DLT_TOPIC="${AUDIT_NOTIFICATION_ORDER_CREATED_DLT_TOPIC:-ecommerce.order.events.audit.DLT}"
ORDER_SERVICE_PAYMENT_RESULT_DLT_TOPIC="${ORDER_SERVICE_PAYMENT_RESULT_DLT_TOPIC:-ecommerce.payment.events.DLT}"
AUDIT_NOTIFICATION_PAYMENT_RESULTS_DLT_TOPIC="${AUDIT_NOTIFICATION_PAYMENT_RESULTS_DLT_TOPIC:-ecommerce.payment.events.audit.DLT}"
ORDER_CONNECTOR_FILE="${ORDER_OUTBOX_CONNECTOR_FILE:-infra/debezium/order-outbox-connector.json}"
PAYMENT_CONNECTOR_FILE="${PAYMENT_OUTBOX_CONNECTOR_FILE:-infra/debezium/payment-outbox-connector.json}"
ORDER_CONNECTOR_NAME="$(connector_name "$ORDER_CONNECTOR_FILE")"
PAYMENT_CONNECTOR_NAME="$(connector_name "$PAYMENT_CONNECTOR_FILE")"
if [ -z "$ORDER_CONNECTOR_NAME" ] || [ -z "$PAYMENT_CONNECTOR_NAME" ]; then
  echo "Both connector definitions must include a top-level name." >&2
  exit 2
fi

KEYCLOAK_URL="http://localhost:${KEYCLOAK_PORT}"
GATEWAY_URL="http://localhost:${GATEWAY_SERVICE_HOST_PORT}"
CATALOG_URL="http://localhost:${CATALOG_SERVICE_HOST_PORT}"
ORDER_URL="http://localhost:${ORDER_SERVICE_HOST_PORT}"
PAYMENT_URL="http://localhost:${PAYMENT_SERVICE_HOST_PORT}"
AUDIT_NOTIFICATION_URL="http://localhost:${AUDIT_NOTIFICATION_SERVICE_HOST_PORT}"
MOCK_PAYMENT_PROVIDER_URL="http://localhost:${MOCK_PAYMENT_PROVIDER_HOST_PORT}"
DEBEZIUM_CONNECT_URL="${DEBEZIUM_CONNECT_URL:-http://localhost:${DEBEZIUM_CONNECT_PORT}}"
RUN_ID="${RESILIENCE_RUN_ID:-resilience-$(date -u +%Y%m%d%H%M%S)-$(random_suffix)}"
RUN_SLUG="$(echo "$RUN_ID" | tr '[:upper:]_' '[:lower:]-' | tr -cd 'a-z0-9-' | cut -c1-36)"
ADMIN_USERNAME="${RESILIENCE_ADMIN_USERNAME:-${RUN_SLUG}-admin}"
CUSTOMER_USERNAME="${RESILIENCE_CUSTOMER_USERNAME:-${RUN_SLUG}-customer}"
ADMIN_PASSWORD="Local-Resilience-${RUN_SLUG}-admin-$(random_suffix)!"
CUSTOMER_PASSWORD="Local-Resilience-${RUN_SLUG}-customer-$(random_suffix)!"
CREATED_KEYCLOAK_USER_IDS=()
TOUCHED_PAYMENT=false
TOUCHED_AUDIT=false
TOUCHED_KAFKA=false
CURRENT_SCENARIO=setup
SCENARIO_STARTED_AT="$(now_seconds)"

trap 'cleanup "$?"' EXIT

echo "Resilience validation run ${RUN_ID}"
echo "profile=${PROFILE}, scenarios=${SCENARIOS}"
echo "faultWindows=payment:${PAYMENT_FAULT_SECONDS}s,audit:${AUDIT_FAULT_SECONDS}s,kafka:${KAFKA_FAULT_SECONDS}s"

if [ "$START_STACK" != false ]; then
  start_compose_stack
else
  echo "Using an already-running Compose stack."
fi
DEADLINE_SECONDS=$(( $(now_seconds) + STACK_TIMEOUT_SECONDS ))
verify_stack_ready

echo "Creating isolated Keycloak users for run ${RUN_ID}..."
ADMIN_TOKEN="$(keycloak_admin_token)"
if [ -z "$ADMIN_TOKEN" ] || [ "$ADMIN_TOKEN" = null ]; then
  echo "Could not obtain a Keycloak admin token." >&2
  exit 1
fi
ensure_local_test_client_mappers "$ADMIN_TOKEN"
ADMIN_USER_ID="$(create_keycloak_user "$ADMIN_TOKEN" "$ADMIN_USERNAME" "$ADMIN_PASSWORD" CATALOG_ADMIN)"
CUSTOMER_USER_ID="$(create_keycloak_user "$ADMIN_TOKEN" "$CUSTOMER_USERNAME" "$CUSTOMER_PASSWORD" CUSTOMER)"
CREATED_KEYCLOAK_USER_IDS+=("$ADMIN_USER_ID" "$CUSTOMER_USER_ID")
CATALOG_ADMIN_TOKEN="$(user_token "$ADMIN_USERNAME" "$ADMIN_PASSWORD")"
CUSTOMER_TOKEN="$(user_token "$CUSTOMER_USERNAME" "$CUSTOMER_PASSWORD")"
if [ -z "$CATALOG_ADMIN_TOKEN" ] || [ "$CATALOG_ADMIN_TOKEN" = null ] \
  || [ -z "$CUSTOMER_TOKEN" ] || [ "$CUSTOMER_TOKEN" = null ]; then
  echo "Could not obtain local Keycloak user tokens." >&2
  exit 1
fi

echo "Creating resilience catalog category through Gateway..."
CATEGORY_JSON="$(create_category "$CATALOG_ADMIN_TOKEN" "$RUN_SLUG")"
CATEGORY_ID="$(echo "$CATEGORY_JSON" | jq -r '.id')"
if [ -z "$CATEGORY_ID" ] || [ "$CATEGORY_ID" = null ]; then
  echo "Catalog category creation did not return an id." >&2
  exit 1
fi

run_selected_scenarios
exercise_dlt_boundaries

echo "Restart/replay resilience validation passed for run ${RUN_ID}."
