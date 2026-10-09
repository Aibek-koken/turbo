# Demo Runbook

This runbook shows the authenticated purchase happy path, the deterministic
payment-failure path and the evidence expected in Payment, Audit Notification,
the mock provider, metrics and traces.

Use the automated E2E command when you want the fastest repeatable demo:

```bash
scripts/project-validate.sh e2e-test
```

The command creates isolated local Keycloak users, creates Catalog fixtures
through Gateway, runs approved and declined purchases, verifies replay safety
and removes only the generated Keycloak users. The manual flow below uses the
same boundaries with explicit curl commands.

## Prerequisites

- Java 21, Maven 3.9+, Docker Compose, `curl` and `jq`.
- Full stack running:

```bash
docker compose up --build
```

- Readiness checked from another shell:

```bash
scripts/verify-full-stack.sh --skip-up
```

- Local Keycloak users created in the `ecommerce` realm:
  - one user with `CATALOG_ADMIN`
  - one user with `CUSTOMER`

No real user passwords, tokens or provider credentials belong in the
repository. Use local disposable values only.

## Get Local Tokens

Request a token for each local user. Replace placeholders with local-only
credentials from your Keycloak realm.

```bash
export CATALOG_ADMIN_TOKEN="$(
  curl -sS -X POST "http://localhost:8085/realms/ecommerce/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "grant_type=password" \
    -d "client_id=ecommerce-local-test" \
    -d "username=<catalog-admin-username>" \
    -d "password=<catalog-admin-password>" \
    -d "scope=openid profile email" \
  | jq -r '.access_token'
)"

export CUSTOMER_TOKEN="$(
  curl -sS -X POST "http://localhost:8085/realms/ecommerce/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "grant_type=password" \
    -d "client_id=ecommerce-local-test" \
    -d "username=<customer-username>" \
    -d "password=<customer-password>" \
    -d "scope=openid profile email" \
  | jq -r '.access_token'
)"
```

Confirm the tokens are not empty:

```bash
test -n "$CATALOG_ADMIN_TOKEN"
test -n "$CUSTOMER_TOKEN"
```

## Create Catalog Fixtures

Create a category through Gateway:

```bash
export DEMO_SLUG="demo-$(date -u +%Y%m%d%H%M%S)"

export CATEGORY_ID="$(
  curl -fsS -X POST "http://localhost:8080/api/catalog/admin/categories" \
    -H "Authorization: Bearer $CATALOG_ADMIN_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{
      \"name\":\"Demo ${DEMO_SLUG}\",
      \"slug\":\"${DEMO_SLUG}\",
      \"description\":\"Local demo category\"
    }" \
  | jq -r '.id'
)"
```

Create an approved product. The mock payment provider approves this amount by
default:

```bash
export APPROVED_PRODUCT_ID="$(
  curl -fsS -X POST "http://localhost:8080/api/catalog/admin/products" \
    -H "Authorization: Bearer $CATALOG_ADMIN_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{
      \"categoryId\":\"${CATEGORY_ID}\",
      \"sku\":\"${DEMO_SLUG}-approved\",
      \"name\":\"Demo Approved Product\",
      \"description\":\"Local approved demo product\",
      \"priceAmount\":42.00,
      \"currency\":\"USD\",
      \"status\":\"ACTIVE\",
      \"attributes\":[{\"attributeKey\":\"demo-run\",\"attributeValue\":\"${DEMO_SLUG}\"}]
    }" \
  | jq -r '.id'
)"
```

Create a declined product. The local mock provider declines amount `400.00`:

```bash
export DECLINED_PRODUCT_ID="$(
  curl -fsS -X POST "http://localhost:8080/api/catalog/admin/products" \
    -H "Authorization: Bearer $CATALOG_ADMIN_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{
      \"categoryId\":\"${CATEGORY_ID}\",
      \"sku\":\"${DEMO_SLUG}-declined\",
      \"name\":\"Demo Declined Product\",
      \"description\":\"Local declined demo product\",
      \"priceAmount\":400.00,
      \"currency\":\"USD\",
      \"status\":\"ACTIVE\",
      \"attributes\":[{\"attributeKey\":\"demo-run\",\"attributeValue\":\"${DEMO_SLUG}\"}]
    }" \
  | jq -r '.id'
)"
```

Customer browse/detail should see both active products:

```bash
curl -fsS "http://localhost:8080/api/catalog/products?q=${DEMO_SLUG}&size=10" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" | jq .

curl -fsS "http://localhost:8080/api/catalog/products/${APPROVED_PRODUCT_ID}" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" | jq .
```

## Happy Path

Create an order for the approved product:

```bash
export APPROVED_ORDER_ID="$(
  curl -fsS -X POST "http://localhost:8080/api/orders/customer/orders" \
    -H "Authorization: Bearer $CUSTOMER_TOKEN" \
    -H "Content-Type: application/json" \
    -H "X-Correlation-Id: ${DEMO_SLUG}-approved" \
    -d "{\"items\":[{\"productId\":\"${APPROVED_PRODUCT_ID}\",\"quantity\":1}]}" \
  | jq -r '.orderId'
)"
```

Poll until the order reaches `PAID`:

```bash
for attempt in $(seq 1 60); do
  curl -fsS -H "Authorization: Bearer $CUSTOMER_TOKEN" \
    "http://localhost:8080/api/orders/customer/orders/${APPROVED_ORDER_ID}" \
    | jq '{orderId,status}'
  status="$(
    curl -fsS -H "Authorization: Bearer $CUSTOMER_TOKEN" \
      "http://localhost:8080/api/orders/customer/orders/${APPROVED_ORDER_ID}" \
    | jq -r '.status'
  )"
  [ "$status" = "PAID" ] && break
  sleep 2
done
```

Expected result:

- Order status becomes `PAID`.
- Payment status becomes `SUCCEEDED`.
- The mock provider ledger contains one authorization for the order.
- MongoDB contains one `OrderCreated` and one `PaymentSucceeded` audit event.
- MongoDB contains one `SENT` `EMAIL` and one `SENT` `PUSH` delivery for each
  of those events.

Payment API evidence:

```bash
curl -fsS "http://localhost:8080/api/payments/customer/payments/by-order/${APPROVED_ORDER_ID}" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" | jq .
```

Mock provider evidence:

```bash
curl -fsS "http://localhost:8089/api/mock-payments/requests?orderId=${APPROVED_ORDER_ID}" | jq .
```

Audit and notification evidence:

```bash
docker compose exec -T mongodb mongosh \
  --quiet \
  --username ecommerce \
  --password change-me-local-mongo \
  --authenticationDatabase admin \
  audit \
  --eval "db.audit_events.find({order_id:'${APPROVED_ORDER_ID}'},{_id:0,event_id:1,event_type:1,order_id:1,payment_id:1,correlation_id:1}).sort({event_type:1}).toArray()"

docker compose exec -T mongodb mongosh \
  --quiet \
  --username ecommerce \
  --password change-me-local-mongo \
  --authenticationDatabase admin \
  audit \
  --eval "db.notification_deliveries.find({order_id:'${APPROVED_ORDER_ID}'},{_id:0,event_id:1,event_type:1,channel:1,status:1,attempt_count:1}).sort({event_type:1,channel:1}).toArray()"
```

## Payment-Failure Path

Create an order for the declined product:

```bash
export DECLINED_ORDER_ID="$(
  curl -fsS -X POST "http://localhost:8080/api/orders/customer/orders" \
    -H "Authorization: Bearer $CUSTOMER_TOKEN" \
    -H "Content-Type: application/json" \
    -H "X-Correlation-Id: ${DEMO_SLUG}-declined" \
    -d "{\"items\":[{\"productId\":\"${DECLINED_PRODUCT_ID}\",\"quantity\":1}]}" \
  | jq -r '.orderId'
)"
```

Poll until the order reaches `PAYMENT_FAILED`:

```bash
for attempt in $(seq 1 60); do
  curl -fsS -H "Authorization: Bearer $CUSTOMER_TOKEN" \
    "http://localhost:8080/api/orders/customer/orders/${DECLINED_ORDER_ID}" \
    | jq '{orderId,status}'
  status="$(
    curl -fsS -H "Authorization: Bearer $CUSTOMER_TOKEN" \
      "http://localhost:8080/api/orders/customer/orders/${DECLINED_ORDER_ID}" \
    | jq -r '.status'
  )"
  [ "$status" = "PAYMENT_FAILED" ] && break
  sleep 2
done
```

Expected result:

- Order status becomes `PAYMENT_FAILED`.
- Payment status becomes `FAILED`.
- The mock provider ledger contains one declined authorization for the order.
- MongoDB contains one `OrderCreated` and one `PaymentFailed` audit event.
- MongoDB contains one `SENT` `EMAIL` and one `SENT` `PUSH` delivery for each
  of those events.

Payment and provider evidence:

```bash
curl -fsS "http://localhost:8080/api/payments/customer/payments/by-order/${DECLINED_ORDER_ID}" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" | jq .

curl -fsS "http://localhost:8089/api/mock-payments/requests?orderId=${DECLINED_ORDER_ID}" | jq .
```

Audit evidence:

```bash
docker compose exec -T mongodb mongosh \
  --quiet \
  --username ecommerce \
  --password change-me-local-mongo \
  --authenticationDatabase admin \
  audit \
  --eval "db.audit_events.find({order_id:'${DECLINED_ORDER_ID}'},{_id:0,event_id:1,event_type:1,order_id:1,payment_id:1,correlation_id:1}).sort({event_type:1}).toArray()"

docker compose exec -T mongodb mongosh \
  --quiet \
  --username ecommerce \
  --password change-me-local-mongo \
  --authenticationDatabase admin \
  audit \
  --eval "db.notification_deliveries.find({order_id:'${DECLINED_ORDER_ID}'},{_id:0,event_id:1,event_type:1,channel:1,status:1,attempt_count:1}).sort({event_type:1,channel:1}).toArray()"
```

## Operational Evidence

Connector status:

```bash
curl -fsS http://localhost:8086/connectors/ecommerce-order-outbox-v1/status | jq .
curl -fsS http://localhost:8086/connectors/ecommerce-payment-outbox-v1/status | jq .
```

Kafka topics:

```bash
docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --describe \
  --topic ecommerce.order.events

docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --describe \
  --topic ecommerce.payment.events
```

Metrics and traces:

- Prometheus targets: `http://localhost:9090/targets`
- Jaeger UI: `http://localhost:16686`
- Service health: `http://localhost:8080/actuator/health`
- Gateway metrics: `http://localhost:8080/actuator/prometheus`

For DLT depth inspection and a bounded local DLT simulation, use the
[observability runbook](observability-runbook.md#bounded-dead-letter-signal-simulation).
For duplicate replay checks, use the E2E command above or the
[resilience validation runbook](resilience-validation.md).
