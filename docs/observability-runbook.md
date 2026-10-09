# Observability Runbook

This is the local baseline for metrics, traces and request correlation.

## Endpoints

Each Spring service exposes:

| Service | Health | Prometheus |
| --- | --- | --- |
| gateway-service | `http://localhost:8080/actuator/health` | `http://localhost:8080/actuator/prometheus` |
| catalog-service | `http://localhost:8081/actuator/health` | `http://localhost:8081/actuator/prometheus` |
| order-service | `http://localhost:8082/actuator/health` | `http://localhost:8082/actuator/prometheus` |
| payment-service | `http://localhost:8083/actuator/health` | `http://localhost:8083/actuator/prometheus` |
| audit-notification-service | `http://localhost:8084/actuator/health` | `http://localhost:8084/actuator/prometheus` |

Health, info and Prometheus endpoints are intentionally public for local
operations checks. Application API paths remain protected by JWT rules.

## Local Metrics

Prometheus uses `infra/observability/prometheus.yml`, loads local alert rules
from `infra/observability/alert-rules.yml` and scrapes:

- Prometheus itself.
- Keycloak management metrics.
- The five Spring services through `host.docker.internal` on ports 8080-8084.

Start the infrastructure with:

```bash
docker compose up -d prometheus jaeger keycloak
```

Then run one or more services on the host and open:

```text
http://localhost:9090/targets
```

Every Spring service also adds an `application` metrics tag using its
`spring.application.name`.

## Custom Operational Meters

HTTP latency and error metrics remain the Spring Boot Actuator defaults. The
portfolio-specific meters use bounded labels only: `service`, `topic`,
`event_type`, `channel`, `cache` and `outcome`. Do not add event, order,
payment, customer, trace or correlation identifiers as metric labels.

| Prometheus metric | Labels | Meaning |
| --- | --- | --- |
| `ecommerce_cache_requests_total` | `service`, `cache`, `outcome` | Catalog product-detail cache lookup hit/miss counts. |
| `ecommerce_kafka_consumer_events_total` | `service`, `topic`, `event_type`, `outcome` | Order, Payment and Audit Kafka consumer processing outcomes. |
| `ecommerce_kafka_consumer_retries_total` | `service`, `topic`, `event_type`, `outcome` | Bounded Kafka retry attempts; `outcome="retry"`. |
| `ecommerce_kafka_consumer_dead_letter_publications_total` | `service`, `topic`, `event_type`, `outcome` | Dead-letter publish attempts; `topic` is the source topic and `outcome="attempted"`. |
| `ecommerce_payment_terminal_outcomes_total` | `service`, `event_type`, `outcome` | Payment terminal result outcomes, including failed terminal payments. |
| `ecommerce_audit_persistence_events_total` | `service`, `topic`, `event_type`, `outcome` | Audit Mongo persistence outcomes. |
| `ecommerce_notification_deliveries_total` | `service`, `channel`, `event_type`, `outcome` | Mock email and push delivery outcomes. |
| `ecommerce_kafka_dead_letter_topic_depth` | `service`, `topic` | Retained records across partitions for the fixed configured Order, Payment and Audit dead-letter topics. |

Representative PromQL:

```promql
# HTTP 5xx rate by service.
sum by (application, uri, status) (
  rate(http_server_requests_seconds_count{status=~"5.."}[5m])
)

# Catalog product-detail cache hit ratio.
sum(rate(ecommerce_cache_requests_total{service="catalog-service",cache="product-detail",outcome="hit"}[5m]))
/
sum(rate(ecommerce_cache_requests_total{service="catalog-service",cache="product-detail"}[5m]))

# Kafka consumer failures and rejected payloads.
sum by (service, topic, event_type, outcome) (
  increase(ecommerce_kafka_consumer_events_total{outcome=~"failure|rejected"}[15m])
)

# Kafka retry attempts.
sum by (service, topic, event_type) (
  increase(ecommerce_kafka_consumer_retries_total[15m])
)

# Dead-letter publish attempts.
sum by (service, topic, event_type) (
  increase(ecommerce_kafka_consumer_dead_letter_publications_total[15m])
)

# Terminal payment failures.
sum(
  increase(ecommerce_payment_terminal_outcomes_total{service="payment-service",event_type="PaymentFailed",outcome="failed"}[15m])
)

# Audit persistence failures.
sum by (topic, event_type) (
  increase(ecommerce_audit_persistence_events_total{outcome="failure"}[15m])
)

# Notification delivery failures or exhausted retry budgets.
sum by (channel, event_type, outcome) (
  increase(ecommerce_notification_deliveries_total{outcome=~"failed|exhausted"}[15m])
)

# Dead-letter topics with retained records.
max by (service, topic) (
  ecommerce_kafka_dead_letter_topic_depth
) > 0
```

## Local Alert Rules

Local Prometheus alert rules cover:

- Service scrape availability for the five Spring services.
- Sustained HTTP 5xx responses.
- Terminal payment failures.
- Mock email or push delivery failures.
- Kafka consumer rejected/failure outcomes.
- Positive retained depth on the fixed configured dead-letter topics.

Inspect rule loading and alert state locally:

```bash
docker compose up -d prometheus
docker compose exec prometheus promtool check config /etc/prometheus/prometheus.yml
curl -fsS http://localhost:9090/api/v1/rules | jq '.data.groups[].rules[] | {name, state, query}'
curl -fsS http://localhost:9090/api/v1/alerts | jq '.data.alerts[] | {name: .labels.alertname, state: .state, labels: .labels}'
```

Prometheus was started with lifecycle reload enabled, so config/rule edits can
be reloaded without deleting metrics data:

```bash
curl -X POST http://localhost:9090/-/reload
```

## Bounded Dead-Letter Signal Simulation

For a local-only positive-DLQ-depth check that does not delete Kafka or
application data, point one configured DLT gauge at a temporary smoke topic,
start Audit Notification Service with that environment override, and write one
synthetic record:

```bash
export PAYMENT_SERVICE_ORDER_CREATED_DLT_TOPIC=ecommerce.local.dlt-smoke
docker compose up -d kafka prometheus
printf 'ecom-039-local-smoke\n' | docker compose exec -T kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 \
  --topic ecommerce.local.dlt-smoke
```

After the next audit-service Prometheus scrape, check:

```promql
ecommerce_kafka_dead_letter_topic_depth{topic="ecommerce.local.dlt-smoke"}
max by (service, topic) (ecommerce_kafka_dead_letter_topic_depth) > 0
```

Keep this simulation local. It intentionally produces one harmless retained
record to a smoke topic and does not use Kafka topic deletion, database cleanup
or application table truncation.

## Local Traces

Micrometer Tracing is enabled for all services with W3C trace propagation.
Spans are exported through OTLP HTTP to Jaeger by default:

```text
http://localhost:4318/v1/traces
```

Override the endpoint for a different collector with:

```bash
export OTEL_EXPORTER_OTLP_TRACES_ENDPOINT=http://localhost:4318/v1/traces
```

Open Jaeger locally at:

```text
http://localhost:16686
```

## Correlation IDs

The platform uses `X-Correlation-Id` for request correlation:

- The gateway accepts an inbound value or generates one when absent.
- The gateway forwards `X-Correlation-Id` to downstream services and returns it
  on the response.
- Servlet services accept or generate the same header, return it on responses
  and add it to the logging MDC as `correlationId`.
- Order creation copies the active MDC `traceId` and `correlationId` into the
  `OrderCreated` outbox envelope.
- Payment and Audit Kafka consumers enable Spring Kafka observation and create
  service-local processing observations around valid envelopes.
- Kafka consumer handling restores envelope `traceId`, `correlationId`,
  `eventId`, `eventType`, `eventVersion` and `aggregateId` into MDC only for
  the active handling scope, then restores the previous MDC values.
- Audit notification dispatch temporarily binds each delivery record's event
  metadata while sending mock email or push notifications, so batched pending
  deliveries do not inherit the triggering listener's event context.
- Kafka-service log patterns include `application`, `traceId`, `spanId`,
  `correlationId`, `eventId` and `eventType`.

Debezium publishes the stored JSON outbox payload as the event envelope. The
envelope remains the cross-service correlation contract for the Kafka hops;
Kafka tracing/observation adds consumer spans around that contract and does
not replace it. Do not add trace, correlation, event, order, payment or
customer identifiers as metric tags.

## Local Trace And Correlation Flow

Use this flow to inspect one purchase across REST, outbox, Kafka, payment,
audit persistence and notification logs.

1. Start local infrastructure and the services:

```bash
docker compose up -d postgres kafka debezium-connect mongodb jaeger keycloak prometheus
```

2. Register the outbox connectors if they are not already present:

```bash
./scripts/register-order-outbox-connector.sh
./scripts/register-payment-outbox-connector.sh
```

The default full-stack Compose graph also runs the idempotent
`debezium-connector-bootstrap` service, so manual registration is only needed
when you start a partial stack.

3. Create an order through the gateway with a known correlation ID. Use a local
   customer JWT for `CUSTOMER_TOKEN` and a valid active Catalog product ID from
   local demo or E2E fixture data:

```bash
export CORRELATION_ID=purchase-local-001
curl -i \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H "Content-Type: application/json" \
  -H "X-Correlation-Id: $CORRELATION_ID" \
  -d '{"items":[{"productId":"00000000-0000-0000-0000-000000000001","quantity":1}]}' \
  http://localhost:8080/api/orders/customer/orders
```

4. Confirm the gateway response returns the same `X-Correlation-Id`, then
   inspect the Order outbox envelope:

```bash
docker compose exec postgres psql -U ecommerce -d orders \
  -c "select id,event_type,trace_id,correlation_id,payload->>'eventId' as envelope_event_id from outbox_events order by created_at desc limit 5;"
```

5. Inspect the two Kafka topics. The JSON values should contain the same
   `correlationId`, the original `OrderCreated.eventId`, and the payment-result
   envelope emitted by Payment:

```bash
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic ecommerce.order.events \
  --from-beginning \
  --max-messages 5

docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic ecommerce.payment.events \
  --from-beginning \
  --max-messages 5
```

6. Inspect Audit MongoDB. The `audit_events` document and each
   `notification_deliveries` document should retain `trace_id`,
   `correlation_id` and `event_id`:

```bash
docker compose exec mongodb mongosh -u ecommerce -p change-me-local-mongo --authenticationDatabase admin audit \
  --eval 'db.audit_events.find({}, {event_id:1,event_type:1,order_id:1,payment_id:1,trace_id:1,correlation_id:1}).sort({received_at:-1}).limit(5)'

docker compose exec mongodb mongosh -u ecommerce -p change-me-local-mongo --authenticationDatabase admin audit \
  --eval 'db.notification_deliveries.find({}, {event_id:1,channel:1,status:1,order_id:1,payment_id:1,trace_id:1,correlation_id:1}).sort({created_at:-1}).limit(10)'
```

7. Search service logs for the correlation ID:

```bash
docker compose logs --no-color | grep "$CORRELATION_ID"
```

8. Open Jaeger at `http://localhost:16686` and inspect Gateway/Order HTTP
   spans plus Kafka listener processing spans for Order, Payment and Audit.
   Because Debezium emits database outbox rows to Kafka, use the event envelope
   `traceId`, `correlationId` and `eventId` fields to connect the two Kafka
   hops and audit/notification records when a single distributed trace is split
   at the outbox boundary.

## Quick Checks

```bash
curl -i http://localhost:8080/actuator/health
curl -i -H 'X-Correlation-Id: local-check-1' http://localhost:8081/actuator/health
curl -fsS http://localhost:8080/actuator/prometheus | head
```

If Prometheus targets are down, confirm the service is running on its default
host port and that Docker can resolve `host.docker.internal`.
