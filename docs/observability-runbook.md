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

Prometheus uses `infra/observability/prometheus.yml` and scrapes:

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
- Log patterns include `application`, `traceId`, `spanId` and `correlationId`.

Future Kafka producers and consumers should copy the same correlation ID into
message headers and into the event envelope `correlationId` field.

## Quick Checks

```bash
curl -i http://localhost:8080/actuator/health
curl -i -H 'X-Correlation-Id: local-check-1' http://localhost:8081/actuator/health
curl -fsS http://localhost:8080/actuator/prometheus | head
```

If Prometheus targets are down, confirm the service is running on its default
host port and that Docker can resolve `host.docker.internal`.
