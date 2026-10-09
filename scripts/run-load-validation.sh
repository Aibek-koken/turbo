#!/usr/bin/env bash
set -euo pipefail

PROFILE="${LOAD_PROFILE:-local}"
BASE_URL="${LOAD_BASE_URL:-}"
VUS="${LOAD_VUS:-}"
DURATION_SECONDS="${LOAD_DURATION_SECONDS:-}"
MAX_P95_MS="${LOAD_MAX_P95_MS:-}"
MAX_ERROR_RATE="${LOAD_MAX_ERROR_RATE:-}"
MIN_THROUGHPUT_RPS="${LOAD_MIN_THROUGHPUT_RPS:-}"
WARM_READS="${LOAD_CACHE_WARM_READS:-}"
BROWSE_EVERY="${LOAD_BROWSE_EVERY:-4}"
REQUEST_TIMEOUT_SECONDS="${LOAD_REQUEST_TIMEOUT_SECONDS:-15}"
READINESS_TIMEOUT_SECONDS="${LOAD_READINESS_TIMEOUT_SECONDS:-20}"
STACK_TIMEOUT_SECONDS="${LOAD_STACK_TIMEOUT_SECONDS:-600}"
POLL_SECONDS="${LOAD_POLL_SECONDS:-2}"
START_STACK="${LOAD_START_STACK:-auto}"
ALLOW_HIGHER_LOAD="${LOAD_ALLOW_HIGHER:-false}"
VIRTUAL_THREAD_TASKS="${LOAD_VIRTUAL_THREAD_TASKS:-16}"
COMPOSE_OVERRIDE_FILE="${LOAD_COMPOSE_OVERRIDE_FILE:-tests/load/docker-compose.load.yml}"
CLEANUP_KEYCLOAK_USERS=true

usage() {
  cat <<'USAGE'
Usage: scripts/run-load-validation.sh [options]

Runs a bounded Catalog browse/detail load scenario through Gateway and prints
latency percentiles, throughput, error rate, cache evidence and a Java 21
virtual-thread runtime observation.

Options:
  --smoke                 Use CI/developer-safe smoke defaults.
  --profile NAME          One of smoke, local or higher (default: local).
  --allow-higher-load     Required for the higher profile or larger overrides.
  --base-url URL          Gateway base URL (default: http://localhost:8080).
  --vus COUNT             Virtual users for the load phase.
  --duration SECONDS      Duration for the load phase.
  --p95-threshold-ms MS   p95 latency smoke threshold.
  --max-error-rate RATE   Error-rate smoke threshold, e.g. 0.01.
  --min-throughput-rps N  Minimum throughput smoke threshold.
  --warm-reads COUNT      Sequential warm product-detail reads for cache proof.
  --up                    Start the Compose stack with existing images if needed.
  --skip-up               Require an already-running stack.
  -h, --help              Show this help.

Environment overrides mirror the LOAD_* option names. The higher profile is
still capped and must be explicitly opted in; this script never runs an
unbounded stress test.
USAGE
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --smoke)
      PROFILE=smoke
      shift
      ;;
    --profile)
      PROFILE="$2"
      shift 2
      ;;
    --allow-higher-load)
      ALLOW_HIGHER_LOAD=true
      shift
      ;;
    --base-url)
      BASE_URL="$2"
      shift 2
      ;;
    --vus)
      VUS="$2"
      shift 2
      ;;
    --duration)
      DURATION_SECONDS="$2"
      shift 2
      ;;
    --p95-threshold-ms)
      MAX_P95_MS="$2"
      shift 2
      ;;
    --max-error-rate)
      MAX_ERROR_RATE="$2"
      shift 2
      ;;
    --min-throughput-rps)
      MIN_THROUGHPUT_RPS="$2"
      shift 2
      ;;
    --warm-reads)
      WARM_READS="$2"
      shift 2
      ;;
    --up)
      START_STACK=true
      shift
      ;;
    --skip-up)
      START_STACK=false
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

need_command() {
  local command_name="$1"
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "Missing required command: ${command_name}" >&2
    exit 2
  fi
}

load_env_file() {
  local env_file=".env"
  local line key value

  if [ ! -f "$env_file" ]; then
    return
  fi

  while IFS= read -r line || [ -n "$line" ]; do
    case "$line" in
      ""|\#*) continue ;;
    esac
    key="${line%%=*}"
    value="${line#*=}"
    case "$key" in
      ""|*[!A-Za-z0-9_]*)
        continue
        ;;
    esac
    if [ -z "${!key+x}" ]; then
      case "$value" in
        \"*\") value="${value#\"}"; value="${value%\"}" ;;
        \'*\') value="${value#\'}"; value="${value%\'}" ;;
      esac
      export "$key=$value"
    fi
  done < "$env_file"
}

configure_compose_files() {
  local separator="${COMPOSE_PATH_SEPARATOR:-:}"

  if [ -z "$COMPOSE_OVERRIDE_FILE" ]; then
    return
  fi
  if [ ! -f "$COMPOSE_OVERRIDE_FILE" ]; then
    echo "Missing load Compose override: ${COMPOSE_OVERRIDE_FILE}" >&2
    exit 2
  fi
  if [ -n "${COMPOSE_FILE:-}" ]; then
    export COMPOSE_FILE="${COMPOSE_FILE}${separator}${COMPOSE_OVERRIDE_FILE}"
  else
    export COMPOSE_FILE="docker-compose.yml${separator}${COMPOSE_OVERRIDE_FILE}"
  fi
}

is_positive_int() {
  case "$1" in
    ''|*[!0-9]*)
      return 1
      ;;
    0)
      return 1
      ;;
    *)
      return 0
      ;;
  esac
}

apply_profile_defaults() {
  case "$PROFILE" in
    smoke)
      VUS="${VUS:-2}"
      DURATION_SECONDS="${DURATION_SECONDS:-8}"
      MAX_P95_MS="${MAX_P95_MS:-5000}"
      MAX_ERROR_RATE="${MAX_ERROR_RATE:-0.00}"
      MIN_THROUGHPUT_RPS="${MIN_THROUGHPUT_RPS:-0.20}"
      WARM_READS="${WARM_READS:-5}"
      ;;
    local)
      VUS="${VUS:-4}"
      DURATION_SECONDS="${DURATION_SECONDS:-30}"
      MAX_P95_MS="${MAX_P95_MS:-2500}"
      MAX_ERROR_RATE="${MAX_ERROR_RATE:-0.01}"
      MIN_THROUGHPUT_RPS="${MIN_THROUGHPUT_RPS:-1.00}"
      WARM_READS="${WARM_READS:-8}"
      ;;
    higher)
      if [ "$ALLOW_HIGHER_LOAD" != true ]; then
        echo "The higher load profile requires --allow-higher-load or LOAD_ALLOW_HIGHER=true." >&2
        exit 2
      fi
      VUS="${VUS:-12}"
      DURATION_SECONDS="${DURATION_SECONDS:-60}"
      MAX_P95_MS="${MAX_P95_MS:-3000}"
      MAX_ERROR_RATE="${MAX_ERROR_RATE:-0.02}"
      MIN_THROUGHPUT_RPS="${MIN_THROUGHPUT_RPS:-2.00}"
      WARM_READS="${WARM_READS:-10}"
      ;;
    *)
      echo "Unsupported load profile: ${PROFILE}" >&2
      exit 2
      ;;
  esac
}

enforce_bounds() {
  if ! is_positive_int "$VUS"; then
    echo "Virtual users must be a positive integer." >&2
    exit 2
  fi
  if ! is_positive_int "$DURATION_SECONDS"; then
    echo "Duration must be a positive integer." >&2
    exit 2
  fi
  if ! is_positive_int "$WARM_READS"; then
    echo "Warm reads must be a positive integer." >&2
    exit 2
  fi
  if ! is_positive_int "$BROWSE_EVERY"; then
    echo "Browse cadence must be a positive integer." >&2
    exit 2
  fi

  if [ "$ALLOW_HIGHER_LOAD" != true ]; then
    if [ "$VUS" -gt 8 ] || [ "$DURATION_SECONDS" -gt 60 ]; then
      echo "More than 8 virtual users or 60 seconds requires --allow-higher-load." >&2
      exit 2
    fi
  fi

  if [ "$VUS" -gt 32 ]; then
    echo "Virtual users are capped at 32 for this local validation." >&2
    exit 2
  fi
  if [ "$DURATION_SECONDS" -gt 180 ]; then
    echo "Duration is capped at 180 seconds for this local validation." >&2
    exit 2
  fi
  if [ "$WARM_READS" -gt 50 ]; then
    echo "Warm cache reads are capped at 50 for this local validation." >&2
    exit 2
  fi
}

now_seconds() {
  date +%s
}

time_left() {
  local now
  now="$(now_seconds)"
  echo $((DEADLINE_SECONDS - now))
}

wait_until() {
  local label="$1"
  shift

  echo "Waiting for ${label}..."
  until "$@"; do
    if [ "$(time_left)" -le 0 ]; then
      echo "Timed out waiting for ${label}." >&2
      exit 1
    fi
    sleep "$POLL_SECONDS"
  done
  echo "OK: ${label}"
}

curl_ok() {
  curl -fsS --connect-timeout "$REQUEST_TIMEOUT_SECONDS" --max-time "$READINESS_TIMEOUT_SECONDS" "$1" >/dev/null
}

curl_json() {
  curl -fsS --connect-timeout "$REQUEST_TIMEOUT_SECONDS" --max-time "$REQUEST_TIMEOUT_SECONDS" "$@"
}

curl_keycloak_json() {
  curl -fsS --connect-timeout "$KEYCLOAK_CURL_TIMEOUT_SECONDS" --max-time "$KEYCLOAK_CURL_TIMEOUT_SECONDS" "$@"
}

curl_json_body() {
  local method="$1"
  local url="$2"
  local token="$3"
  local body="$4"

  curl_json \
    -X "$method" \
    -H "Authorization: Bearer ${token}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    --data "$body" \
    "$url"
}

curl_get_json() {
  local url="$1"
  local token="$2"

  curl_json \
    -H "Authorization: Bearer ${token}" \
    -H "Accept: application/json" \
    "$url"
}

random_suffix() {
  if command -v uuidgen >/dev/null 2>&1; then
    uuidgen | tr '[:upper:]' '[:lower:]' | tr -d '-' | cut -c1-12
    return
  fi
  printf '%s%s' "$(date -u +%s)" "$$" | cut -c1-12
}

start_stack_if_needed() {
  if curl_ok "${BASE_URL}/actuator/health/readiness" 2>/dev/null \
      && curl_ok "${CATALOG_URL}/actuator/health/readiness" 2>/dev/null; then
    return
  fi
  if [ "$START_STACK" = false ]; then
    echo "Gateway is not ready at ${BASE_URL}. Start the stack or omit --skip-up." >&2
    exit 1
  fi
  if [ "$START_STACK" = true ] || [ "$START_STACK" = auto ]; then
    need_command docker
    echo "Gateway is not ready; starting the local Catalog/Gateway Compose slice with existing images..."
    COMPOSE_BAKE="${COMPOSE_BAKE:-false}" COMPOSE_PARALLEL_LIMIT="${COMPOSE_PARALLEL_LIMIT:-1}" \
      docker compose up -d --no-build postgres redis keycloak jaeger catalog-service
    COMPOSE_BAKE="${COMPOSE_BAKE:-false}" COMPOSE_PARALLEL_LIMIT="${COMPOSE_PARALLEL_LIMIT:-1}" \
      docker compose up -d --no-build --no-deps gateway-service
  fi
}

keycloak_admin_token() {
  curl_keycloak_json \
    -X POST "${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    --data-urlencode "grant_type=password" \
    --data-urlencode "client_id=admin-cli" \
    --data-urlencode "username=${KEYCLOAK_ADMIN_USER}" \
    --data-urlencode "password=${KEYCLOAK_ADMIN_PASSWORD_VALUE}" \
    | jq -r '.access_token'
}

keycloak_admin_token_available() {
  local token
  token="$(keycloak_admin_token 2>/dev/null || true)"
  [ -n "$token" ] && [ "$token" != null ]
}

keycloak_role_json() {
  local admin_token="$1"
  local role="$2"

  curl_keycloak_json \
    -H "Authorization: Bearer ${admin_token}" \
    "${KEYCLOAK_URL}/admin/realms/${KEYCLOAK_REALM}/roles/${role}"
}

keycloak_user_id_by_username() {
  local admin_token="$1"
  local username="$2"

  curl_keycloak_json \
    -H "Authorization: Bearer ${admin_token}" \
    --get \
    --data-urlencode "username=${username}" \
    --data-urlencode "exact=true" \
    "${KEYCLOAK_URL}/admin/realms/${KEYCLOAK_REALM}/users" \
    | jq -r '.[0].id // empty'
}

keycloak_client_id_by_client_id() {
  local admin_token="$1"
  local client_id="$2"

  curl_keycloak_json \
    -H "Authorization: Bearer ${admin_token}" \
    --get \
    --data-urlencode "clientId=${client_id}" \
    "${KEYCLOAK_URL}/admin/realms/${KEYCLOAK_REALM}/clients" \
    | jq -r '.[0].id // empty'
}

keycloak_client_mappers() {
  local admin_token="$1"
  local client_uuid="$2"

  curl_keycloak_json \
    -H "Authorization: Bearer ${admin_token}" \
    "${KEYCLOAK_URL}/admin/realms/${KEYCLOAK_REALM}/clients/${client_uuid}/protocol-mappers/models"
}

ensure_keycloak_mapper() {
  local admin_token="$1"
  local client_uuid="$2"
  local mapper_name="$3"
  local mapper_json="$4"

  if keycloak_client_mappers "$admin_token" "$client_uuid" \
      | jq -e --arg name "$mapper_name" 'any(.[]; .name == $name)' >/dev/null; then
    return
  fi

  curl_keycloak_json \
    -X POST \
    -H "Authorization: Bearer ${admin_token}" \
    -H "Content-Type: application/json" \
    --data "$mapper_json" \
    "${KEYCLOAK_URL}/admin/realms/${KEYCLOAK_REALM}/clients/${client_uuid}/protocol-mappers/models" >/dev/null
}

ensure_local_test_client_mappers() {
  local admin_token="$1"
  local client_uuid realm_roles_mapper audience_mapper

  client_uuid="$(keycloak_client_id_by_client_id "$admin_token" "$KEYCLOAK_LOCAL_TEST_CLIENT")"
  if [ -z "$client_uuid" ]; then
    echo "Could not find Keycloak client ${KEYCLOAK_LOCAL_TEST_CLIENT}." >&2
    exit 1
  fi

  realm_roles_mapper="$(jq -n '{
    name:"realm-roles",
    protocol:"openid-connect",
    protocolMapper:"oidc-usermodel-realm-role-mapper",
    consentRequired:false,
    config:{
      "access.token.claim":"true",
      "claim.name":"realm_access.roles",
      "id.token.claim":"false",
      "jsonType.label":"String",
      "multivalued":"true",
      "userinfo.token.claim":"true"
    }
  }')"
  ensure_keycloak_mapper "$admin_token" "$client_uuid" realm-roles "$realm_roles_mapper"

  audience_mapper="$(jq -n '{
    name:"gateway-audience",
    protocol:"openid-connect",
    protocolMapper:"oidc-audience-mapper",
    consentRequired:false,
    config:{
      "access.token.claim":"true",
      "id.token.claim":"false",
      "included.client.audience":"ecommerce-gateway",
      "included.custom.audience":"",
      "userinfo.token.claim":"false"
    }
  }')"
  ensure_keycloak_mapper "$admin_token" "$client_uuid" gateway-audience "$audience_mapper"
}

create_keycloak_user() {
  local admin_token="$1"
  local username="$2"
  local password="$3"
  local role="$4"
  local body user_id role_json

  body="$(jq -n \
    --arg username "$username" \
    --arg email "${username}@example.invalid" \
    --arg runId "$RUN_ID" \
    '{
      username:$username,
      email:$email,
      firstName:"Local",
      lastName:"Load",
      enabled:true,
      emailVerified:true,
      requiredActions:[],
      attributes:{loadRunId:[$runId]}
    }')"

  curl_keycloak_json \
    -X POST \
    -H "Authorization: Bearer ${admin_token}" \
    -H "Content-Type: application/json" \
    --data "$body" \
    "${KEYCLOAK_URL}/admin/realms/${KEYCLOAK_REALM}/users" >/dev/null

  user_id="$(keycloak_user_id_by_username "$admin_token" "$username")"
  if [ -z "$user_id" ]; then
    echo "Created Keycloak user ${username}, but could not read its ID." >&2
    exit 1
  fi

  body="$(jq -n --arg password "$password" '{type:"password",temporary:false,value:$password}')"
  curl_keycloak_json \
    -X PUT \
    -H "Authorization: Bearer ${admin_token}" \
    -H "Content-Type: application/json" \
    --data "$body" \
    "${KEYCLOAK_URL}/admin/realms/${KEYCLOAK_REALM}/users/${user_id}/reset-password" >/dev/null

  role_json="$(keycloak_role_json "$admin_token" "$role")"
  curl_keycloak_json \
    -X POST \
    -H "Authorization: Bearer ${admin_token}" \
    -H "Content-Type: application/json" \
    --data "[$role_json]" \
    "${KEYCLOAK_URL}/admin/realms/${KEYCLOAK_REALM}/users/${user_id}/role-mappings/realm" >/dev/null

  echo "$user_id"
}

delete_keycloak_users() {
  local admin_token user_id

  if [ "$CLEANUP_KEYCLOAK_USERS" != true ] || [ "${#CREATED_KEYCLOAK_USER_IDS[@]}" -eq 0 ]; then
    return
  fi

  admin_token="$(keycloak_admin_token || true)"
  if [ -z "$admin_token" ] || [ "$admin_token" = null ]; then
    echo "Could not obtain a Keycloak admin token for load-user cleanup." >&2
    return
  fi

  for user_id in "${CREATED_KEYCLOAK_USER_IDS[@]}"; do
    curl -fsS \
      --connect-timeout "$KEYCLOAK_CURL_TIMEOUT_SECONDS" \
      --max-time "$KEYCLOAK_CURL_TIMEOUT_SECONDS" \
      -X DELETE \
      -H "Authorization: Bearer ${admin_token}" \
      "${KEYCLOAK_URL}/admin/realms/${KEYCLOAK_REALM}/users/${user_id}" >/dev/null || true
  done
}

user_token() {
  local username="$1"
  local password="$2"

  curl_keycloak_json \
    -X POST "${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    --data-urlencode "grant_type=password" \
    --data-urlencode "client_id=${KEYCLOAK_LOCAL_TEST_CLIENT}" \
    --data-urlencode "username=${username}" \
    --data-urlencode "password=${password}" \
    --data-urlencode "scope=openid profile" \
    | jq -r '.access_token'
}

create_category() {
  local token="$1"
  local slug="$2"
  local body

  body="$(jq -n \
    --arg name "Load ${slug}" \
    --arg slug "$slug" \
    '{name:$name,slug:$slug,description:"Local load validation fixture category"}')"

  curl_json_body POST "${BASE_URL}/api/catalog/admin/categories" "$token" "$body"
}

create_product() {
  local token="$1"
  local category_id="$2"
  local sku="$3"
  local body

  body="$(jq -n \
    --arg categoryId "$category_id" \
    --arg sku "$sku" \
    '{
      categoryId:$categoryId,
      sku:$sku,
      name:"Load Validation Product",
      description:"Local bounded load validation fixture product",
      priceAmount:19.99,
      currency:"USD",
      status:"ACTIVE",
      attributes:[{attributeKey:"load-run",attributeValue:$sku}]
    }')"

  curl_json_body POST "${BASE_URL}/api/catalog/admin/products" "$token" "$body"
}

assert_product_visible() {
  local list detail encoded_sku

  encoded_sku="$(jq -rn --arg value "$PRODUCT_SKU" '$value | @uri')"
  list="$(curl_get_json "${BASE_URL}/api/catalog/products?q=${encoded_sku}&size=10" "$CUSTOMER_TOKEN")"
  echo "$list" | jq -e --arg productId "$PRODUCT_ID" '.content | any(.id == $productId)' >/dev/null

  detail="$(curl_get_json "${BASE_URL}/api/catalog/products/${PRODUCT_ID}" "$CUSTOMER_TOKEN")"
  echo "$detail" | jq -e --arg productId "$PRODUCT_ID" '.id == $productId and .status == "ACTIVE"' >/dev/null
}

create_load_fixture() {
  local admin_token category_json category_id product_json admin_user_id customer_user_id

  if [ -n "${LOAD_CUSTOMER_TOKEN:-}" ] && [ -n "${LOAD_PRODUCT_ID:-}" ] && [ -n "${LOAD_QUERY:-}" ]; then
    CUSTOMER_TOKEN="$LOAD_CUSTOMER_TOKEN"
    PRODUCT_ID="$LOAD_PRODUCT_ID"
    PRODUCT_SKU="$LOAD_QUERY"
    if [ -n "${LOAD_ADMIN_TOKEN:-}" ]; then
      CATALOG_ADMIN_TOKEN="$LOAD_ADMIN_TOKEN"
    fi
    CLEANUP_KEYCLOAK_USERS=false
    echo "Using caller-provided load fixture product ${PRODUCT_ID}."
    return
  fi

  echo "Creating isolated local Keycloak users for load run ${RUN_ID}..."
  admin_token="$(keycloak_admin_token)"
  if [ -z "$admin_token" ] || [ "$admin_token" = null ]; then
    echo "Could not obtain a Keycloak admin token." >&2
    exit 1
  fi
  ensure_local_test_client_mappers "$admin_token"

  admin_user_id="$(create_keycloak_user "$admin_token" "$ADMIN_USERNAME" "$ADMIN_PASSWORD" CATALOG_ADMIN)"
  customer_user_id="$(create_keycloak_user "$admin_token" "$CUSTOMER_USERNAME" "$CUSTOMER_PASSWORD" CUSTOMER)"
  CREATED_KEYCLOAK_USER_IDS+=("$admin_user_id")
  CREATED_KEYCLOAK_USER_IDS+=("$customer_user_id")

  CATALOG_ADMIN_TOKEN="$(user_token "$ADMIN_USERNAME" "$ADMIN_PASSWORD")"
  CUSTOMER_TOKEN="$(user_token "$CUSTOMER_USERNAME" "$CUSTOMER_PASSWORD")"
  if [ -z "$CATALOG_ADMIN_TOKEN" ] || [ "$CATALOG_ADMIN_TOKEN" = null ] \
    || [ -z "$CUSTOMER_TOKEN" ] || [ "$CUSTOMER_TOKEN" = null ]; then
    echo "Could not obtain local Keycloak load-user tokens." >&2
    exit 1
  fi

  echo "Creating one local Catalog fixture through Gateway..."
  category_json="$(create_category "$CATALOG_ADMIN_TOKEN" "$RUN_SLUG")"
  category_id="$(echo "$category_json" | jq -r '.id')"
  PRODUCT_SKU="${RUN_SLUG}-product"
  product_json="$(create_product "$CATALOG_ADMIN_TOKEN" "$category_id" "$PRODUCT_SKU")"
  PRODUCT_ID="$(echo "$product_json" | jq -r '.id')"
  if [ -z "$PRODUCT_ID" ] || [ "$PRODUCT_ID" = null ]; then
    echo "Catalog product creation did not return an ID." >&2
    exit 1
  fi
}

cache_metric_value() {
  local outcome="$1"

  curl_json "${CATALOG_URL}/actuator/prometheus" \
    | awk -v outcome="$outcome" '
      $1 ~ /^ecommerce_cache_requests_total/ \
        && $0 ~ "service=\"catalog-service\"" \
        && $0 ~ "cache=\"product-detail\"" \
        && $0 ~ "outcome=\"" outcome "\"" {
          value = $NF
        }
      END {
        if (value == "") {
          print 0
        } else {
          print int(value)
        }
      }'
}

catalog_db_read_counter() {
  if ! command -v docker >/dev/null 2>&1; then
    return 1
  fi
  docker compose exec -T postgres \
    psql \
    -v ON_ERROR_STOP=1 \
    -U "$POSTGRES_USER" \
    -d postgres \
    -Atc "select coalesce(tup_returned,0) + coalesce(tup_fetched,0) from pg_stat_database where datname = 'catalog';" 2>/dev/null
}

assert_cache_effect() {
  local before_hit before_miss after_cold_hit after_cold_miss after_warm_hit after_warm_miss
  local cold_miss_delta warm_hit_delta warm_miss_delta db_before db_after_cold db_after_warm
  local cold_db_delta warm_db_delta i

  echo "Checking cold versus warm Catalog product-detail cache behavior..."
  before_hit="$(cache_metric_value hit)"
  before_miss="$(cache_metric_value miss)"
  db_before="$(catalog_db_read_counter || true)"

  assert_product_visible

  after_cold_hit="$(cache_metric_value hit)"
  after_cold_miss="$(cache_metric_value miss)"
  db_after_cold="$(catalog_db_read_counter || true)"

  i=1
  while [ "$i" -le "$WARM_READS" ]; do
    curl_get_json "${BASE_URL}/api/catalog/products/${PRODUCT_ID}" "$CUSTOMER_TOKEN" \
      | jq -e --arg productId "$PRODUCT_ID" '.id == $productId and .status == "ACTIVE"' >/dev/null
    i=$((i + 1))
  done

  after_warm_hit="$(cache_metric_value hit)"
  after_warm_miss="$(cache_metric_value miss)"
  db_after_warm="$(catalog_db_read_counter || true)"

  cold_miss_delta=$((after_cold_miss - before_miss))
  warm_hit_delta=$((after_warm_hit - after_cold_hit))
  warm_miss_delta=$((after_warm_miss - after_cold_miss))

  echo "cache_before hit=${before_hit} miss=${before_miss}"
  echo "cache_after_cold hit=${after_cold_hit} miss=${after_cold_miss} cold_miss_delta=${cold_miss_delta}"
  echo "cache_after_warm hit=${after_warm_hit} miss=${after_warm_miss} warm_hit_delta=${warm_hit_delta} warm_miss_delta=${warm_miss_delta}"

  if [ "$cold_miss_delta" -lt 1 ]; then
    echo "Expected at least one product-detail cache miss for the cold read." >&2
    exit 1
  fi
  if [ "$warm_hit_delta" -lt "$WARM_READS" ]; then
    echo "Expected warm product-detail reads to be served from cache." >&2
    exit 1
  fi
  if [ "$warm_miss_delta" -ne 0 ]; then
    echo "Expected warm product-detail reads not to add cache misses." >&2
    exit 1
  fi

  if [ -n "$db_before" ] && [ -n "$db_after_cold" ] && [ -n "$db_after_warm" ]; then
    cold_db_delta=$((db_after_cold - db_before))
    warm_db_delta=$((db_after_warm - db_after_cold))
    echo "catalog_db_tuple_read_observation before=${db_before} after_cold=${db_after_cold} after_warm=${db_after_warm} cold_delta=${cold_db_delta} warm_delta=${warm_db_delta}"
    if [ "$warm_db_delta" -gt "$cold_db_delta" ]; then
      echo "Warm detail reads touched more database tuple reads than the cold read observation." >&2
      exit 1
    fi
  else
    echo "catalog_db_tuple_read_observation unavailable; cache hit/miss counters were still validated."
  fi
}

java_major() {
  local version
  version="$(java -version 2>&1 | awk -F '"' '/version/ {print $2; exit}' || true)"
  case "$version" in
    1.*) printf '%s\n' "$version" | cut -d. -f2 ;;
    "") echo 0 ;;
    *) printf '%s\n' "$version" | cut -d. -f1 ;;
  esac
}

run_virtual_thread_probe() {
  local major probe_status tmp_dir

  need_command java
  need_command javac
  major="$(java_major)"
  if [ "$major" -lt 21 ]; then
    echo "Java 21 is required for the virtual-thread runtime probe. Current Java major: ${major}" >&2
    exit 2
  fi

  tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/ecommerce-load-vthreads.XXXXXX")"
  javac --release 21 -d "$tmp_dir" tests/load/VirtualThreadRuntimeProbe.java
  probe_status=0
  java -cp "$tmp_dir" VirtualThreadRuntimeProbe "$VIRTUAL_THREAD_TASKS" || probe_status=$?
  find "$tmp_dir" -type f -name '*.class' -delete
  rmdir "$tmp_dir"
  return "$probe_status"
}

cleanup() {
  delete_keycloak_users || true
}

need_command curl
need_command jq
need_command awk
need_command python3
load_env_file
configure_compose_files
apply_profile_defaults
enforce_bounds

KEYCLOAK_PORT="${KEYCLOAK_PORT:-8085}"
KEYCLOAK_REALM="${KEYCLOAK_REALM:-ecommerce}"
KEYCLOAK_LOCAL_TEST_CLIENT="${KEYCLOAK_LOCAL_TEST_CLIENT:-ecommerce-local-test}"
KEYCLOAK_ADMIN_USER="${KEYCLOAK_ADMIN:-admin}"
KEYCLOAK_ADMIN_PASSWORD_VALUE="${KEYCLOAK_ADMIN_PASSWORD:-change-me-local-keycloak}"
KEYCLOAK_CURL_TIMEOUT_SECONDS="${LOAD_KEYCLOAK_CURL_TIMEOUT_SECONDS:-20}"
GATEWAY_SERVICE_HOST_PORT="${GATEWAY_SERVICE_HOST_PORT:-8080}"
CATALOG_SERVICE_HOST_PORT="${CATALOG_SERVICE_HOST_PORT:-8081}"
POSTGRES_USER="${POSTGRES_USER:-ecommerce}"

BASE_URL="${BASE_URL:-http://localhost:${GATEWAY_SERVICE_HOST_PORT}}"
BASE_URL="${BASE_URL%/}"
KEYCLOAK_URL="http://localhost:${KEYCLOAK_PORT}"
CATALOG_URL="http://localhost:${CATALOG_SERVICE_HOST_PORT}"
RUN_ID="${LOAD_RUN_ID:-load-$(date -u +%Y%m%d%H%M%S)-$(random_suffix)}"
RUN_SLUG="$(echo "$RUN_ID" | tr '[:upper:]_' '[:lower:]-' | tr -cd 'a-z0-9-')"
ADMIN_USERNAME="${LOAD_ADMIN_USERNAME:-${RUN_SLUG}-catalog-admin}"
CUSTOMER_USERNAME="${LOAD_CUSTOMER_USERNAME:-${RUN_SLUG}-customer}"
ADMIN_PASSWORD="Local-Load-${RUN_SLUG}-admin-$(random_suffix)!"
CUSTOMER_PASSWORD="Local-Load-${RUN_SLUG}-customer-$(random_suffix)!"
CREATED_KEYCLOAK_USER_IDS=()
CATALOG_ADMIN_TOKEN=""
CUSTOMER_TOKEN=""
PRODUCT_ID=""
PRODUCT_SKU=""

trap cleanup EXIT

echo "Load validation profile=${PROFILE} base_url=${BASE_URL} vus=${VUS} duration_seconds=${DURATION_SECONDS}"
start_stack_if_needed
DEADLINE_SECONDS=$(( $(now_seconds) + STACK_TIMEOUT_SECONDS ))
wait_until "Keycloak realm ${KEYCLOAK_REALM}" curl_ok "${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/.well-known/openid-configuration"
wait_until "Keycloak admin token endpoint" keycloak_admin_token_available
wait_until "Catalog Service readiness" curl_ok "${CATALOG_URL}/actuator/health/readiness"
wait_until "Gateway readiness" curl_ok "${BASE_URL}/actuator/health/readiness"

create_load_fixture
assert_cache_effect

python3 tests/load/catalog_browse_detail_load.py \
  --base-url "$BASE_URL" \
  --token "$CUSTOMER_TOKEN" \
  --product-id "$PRODUCT_ID" \
  --query "$PRODUCT_SKU" \
  --virtual-users "$VUS" \
  --duration-seconds "$DURATION_SECONDS" \
  --browse-every "$BROWSE_EVERY" \
  --request-timeout-seconds "$REQUEST_TIMEOUT_SECONDS" \
  --max-p95-ms "$MAX_P95_MS" \
  --max-error-rate "$MAX_ERROR_RATE" \
  --min-throughput-rps "$MIN_THROUGHPUT_RPS"

run_virtual_thread_probe

echo "Bounded load and cache-effect validation passed for run ${RUN_ID}."
