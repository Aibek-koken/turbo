# Service-Level RBAC Baseline

All business services are OAuth2 resource servers. The gateway validates JWTs at
the edge, and each downstream service repeats local authorization so bypassing
the gateway does not create an unprotected API surface.

## JWT Role Mapping

Local Keycloak realm roles are read from:

```text
realm_access.roles
```

The shared `libs/security-support` helper maps those role values to Spring
Security authorities with the `ROLE_` prefix. The baseline roles are:

- `CUSTOMER`
- `CATALOG_ADMIN`
- `OPS_ADMIN`

Each service uses the same default issuer and JWK set settings as the gateway:

```text
ECOMMERCE_SECURITY_ISSUER_URI
ECOMMERCE_SECURITY_JWK_SET_URI
```

## Service Rules

Actuator health, info and Prometheus endpoints remain public for local
infrastructure checks. All other unmatched paths are denied by default.

| Service | Path family | Roles |
| --- | --- | --- |
| Catalog | `GET /api/catalog/products/**`, `GET /api/catalog/categories/**` | `CUSTOMER`, `CATALOG_ADMIN`, `OPS_ADMIN` |
| Catalog | `/api/catalog/admin/**` | `CATALOG_ADMIN` |
| Order | `/api/orders/customer/**` | `CUSTOMER`, `OPS_ADMIN` |
| Order | `/api/orders/ops/**` | `OPS_ADMIN` |
| Payment | `/api/payments/customer/**` | `CUSTOMER`, `OPS_ADMIN` |
| Payment | `/api/payments/ops/**` | `OPS_ADMIN` |
| Audit Notification | `/api/audit-notifications/customer/**` | `CUSTOMER`, `OPS_ADMIN` |
| Audit Notification | `/api/audit-notifications/ops/**` | `OPS_ADMIN` |

Temporary RBAC probe endpoints live under those same path families and should be
removed or replaced once real APIs cover the same authorization behavior.

## Customer-Scoped Follow-Up Rules

Role checks are necessary but not sufficient for customer-owned data.

- Catalog customer reads are customer-facing, not customer-owned. Later browse
  and detail APIs must expose only customer-visible product/category state and
  must keep admin write paths under `CATALOG_ADMIN`.
- Order customer APIs must compare the authenticated principal to the persisted
  order owner before returning, updating or cancelling an order. Use a stable
  customer identifier from the JWT, preferably a future explicit customer claim;
  until that exists, use `sub` consistently.
- Payment customer APIs must apply the same owner check through the related
  order/customer reference before exposing payment status.
- `OPS_ADMIN` is reserved for operational support views and may bypass
  customer ownership checks only on explicitly `/ops/` paths.
