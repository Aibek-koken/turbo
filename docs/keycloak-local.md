# Local Keycloak

The local Keycloak container imports `infra/keycloak/ecommerce-realm.json` when
it starts with an empty Keycloak database.

## Realm

- Realm: `ecommerce`
- Issuer: `http://localhost:8085/realms/ecommerce`
- Roles: `CUSTOMER`, `CATALOG_ADMIN`, `OPS_ADMIN`

## Clients

- `ecommerce-gateway` is the local gateway/API audience used for JWT validation.
- `ecommerce-local-test` is a public local-only client for curl, Postman and
  gateway API testing.

No test users, passwords, client secrets or generated tokens are checked in.
Create local users in the Keycloak admin console and assign one or more realm
roles when you need a token for manual testing.

## Placeholder Token Request

Use placeholders for local-only credentials:

```bash
curl -sS -X POST "http://localhost:8085/realms/ecommerce/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=ecommerce-local-test" \
  -d "username=<local-test-username>" \
  -d "password=<local-test-password>" \
  -d "scope=openid profile email"
```

The access token should contain assigned realm roles under `realm_access.roles`
and the gateway audience added by the local test client mapper.

If the realm was already created before the import file changed, update the
realm through the admin console or reset only your disposable local Keycloak
database before starting Compose again.
