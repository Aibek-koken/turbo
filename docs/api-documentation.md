# API Documentation

The four HTTP business services publish Springdoc OpenAPI JSON and Swagger UI
from their local service ports. These documentation endpoints are public for
local development, but the documented business endpoints still require bearer
JWT authentication and service-level RBAC.

| Service | OpenAPI JSON | Swagger UI |
| --- | --- | --- |
| Catalog | `http://localhost:8081/v3/api-docs` | `http://localhost:8081/swagger-ui/index.html` |
| Order | `http://localhost:8082/v3/api-docs` | `http://localhost:8082/swagger-ui/index.html` |
| Payment | `http://localhost:8083/v3/api-docs` | `http://localhost:8083/swagger-ui/index.html` |
| Audit Notification | `http://localhost:8084/v3/api-docs` | `http://localhost:8084/swagger-ui/index.html` |

The Gateway continues to route runtime API traffic on `/api/**`; OpenAPI is
served directly by each service so the docs describe service-owned contracts
without introducing Gateway aggregation state.

## Security

Each OpenAPI document declares the `bearer-jwt` HTTP security scheme. Tokens
come from the local Keycloak `ecommerce` realm, and service authorization still
uses realm roles mapped from `realm_access.roles`.

| API family | Roles |
| --- | --- |
| Catalog browse: `GET /api/catalog/products/**` | `CUSTOMER`, `CATALOG_ADMIN`, `OPS_ADMIN` |
| Catalog admin: `/api/catalog/admin/**` | `CATALOG_ADMIN` |
| Order customer: `/api/orders/customer/**` | `CUSTOMER` |
| Order ops: `/api/orders/ops/**` | `OPS_ADMIN` |
| Payment customer: `/api/payments/customer/**` | `CUSTOMER`, `OPS_ADMIN`, scoped by JWT subject |
| Payment ops: `/api/payments/ops/**` | `OPS_ADMIN` |
| Audit notification customer: `/api/audit-notifications/customer/**` | `CUSTOMER`, `OPS_ADMIN`, scoped by JWT subject |
| Audit notification ops: `/api/audit-notifications/ops/**` | `OPS_ADMIN` |

Actuator endpoints are intentionally not included in the public OpenAPI paths.
Temporary RBAC probe endpoints are also hidden from the public API description.

## Published APIs

Catalog documents customer browse/detail operations, category/product admin
operations and supplier CSV import launch/status operations. Catalog browse
uses zero-based `page` and `size` query parameters and returns page metadata in
the response body.

Order documents customer order creation, customer-owned order list/detail
queries, ops order lookup and ops state transition. Customer order list uses
zero-based `page` and `size` query parameters and returns page metadata.

Payment documents read-only customer and ops payment status lookups:

- `GET /api/payments/customer/payments/by-order/{orderId}`
- `GET /api/payments/ops/payments/{paymentId}`
- `GET /api/payments/ops/payments/by-order/{orderId}`

Audit Notification documents customer notification delivery lookup and ops
audit-event lookup:

- `GET /api/audit-notifications/customer/orders/{orderId}/notifications`
- `GET /api/audit-notifications/ops/audit-events/{eventId}`
- `GET /api/audit-notifications/ops/orders/{orderId}/audit-events`

Ops audit-event lists use zero-based `page` and `size` query parameters.

## Error Shape

Validation, lookup and state-transition failures are returned as RFC 9457
Problem Details JSON. The service-specific handlers add safe diagnostic fields
such as `failure`, `orderId`, `paymentId`, `productId`, `eventId` or
`maxAllowed` when applicable. Authentication and authorization failures remain
handled by Spring Security as `401` or `403` responses and do not weaken the
protected business endpoints.

## Validation

OpenAPI coverage is exercised by:

```bash
scripts/project-validate.sh api-docs-test
```

The validation runs `mvn test`, including focused tests that verify document
availability, key documented paths, bearer security metadata, hidden actuator
and probe endpoints, customer ownership checks and unchanged RBAC behavior.
