# E-Commerce Microservices Portfolio

Java 21 / Spring Boot 3.x monorepo for a portfolio-grade e-commerce platform.
The project is organized as independently deployable service modules with
service-owned data boundaries.

## Services

- `services/gateway-service` - API entry point and future JWT-validating Spring Cloud Gateway.
- `services/catalog-service` - catalog API boundary and future catalog PostgreSQL owner.
- `services/order-service` - order API boundary and future order/outbox PostgreSQL owner.
- `services/payment-service` - payment boundary and future idempotent payment event consumer.
- `services/audit-notification-service` - audit and notification boundary for future MongoDB/Kafka work.

No shared business domain module exists yet. Add a shared `libs` module only
when event contracts or infrastructure helpers create real duplication.

## Local Setup

Prerequisites:

- Java 21
- Maven 3.9+
- Docker with Docker Compose for later infrastructure tasks

Useful commands:

```bash
scripts/project-validate.sh structure
mvn -DskipTests validate
mvn -pl services/catalog-service -am spring-boot:run
```

Default service ports:

| Service | Port |
| --- | ---: |
| gateway-service | 8080 |
| catalog-service | 8081 |
| order-service | 8082 |
| payment-service | 8083 |
| audit-notification-service | 8084 |

Each service exposes actuator health on `/actuator/health`. Prometheus exposure
is configured for the services and will become useful once the observability
dependencies and local infrastructure are expanded in later tasks.

See [docs/developer-setup.md](docs/developer-setup.md) for setup notes.
