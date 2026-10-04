#!/usr/bin/env bash
set -euo pipefail

CONNECT_URL="${DEBEZIUM_CONNECT_URL:-http://localhost:${DEBEZIUM_CONNECT_PORT:-8086}}"
CONNECTOR_FILE="${PAYMENT_OUTBOX_CONNECTOR_FILE:-infra/debezium/payment-outbox-connector.json}"

need_command() {
  local command_name="$1"
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "Missing required command: $command_name" >&2
    exit 2
  fi
}

need_command curl
need_command jq

if [ ! -f "$CONNECTOR_FILE" ]; then
  echo "Missing connector definition: $CONNECTOR_FILE" >&2
  exit 2
fi

CONNECTOR_NAME="$(jq -r '.name // empty' "$CONNECTOR_FILE")"
if [ -z "$CONNECTOR_NAME" ]; then
  echo "Connector definition must include a top-level name." >&2
  exit 2
fi

CONFIG_FILE="$(mktemp)"
RESPONSE_FILE="$(mktemp)"
cleanup() {
  rm -f "$CONFIG_FILE" "$RESPONSE_FILE"
}
trap cleanup EXIT

jq -e '.config' "$CONNECTOR_FILE" > "$CONFIG_FILE"

STATUS_CODE="$(
  curl -sS \
    -o "$RESPONSE_FILE" \
    -w "%{http_code}" \
    -X PUT \
    -H "Content-Type: application/json" \
    --data-binary @"$CONFIG_FILE" \
    "$CONNECT_URL/connectors/$CONNECTOR_NAME/config"
)"

case "$STATUS_CODE" in
  200|201)
    echo "Registered connector $CONNECTOR_NAME at $CONNECT_URL."
    ;;
  *)
    echo "Failed to register connector $CONNECTOR_NAME at $CONNECT_URL (HTTP $STATUS_CODE)." >&2
    cat "$RESPONSE_FILE" >&2
    exit 1
    ;;
esac

curl -fsS "$CONNECT_URL/connectors/$CONNECTOR_NAME/status" | jq .
