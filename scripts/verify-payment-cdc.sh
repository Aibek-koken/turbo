#!/usr/bin/env bash
set -euo pipefail

TOPIC="${PAYMENT_OUTBOX_TOPIC:-ecommerce.payment.events}"
POSTGRES_SERVICE="${PAYMENT_CDC_POSTGRES_SERVICE:-postgres}"
KAFKA_SERVICE="${PAYMENT_CDC_KAFKA_SERVICE:-kafka}"
POSTGRES_USER="${POSTGRES_USER:-ecommerce}"
POSTGRES_DB="${DEBEZIUM_PAYMENT_DB_NAME:-payments}"
TIMEOUT_MS="${PAYMENT_CDC_SMOKE_TIMEOUT_MS:-60000}"
MAX_MESSAGES="${PAYMENT_CDC_SMOKE_MAX_MESSAGES:-200}"

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

EVENT_ID="${PAYMENT_CDC_SMOKE_EVENT_ID:-$(new_uuid)}"
if [ -n "${PAYMENT_CDC_SMOKE_ORDER_ID:-}" ]; then
  ORDER_ID="$PAYMENT_CDC_SMOKE_ORDER_ID"
else
  ORDER_ID="${PAYMENT_CDC_SMOKE_AGGREGATE_ID:-$(new_uuid)}"
fi
PAYMENT_ID="${PAYMENT_CDC_SMOKE_PAYMENT_ID:-$(new_uuid)}"
ATTEMPT_ID="${PAYMENT_CDC_SMOKE_ATTEMPT_ID:-$(new_uuid)}"
ORDER_CREATED_EVENT_ID="${PAYMENT_CDC_SMOKE_ORDER_CREATED_EVENT_ID:-$(new_uuid)}"
OCCURRED_AT="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
CONSUMER_OUTPUT="$(mktemp)"
cleanup() {
  rm -f "$CONSUMER_OUTPUT"
}
trap cleanup EXIT

echo "Inserting smoke payment outbox event $EVENT_ID for order $ORDER_ID."

docker compose exec -T "$POSTGRES_SERVICE" psql \
  -v ON_ERROR_STOP=1 \
  -U "$POSTGRES_USER" \
  -d "$POSTGRES_DB" \
  -v event_id="$EVENT_ID" \
  -v order_id="$ORDER_ID" \
  -v payment_id="$PAYMENT_ID" \
  -v attempt_id="$ATTEMPT_ID" \
  -v order_created_event_id="$ORDER_CREATED_EVENT_ID" \
  -v occurred_at="$OCCURRED_AT" <<'SQL'
INSERT INTO outbox_events (
    id,
    aggregate_id,
    event_type,
    event_version,
    payload,
    occurred_at,
    trace_id,
    correlation_id
)
VALUES (
    :'event_id'::uuid,
    :'order_id'::uuid,
    'PaymentSucceeded',
    1,
    json_build_object(
        'eventId', :'event_id',
        'eventType', 'PaymentSucceeded',
        'eventVersion', 1,
        'aggregateId', :'order_id',
        'occurredAt', :'occurred_at',
        'traceId', 'payment-cdc-smoke-trace',
        'correlationId', 'payment-cdc-smoke-correlation',
        'data', json_build_object(
            'paymentId', :'payment_id',
            'orderId', :'order_id',
            'customerId', 'payment-cdc-smoke-customer',
            'amount', '42.0000',
            'currency', 'USD',
            'paymentStatus', 'SUCCEEDED',
            'providerAttemptId', :'attempt_id',
            'providerAttemptOutcome', 'SUCCEEDED',
            'providerReference', 'payment-cdc-smoke-provider-ref',
            'failureReason', NULL,
            'orderCreatedEventId', :'order_created_event_id'
        )
    )::jsonb,
    :'occurred_at'::timestamptz,
    'payment-cdc-smoke-trace',
    'payment-cdc-smoke-correlation'
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

if grep -F "$EVENT_ID" "$CONSUMER_OUTPUT" >/dev/null && grep -F "$ORDER_ID" "$CONSUMER_OUTPUT" >/dev/null; then
  echo "Verified $EVENT_ID with order $ORDER_ID on $TOPIC."
else
  cat "$CONSUMER_OUTPUT" >&2
  echo "Did not find event $EVENT_ID with order $ORDER_ID on $TOPIC within ${TIMEOUT_MS}ms." >&2
  exit 1
fi
