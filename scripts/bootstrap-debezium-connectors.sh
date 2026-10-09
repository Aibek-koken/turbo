#!/usr/bin/env bash
set -euo pipefail

CONNECT_URL="${DEBEZIUM_BOOTSTRAP_CONNECT_URL:-${DEBEZIUM_CONNECT_URL:-http://localhost:${DEBEZIUM_CONNECT_PORT:-8086}}}"
ORDER_CONNECTOR_FILE="${ORDER_OUTBOX_CONNECTOR_FILE:-infra/debezium/order-outbox-connector.json}"
PAYMENT_CONNECTOR_FILE="${PAYMENT_OUTBOX_CONNECTOR_FILE:-infra/debezium/payment-outbox-connector.json}"
CONNECT_TIMEOUT_SECONDS="${DEBEZIUM_BOOTSTRAP_TIMEOUT_SECONDS:-180}"
CONNECTOR_READY_TIMEOUT_SECONDS="${DEBEZIUM_CONNECTOR_READY_TIMEOUT_SECONDS:-180}"
POLL_SECONDS="${DEBEZIUM_BOOTSTRAP_POLL_SECONDS:-2}"
CURL_TIMEOUT_SECONDS="${DEBEZIUM_BOOTSTRAP_CURL_TIMEOUT_SECONDS:-30}"

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

need_command() {
  local command_name="$1"
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "Missing required command: $command_name" >&2
    exit 2
  fi
}

deadline_from_now() {
  local seconds="$1"
  echo $((SECONDS + seconds))
}

sleep_before_retry() {
  local deadline="$1"
  if [ "$SECONDS" -ge "$deadline" ]; then
    return 1
  fi
  sleep "$POLL_SECONDS"
}

curl_json() {
  curl -fsS --connect-timeout "$CURL_TIMEOUT_SECONDS" --max-time "$CURL_TIMEOUT_SECONDS" "$@"
}

wait_for_connect() {
  local deadline
  deadline="$(deadline_from_now "$CONNECT_TIMEOUT_SECONDS")"

  echo "Waiting up to ${CONNECT_TIMEOUT_SECONDS}s for Kafka Connect at ${CONNECT_URL}..."
  until curl_json "$CONNECT_URL/connectors" >/dev/null; do
    if ! sleep_before_retry "$deadline"; then
      echo "Kafka Connect did not become ready at ${CONNECT_URL} within ${CONNECT_TIMEOUT_SECONDS}s." >&2
      exit 1
    fi
  done
}

connector_name() {
  local connector_file="$1"
  jq -r '.name // empty' "$connector_file"
}

render_connector_config() {
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
    .config | render
  ' "$connector_file" > "$config_file"
}

register_connector() {
  local connector_file="$1"
  local connector_name="$2"
  local config_file
  local response_file
  local status_code

  config_file="$(mktemp)"
  response_file="$(mktemp)"
  render_connector_config "$connector_file" "$config_file"

  status_code="$(
    curl -sS \
      --connect-timeout "$CURL_TIMEOUT_SECONDS" \
      --max-time "$CURL_TIMEOUT_SECONDS" \
      -o "$response_file" \
      -w "%{http_code}" \
      -X PUT \
      -H "Content-Type: application/json" \
      --data-binary @"$config_file" \
      "$CONNECT_URL/connectors/$connector_name/config"
  )"

  rm -f "$config_file"

  case "$status_code" in
    200|201)
      echo "Registered connector ${connector_name} from ${connector_file}."
      ;;
    *)
      echo "Failed to register connector ${connector_name} at ${CONNECT_URL} (HTTP ${status_code})." >&2
      cat "$response_file" >&2
      rm -f "$response_file"
      exit 1
      ;;
  esac

  rm -f "$response_file"
}

print_connector_status() {
  local connector_name="$1"
  curl_json "$CONNECT_URL/connectors/$connector_name/status" | jq .
}

connector_is_running() {
  local connector_name="$1"
  curl_json "$CONNECT_URL/connectors/$connector_name/status" \
    | jq -e '.connector.state == "RUNNING" and (.tasks | length > 0) and all(.tasks[]; .state == "RUNNING")' >/dev/null
}

wait_for_connector_running() {
  local connector_name="$1"
  local deadline
  deadline="$(deadline_from_now "$CONNECTOR_READY_TIMEOUT_SECONDS")"

  echo "Waiting up to ${CONNECTOR_READY_TIMEOUT_SECONDS}s for connector ${connector_name} to run..."
  until connector_is_running "$connector_name"; do
    if ! sleep_before_retry "$deadline"; then
      echo "Connector ${connector_name} did not reach RUNNING state within ${CONNECTOR_READY_TIMEOUT_SECONDS}s." >&2
      print_connector_status "$connector_name" >&2 || true
      exit 1
    fi
  done
}

bootstrap_connector() {
  local connector_file="$1"
  local name

  if [ ! -f "$connector_file" ]; then
    echo "Missing connector definition: $connector_file" >&2
    exit 2
  fi

  name="$(connector_name "$connector_file")"
  if [ -z "$name" ]; then
    echo "Connector definition must include a top-level name: $connector_file" >&2
    exit 2
  fi

  register_connector "$connector_file" "$name"
  wait_for_connector_running "$name"
  print_connector_status "$name"
}

need_command curl
need_command jq
load_env_file
apply_connector_defaults

wait_for_connect
bootstrap_connector "$ORDER_CONNECTOR_FILE"
bootstrap_connector "$PAYMENT_CONNECTOR_FILE"

echo "Debezium outbox connector bootstrap completed."
