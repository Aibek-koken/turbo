# Load Validation

This project treats load work as local evidence, not as a production capacity
claim. The checked-in scenario is versioned as `Catalog Gateway load scenario
v1` and sends authenticated, health-safe Catalog browse/detail traffic through
Gateway only.

## Smoke Command

Run the required smoke validation with:

```bash
scripts/project-validate.sh load-smoke
```

The wrapper calls:

```bash
scripts/run-load-validation.sh --smoke
```

Smoke defaults are deliberately small: 2 virtual users, 8 seconds, no tolerated
request errors, p95 latency at or below 5000 ms, and at least 0.20 requests per
second. If Gateway is not already ready, the script attempts to start the local
Catalog/Gateway Compose slice with existing images and the bounded resource
override in `tests/load/docker-compose.load.yml`: PostgreSQL, Redis, Keycloak,
Jaeger, Catalog Service and Gateway. Gateway is started with `--no-deps` after
the Catalog slice is ready so unrelated Order, Payment, Audit or mock-provider
health does not block this Catalog-only smoke. It does not rebuild images,
remove volumes, reset databases or delete application data.

## What The Script Does

The script:

- waits for Keycloak, Catalog and Gateway readiness with a bounded deadline
- creates isolated local-only Keycloak users for `CATALOG_ADMIN` and `CUSTOMER`
- creates one active Catalog category/product fixture through Gateway
- performs one cold product-detail read, then bounded warm detail reads
- verifies `ecommerce_cache_requests_total` miss/hit deltas for the
  `product-detail` cache
- records a best-effort PostgreSQL `pg_stat_database` tuple-read observation
  for the `catalog` database when the Compose PostgreSQL service is reachable
- runs the bounded browse/detail load driver
- compiles and runs a Java 21 virtual-thread runtime probe with bounded tasks

The generated Keycloak users are deleted at the end. Catalog fixture rows remain
in local development data, matching the non-destructive behavior of the E2E
harness.

## Higher Local Load

The default and smoke profiles are capped for developer machines. A larger local
profile requires explicit opt-in:

```bash
scripts/run-load-validation.sh --profile higher --allow-higher-load
```

Even with opt-in, this harness caps load at 32 virtual users, 180 seconds and
50 warm cache reads. Do not use it as an unbounded stress test.

Useful overrides:

```bash
scripts/run-load-validation.sh --skip-up --base-url http://localhost:8080
scripts/run-load-validation.sh --profile local --vus 6 --duration 45
LOAD_CUSTOMER_TOKEN=... LOAD_PRODUCT_ID=... LOAD_QUERY=... scripts/run-load-validation.sh --skip-up --smoke
```

When caller-provided tokens and product IDs are used, the script skips fixture
creation and uses the supplied product for browse/detail traffic.

## Reading The Output

The load driver prints:

- total, successful and failed request counts
- throughput in requests per second
- error rate
- latency min, mean, p50, p90, p95, p99 and max
- the thresholds used for the run

The cache section prints cold miss and warm hit deltas. A passing smoke should
show at least one cold product-detail miss, at least as many warm hits as the
configured warm-read count, and zero warm misses. When the PostgreSQL
observation is available, the warm tuple-read delta should stay no higher than
the cold delta, demonstrating that repeated detail reads use the cache instead
of repeating the cold database work.
