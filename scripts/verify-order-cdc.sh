#!/usr/bin/env bash
set -euo pipefail

TOPIC="${ORDER_OUTBOX_TOPIC:-ecommerce.order.events}"
POSTGRES_SERVICE="${ORDER_CDC_POSTGRES_SERVICE:-postgres}"
KAFKA_SERVICE="${ORDER_CDC_KAFKA_SERVICE:-kafka}"
POSTGRES_USER="${POSTGRES_USER:-ecommerce}"
POSTGRES_DB="${DEBEZIUM_ORDER_DB_NAME:-orders}"
TIMEOUT_MS="${ORDER_CDC_SMOKE_TIMEOUT_MS:-60000}"
MAX_MESSAGES="${ORDER_CDC_SMOKE_MAX_MESSAGES:-200}"

new_uuid() {
  if command -v uuidgen >/dev/null 2>&1; then
    uuidgen | tr '[:upper:]' '[:lower:]'
  elif command -v python3 >/dev/null 2>&1; then
    python3 -c 'import uuid; print(uuid.uuid4())'
  else
    echo "uuidgen or python3 is required to create a smoke event ID." >&2
    exit 2
  fi
}

EVENT_ID="${ORDER_CDC_SMOKE_EVENT_ID:-$(new_uuid)}"
AGGREGATE_ID="${ORDER_CDC_SMOKE_AGGREGATE_ID:-$(new_uuid)}"
OCCURRED_AT="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
CONSUMER_OUTPUT="$(mktemp)"
cleanup() {
  rm -f "$CONSUMER_OUTPUT"
}
trap cleanup EXIT

echo "Inserting smoke outbox event $EVENT_ID for aggregate $AGGREGATE_ID."

docker compose exec -T "$POSTGRES_SERVICE" psql \
  -v ON_ERROR_STOP=1 \
  -U "$POSTGRES_USER" \
  -d "$POSTGRES_DB" \
  -v event_id="$EVENT_ID" \
  -v aggregate_id="$AGGREGATE_ID" \
  -v occurred_at="$OCCURRED_AT" <<'SQL'
INSERT INTO outbox_events (
    id,
    aggregate_id,
    aggregate_type,
    event_type,
    event_version,
    occurred_at,
    trace_id,
    correlation_id,
    payload
)
VALUES (
    :'event_id'::uuid,
    :'aggregate_id'::uuid,
    'Order',
    'OrderCreated',
    1,
    :'occurred_at'::timestamptz,
    'cdc-smoke-trace',
    'cdc-smoke-correlation',
    json_build_object(
        'eventId', :'event_id',
        'eventType', 'OrderCreated',
        'eventVersion', 1,
        'aggregateId', :'aggregate_id',
        'occurredAt', :'occurred_at',
        'traceId', 'cdc-smoke-trace',
        'correlationId', 'cdc-smoke-correlation',
        'data', json_build_object(
            'orderId', :'aggregate_id',
            'customerId', 'cdc-smoke-customer',
            'status', 'CREATED',
            'subtotalAmount', 0,
            'totalAmount', 0,
            'currency', 'USD',
            'createdAt', :'occurred_at',
            'items', json_build_array()
        )
    )
)
ON CONFLICT (id) DO NOTHING;
SQL

echo "Checking topic $TOPIC for event $EVENT_ID."
set +e
docker compose exec -T "$KAFKA_SERVICE" kafka-console-consumer.sh \
  --bootstrap-server kafka:9092 \
  --topic "$TOPIC" \
  --from-beginning \
  --timeout-ms "$TIMEOUT_MS" \
  --max-messages "$MAX_MESSAGES" \
  --property print.key=true \
  --property key.separator='|' > "$CONSUMER_OUTPUT"
CONSUMER_STATUS="$?"
set -e

if [ "$CONSUMER_STATUS" -ne 0 ] && [ "$CONSUMER_STATUS" -ne 1 ]; then
  cat "$CONSUMER_OUTPUT" >&2
  echo "Kafka consumer failed while reading $TOPIC." >&2
  exit "$CONSUMER_STATUS"
fi

if grep -F "$EVENT_ID" "$CONSUMER_OUTPUT" >/dev/null && grep -F "$AGGREGATE_ID" "$CONSUMER_OUTPUT" >/dev/null; then
  echo "Verified $EVENT_ID with aggregate $AGGREGATE_ID on $TOPIC."
else
  cat "$CONSUMER_OUTPUT" >&2
  echo "Did not find event $EVENT_ID with aggregate $AGGREGATE_ID on $TOPIC within ${TIMEOUT_MS}ms." >&2
  exit 1
fi
