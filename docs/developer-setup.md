# Developer Setup

## Toolchain

Use Java 21 for builds and runtime. The Maven parent config targets Java 21 even
if the local shell points at an older JDK.

Check local versions:

```bash
java -version
mvn -version
```

## Project Layout

The root `pom.xml` is an aggregator and dependency-management parent. Service
modules live under `services/` and are designed to produce independent Spring
Boot applications.

Current modules:

- `services/gateway-service`
- `services/catalog-service`
- `services/order-service`
- `services/payment-service`
- `services/audit-notification-service`

Shared libraries are intentionally absent in the initial scaffold. Prefer
service-local code until a later task introduces contracts or helpers that are
actually duplicated across services.

## Validation

Run the structure validation after scaffold changes:

```bash
scripts/project-validate.sh structure
```

Run Maven validation when Java 21 is available:

```bash
mvn -DskipTests validate
```

If Maven validation fails because the active JDK is older than Java 21, switch
`JAVA_HOME` to a Java 21 installation and rerun.
