#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${EMAIL_PROVIDER_ENV_FILE:-${ROOT_DIR}/.env}"
K8S_DIR="${ROOT_DIR}/k8s"
NAMESPACE="email-platform"
SECRET_NAME="email-provider-env"
APP_IMAGE="email-provider:latest"

usage() {
  cat <<'EOF'
Usage:
  ./k8s.sh start
  ./k8s.sh stop
  ./k8s.sh restart [service|all]
  ./k8s.sh delete
  ./k8s.sh clear-db
  ./k8s.sh logs <service> [tail]
  ./k8s.sh describe <service>
  ./k8s.sh status
  ./k8s.sh top [interval_seconds] [stream|once]
  ./k8s.sh build
  ./k8s.sh apply-env
  ./k8s.sh validate
  ./k8s.sh port-forward [local_port]

Services:
  app | postgres | redis | kafka

Examples:
  ./k8s.sh start
  ./k8s.sh restart
  ./k8s.sh restart app
  ./k8s.sh logs app
  ./k8s.sh logs postgres 200
  ./k8s.sh describe redis
  ./k8s.sh status
  ./k8s.sh top
  ./k8s.sh top 5
  ./k8s.sh top 1 once
  ./k8s.sh port-forward 18010

Notes:
  - Postfix runs on the EC2 host, not in Kubernetes.
  - Set EMAIL_PROVIDER_KAFKA_MODE=local to run the bundled Kafka StatefulSet.
  - Set EMAIL_PROVIDER_KAFKA_MODE=external to use KAFKA_BOOTSTRAP_SERVERS only.
  - All Kubernetes commands target namespace email-platform (not the main UnifyDesk App).
  - restart all does not restart PostgreSQL; use restart postgres explicitly.
EOF
}

require_command() {
  local command="$1"
  if ! command -v "${command}" >/dev/null 2>&1; then
    echo "Required command not found: ${command}" >&2
    exit 1
  fi
}

require_env() {
  if [[ ! -f "${ENV_FILE}" ]]; then
    echo "Missing ${ENV_FILE}" >&2
    echo "Copy .env.example to .env and fill in the required secrets." >&2
    exit 1
  fi
}

load_env() {
  require_env
  local line key value
  # Do not split with IFS='=': Bash read strips trailing '=' padding from
  # base64 values (for example, EMAIL_PROVIDER_MASTER_KEY).
  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%

validate_env() {
  local missing=()
  local required=(
    EMAIL_PROVIDER_DB_NAME
    SPRING_DATASOURCE_USERNAME
    SPRING_DATASOURCE_PASSWORD
    EMAIL_PROVIDER_REDIS_PASSWORD
    EMAIL_PROVIDER_REDIS_MAXMEMORY
    SNOWFLAKE_NODE_ID
    EMAIL_PROVIDER_MASTER_KEY
    EMAIL_PROVIDER_INTERNAL_API_TOKEN
    EMAIL_PROVIDER_KAFKA_MODE
  )

  for key in "${required[@]}"; do
    if [[ -z "${!key:-}" ]]; then
      missing+=("${key}")
    fi
  done

  if (( ${#missing[@]} > 0 )); then
    echo "Missing required environment values:" >&2
    printf '  - %s\n' "${missing[@]}" >&2
    exit 1
  fi

  case "${EMAIL_PROVIDER_KAFKA_MODE}" in
    local|external) ;;
    *)
      echo "EMAIL_PROVIDER_KAFKA_MODE must be local or external" >&2
      exit 1
      ;;
  esac

  if [[ "${EMAIL_PROVIDER_KAFKA_MODE}" == "external" && -z "${KAFKA_BOOTSTRAP_SERVERS:-}" ]]; then
    echo "KAFKA_BOOTSTRAP_SERVERS is required when EMAIL_PROVIDER_KAFKA_MODE=external" >&2
    exit 1
  fi

  local key
  for key in SPRING_DATASOURCE_PASSWORD EMAIL_PROVIDER_REDIS_PASSWORD EMAIL_PROVIDER_MASTER_KEY EMAIL_PROVIDER_INTERNAL_API_TOKEN; do
    if [[ "${!key}" == *CHANGE_ME* ]]; then
      echo "Replace placeholder value for ${key} in your .env" >&2
      exit 1
    fi
  done

  if (( ${#EMAIL_PROVIDER_INTERNAL_API_TOKEN} < 32 )); then
    echo "EMAIL_PROVIDER_INTERNAL_API_TOKEN must contain at least 32 characters" >&2
    exit 1
  fi

  require_command base64
  if ! printf '%s' "${EMAIL_PROVIDER_MASTER_KEY}" | base64 --decode >/dev/null 2>&1; then
    echo "EMAIL_PROVIDER_MASTER_KEY must be valid base64" >&2
    exit 1
  fi
  local decoded_key_bytes
  decoded_key_bytes="$(printf '%s' "${EMAIL_PROVIDER_MASTER_KEY}" | base64 --decode | wc -c)"
  decoded_key_bytes="${decoded_key_bytes//[[:space:]]/}"
  if [[ "${decoded_key_bytes}" != "32" ]]; then
    echo "EMAIL_PROVIDER_MASTER_KEY must be base64 encoding of exactly 32 bytes" >&2
    exit 1
  fi

  if [[ "${EMAIL_PROVIDER_FEEDBACK_ENABLED:-false}" == "true" ]]; then
    local feedback_token="${EMAIL_PROVIDER_FEEDBACK_TOKEN:-}"
    if (( ${#feedback_token} < 32 )) || [[ "${feedback_token}" == *CHANGE_ME* ]]; then
      echo "Set a secure EMAIL_PROVIDER_FEEDBACK_TOKEN (at least 32 characters)" >&2
      exit 1
    fi
  fi

  if [[ "${EMAIL_PROVIDER_DELIVERY_ENABLED:-false}" == "true" ]]; then
    local unsubscribe_key="${EMAIL_PROVIDER_UNSUBSCRIBE_SIGNING_KEY:-}"
    if (( ${#unsubscribe_key} < 32 )); then
      echo "Configure EMAIL_PROVIDER_UNSUBSCRIBE_SIGNING_KEY before enabling delivery" >&2
      exit 1
    fi
    echo "WARNING: EMAIL_PROVIDER_DELIVERY_ENABLED=true; verify Postfix, DNS, IP reputation and abuse gates first." >&2
  fi

  if [[ "${EMAIL_PROVIDER_PORT:-8010}" != "8010" ]]; then
    echo "EMAIL_PROVIDER_PORT must be 8010 to match k8s/app.yaml probes and service" >&2
    exit 1
  fi
}

ensure_namespace() {
  kubectl apply -f "${K8S_DIR}/namespace.yaml"
}

apply_env() {
  load_env
  validate_env
  require_command kubectl
  ensure_namespace

  kubectl -n "${NAMESPACE}" create secret generic "${SECRET_NAME}" \
    --from-env-file="${ENV_FILE}" \
    --dry-run=client -o yaml | kubectl apply -f -
}

build_image() {
  require_command docker
  require_command k3s
  if [[ "${EUID}" -ne 0 ]]; then
    require_command sudo
  fi

  (
    local archive
    archive="$(mktemp "${TMPDIR:-/tmp}/email-provider-image.XXXXXXXX.tar")"
    trap 'rm -f "${archive}"' EXIT
    docker build -t "${APP_IMAGE}" "${ROOT_DIR}"
    docker save "${APP_IMAGE}" -o "${archive}"
    if [[ "${EUID}" -eq 0 ]]; then
      k3s ctr images import "${archive}"
    else
      sudo k3s ctr images import "${archive}"
    fi
  )
}

check_existing_postgres_mount() {
  local previous_mount
  previous_mount="$(kubectl -n "${NAMESPACE}" get statefulset email-postgres \
    -o jsonpath='{.spec.template.spec.containers[0].volumeMounts[0].mountPath}' \
    2>/dev/null || true)"
  if [[ "${previous_mount}" == "/var/lib/postgresql/data" ]]; then
    echo "BLOCKED: existing email-postgres uses the pre-PostgreSQL-18 volume path." >&2
    echo "Back up and verify the actual PGDATA, migrate/recover it, then apply postgres.yaml explicitly." >&2
    echo "The helper will not auto-change the existing database volume mount." >&2
    exit 1
  fi
}

apply_dependencies() {
  check_existing_postgres_mount
  kubectl apply -f "${K8S_DIR}/postgres.yaml"
  kubectl apply -f "${K8S_DIR}/redis.yaml"

  if [[ "${EMAIL_PROVIDER_KAFKA_MODE}" == "local" ]]; then
    kubectl apply -f "${K8S_DIR}/kafka.yaml"
  else
    # Changing Kafka mode must not silently delete an existing broker or its queues.
    if kubectl -n "${NAMESPACE}" get statefulset email-kafka >/dev/null 2>&1; then
      echo "NOTICE: Existing local Kafka remains present. Stop/remove it explicitly after migrating data." >&2
    fi
  fi
}

wait_for_dependencies() {
  kubectl -n "${NAMESPACE}" rollout status statefulset/email-postgres --timeout=180s
  kubectl -n "${NAMESPACE}" rollout status statefulset/email-redis --timeout=120s

  if [[ "${EMAIL_PROVIDER_KAFKA_MODE}" == "local" ]]; then
    kubectl -n "${NAMESPACE}" rollout status statefulset/email-kafka --timeout=240s
  fi
}

start() {
  require_command kubectl
  apply_env
  build_image
  apply_dependencies
  # kubectl apply may keep a previous scale-to-zero replica setting.
  scale_if_exists statefulset email-postgres 1
  scale_if_exists statefulset email-redis 1
  if [[ "${EMAIL_PROVIDER_KAFKA_MODE}" == "local" ]]; then
    scale_if_exists statefulset email-kafka 1
  fi
  wait_for_dependencies
  kubectl apply -f "${K8S_DIR}/app.yaml"
  scale_if_exists deployment email-provider 1
  kubectl -n "${NAMESPACE}" rollout status deployment/email-provider --timeout=240s
  status
}

scale_if_exists() {
  local kind="$1"
  local name="$2"
  local replicas="$3"

  if kubectl -n "${NAMESPACE}" get "${kind}" "${name}" >/dev/null 2>&1; then
    kubectl -n "${NAMESPACE}" scale "${kind}" "${name}" --replicas="${replicas}"
  fi
}

stop() {
  require_command kubectl
  scale_if_exists deployment email-provider 0
  scale_if_exists statefulset email-kafka 0
  scale_if_exists statefulset email-redis 0
  scale_if_exists statefulset email-postgres 0
  status
}

workload_for_service() {
  case "${1:-}" in
    app) echo "deployment/email-provider" ;;
    postgres) echo "statefulset/email-postgres" ;;
    redis) echo "statefulset/email-redis" ;;
    kafka) echo "statefulset/email-kafka" ;;
    *)
      usage
      exit 1
      ;;
  esac
}

manifest_for_service() {
  case "${1:-}" in
    app) echo "${K8S_DIR}/app.yaml" ;;
    postgres) echo "${K8S_DIR}/postgres.yaml" ;;
    redis) echo "${K8S_DIR}/redis.yaml" ;;
    kafka) echo "${K8S_DIR}/kafka.yaml" ;;
    *)
      usage
      exit 1
      ;;
  esac
}

restart_service() {
  local service="$1"
  local workload
  local manifest

  if [[ "${service}" == "kafka" && "${EMAIL_PROVIDER_KAFKA_MODE}" != "local" ]]; then
    echo "Kafka is configured as external; no local Kafka workload to restart." >&2
    exit 1
  fi

  if [[ "${service}" == "app" ]]; then
    build_image
  fi

  if [[ "${service}" == "postgres" ]]; then
    check_existing_postgres_mount
  fi
  manifest="$(manifest_for_service "${service}")"
  workload="$(workload_for_service "${service}")"
  kubectl apply -f "${manifest}"
  scale_if_exists "${workload%%/*}" "${workload#*/}" 1
  kubectl -n "${NAMESPACE}" rollout restart "${workload}"
  kubectl -n "${NAMESPACE}" rollout status "${workload}" --timeout=240s
}

restart() {
  local service="${1:-all}"

  require_command kubectl
  apply_env

  if [[ "${service}" == "all" ]]; then
    build_image
    apply_dependencies
    scale_if_exists statefulset email-postgres 1
    scale_if_exists statefulset email-redis 1
    if [[ "${EMAIL_PROVIDER_KAFKA_MODE}" == "local" ]]; then
      scale_if_exists statefulset email-kafka 1
    fi
    # Match UnifyDesk App: never restart the database unless requested explicitly.
    wait_for_dependencies
    kubectl apply -f "${K8S_DIR}/app.yaml"
    scale_if_exists deployment email-provider 1
    kubectl -n "${NAMESPACE}" rollout restart deployment/email-provider
    kubectl -n "${NAMESPACE}" rollout status deployment/email-provider --timeout=240s
  else
    restart_service "${service}"
  fi

  status
}

delete_all() {
  require_command kubectl
  read -r -p "Delete the email-platform workloads but KEEP persistent volumes? Type DELETE to continue: " confirmation
  if [[ "${confirmation}" != "DELETE" ]]; then
    echo "Aborted."
    exit 1
  fi

  kubectl delete -f "${K8S_DIR}/app.yaml" --ignore-not-found
  kubectl delete -f "${K8S_DIR}/kafka.yaml" --ignore-not-found
  kubectl delete -f "${K8S_DIR}/redis.yaml" --ignore-not-found
  kubectl delete -f "${K8S_DIR}/postgres.yaml" --ignore-not-found
  kubectl -n "${NAMESPACE}" delete secret "${SECRET_NAME}" --ignore-not-found
  status
}

clear_db() {
  require_command kubectl
  load_env
  validate_env

  read -r -p "This deletes ALL email-provider application data. Type DELETE to continue: " confirmation
  if [[ "${confirmation}" != "DELETE" ]]; then
    echo "Aborted."
    exit 1
  fi

  local app_replicas
  app_replicas="$(kubectl -n "${NAMESPACE}" get deployment email-provider -o jsonpath='{.spec.replicas}' 2>/dev/null || true)"
  if [[ "${app_replicas:-0}" != "0" ]]; then
    echo "Refusing to clear the DB while email-provider is running. Run ./k8s.sh stop first." >&2
    exit 1
  fi
  scale_if_exists statefulset email-postgres 1
  kubectl -n "${NAMESPACE}" rollout status statefulset/email-postgres --timeout=180s
  kubectl -n "${NAMESPACE}" exec -i statefulset/email-postgres -- \
    sh -ec 'PGPASSWORD="$POSTGRES_PASSWORD" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1' <<'SQL'
DO $$
DECLARE
  truncate_statement text;
BEGIN
  SELECT 'TRUNCATE TABLE ' ||
         string_agg(format('%I.%I', schemaname, tablename), ', ') ||
         ' RESTART IDENTITY CASCADE'
    INTO truncate_statement
  FROM pg_tables
  WHERE schemaname = 'public'
    AND tablename <> 'flyway_schema_history';

  IF truncate_statement IS NOT NULL THEN
    EXECUTE truncate_statement;
  END IF;
END $$;
SQL

  echo "Email-provider database application data deleted."
}

selector_for_service() {
  case "${1:-}" in
    app) echo "app=email-provider" ;;
    postgres) echo "app=email-postgres" ;;
    redis) echo "app=email-redis" ;;
    kafka) echo "app=email-kafka" ;;
    *)
      usage
      exit 1
      ;;
  esac
}

logs() {
  require_command kubectl
  local service="${1:-}"
  local tail="${2:-100}"
  local selector

  selector="$(selector_for_service "${service}")"
  kubectl -n "${NAMESPACE}" logs -l "${selector}" --all-containers=true --tail="${tail}" -f
}

describe() {
  require_command kubectl
  local service="${1:-}"
  local selector

  selector="$(selector_for_service "${service}")"
  kubectl -n "${NAMESPACE}" describe pods -l "${selector}"
}

status() {
  require_command kubectl
  echo "Namespace: ${NAMESPACE}"
  echo
  echo "Pods"
  kubectl -n "${NAMESPACE}" get pods -o wide || true
  echo
  echo "Services"
  kubectl -n "${NAMESPACE}" get svc || true
  echo
  echo "Workloads"
  kubectl -n "${NAMESPACE}" get deployment,statefulset || true
  echo
  echo "Persistent volumes"
  kubectl -n "${NAMESPACE}" get pvc || true
}

render_top() {
  if [[ -t 1 && -n "${TERM:-}" ]]; then clear; fi
  date
  echo
  status
  echo
  echo "Resource usage"
  kubectl -n "${NAMESPACE}" top pods 2>/dev/null || echo "metrics-server is not available"
  echo
  echo "Host Postfix"
  systemctl is-active postfix 2>/dev/null || echo "postfix is not active or systemd is unavailable"
}

top_view() {
  local interval="${1:-1}"
  local mode="${2:-stream}"

  if ! [[ "${interval}" =~ ^[0-9]+$ ]] || [[ "${interval}" -lt 1 ]]; then
    echo "interval_seconds must be a positive number" >&2
    exit 1
  fi

  case "${mode}" in
    stream)
      while true; do
        render_top
        sleep "${interval}"
      done
      ;;
    once)
      render_top
      ;;
    *)
      echo "mode must be stream or once" >&2
      exit 1
      ;;
  esac
}

validate_only() {
  require_command base64
  load_env
  validate_env
  echo "Email provider configuration validated (no secrets displayed)."
}

port_forward() {
  require_command kubectl
  local local_port="${1:-18010}"
  if ! [[ "${local_port}" =~ ^[0-9]+$ ]] || (( local_port < 1024 || local_port > 65535 )); then
    echo "local_port must be between 1024 and 65535" >&2
    exit 1
  fi
  echo "Local console: http://127.0.0.1:${local_port}/console/index.html"
  kubectl -n "${NAMESPACE}" port-forward --address 127.0.0.1 \
    svc/email-provider "${local_port}:8010"
}

command="${1:-}"
case "${command}" in
  start) start ;;
  stop) stop ;;
  restart) shift; restart "$@" ;;
  delete) delete_all ;;
  clear-db) clear_db ;;
  logs) shift; logs "$@" ;;
  describe) shift; describe "$@" ;;
  status) status ;;
  top) shift; top_view "$@" ;;
  build) build_image ;;
  apply-env) apply_env ;;
  validate) validate_only ;;
  port-forward) shift; port_forward "$@" ;;
  *) usage; exit 1 ;;
esac
\r'}"
    [[ -z "${line}" || "${line}" == \#* ]] && continue
    if [[ "${line}" != *=* ]]; then
      echo "Invalid environment line (expected KEY=value)" >&2
      exit 1
    fi
    key="${line%%=*}"
    value="${line#*=}"
    if [[ ! "${key}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
      echo "Invalid environment variable name in ${ENV_FILE}" >&2
      exit 1
    fi
    export "${key}=${value}"
  done < "${ENV_FILE}"
}

validate_env() {
  local missing=()
  local required=(
    EMAIL_PROVIDER_DB_NAME
    SPRING_DATASOURCE_USERNAME
    SPRING_DATASOURCE_PASSWORD
    EMAIL_PROVIDER_REDIS_PASSWORD
    EMAIL_PROVIDER_REDIS_MAXMEMORY
    SNOWFLAKE_NODE_ID
    EMAIL_PROVIDER_MASTER_KEY
    EMAIL_PROVIDER_INTERNAL_API_TOKEN
    EMAIL_PROVIDER_KAFKA_MODE
  )

  for key in "${required[@]}"; do
    if [[ -z "${!key:-}" ]]; then
      missing+=("${key}")
    fi
  done

  if (( ${#missing[@]} > 0 )); then
    echo "Missing required environment values:" >&2
    printf '  - %s\n' "${missing[@]}" >&2
    exit 1
  fi

  case "${EMAIL_PROVIDER_KAFKA_MODE}" in
    local|external) ;;
    *)
      echo "EMAIL_PROVIDER_KAFKA_MODE must be local or external" >&2
      exit 1
      ;;
  esac

  if [[ "${EMAIL_PROVIDER_KAFKA_MODE}" == "external" && -z "${KAFKA_BOOTSTRAP_SERVERS:-}" ]]; then
    echo "KAFKA_BOOTSTRAP_SERVERS is required when EMAIL_PROVIDER_KAFKA_MODE=external" >&2
    exit 1
  fi

  local key
  for key in SPRING_DATASOURCE_PASSWORD EMAIL_PROVIDER_REDIS_PASSWORD EMAIL_PROVIDER_MASTER_KEY EMAIL_PROVIDER_INTERNAL_API_TOKEN; do
    if [[ "${!key}" == *CHANGE_ME* ]]; then
      echo "Replace placeholder value for ${key} in your .env" >&2
      exit 1
    fi
  done

  if (( ${#EMAIL_PROVIDER_INTERNAL_API_TOKEN} < 32 )); then
    echo "EMAIL_PROVIDER_INTERNAL_API_TOKEN must contain at least 32 characters" >&2
    exit 1
  fi

  require_command base64
  if ! printf '%s' "${EMAIL_PROVIDER_MASTER_KEY}" | base64 --decode >/dev/null 2>&1; then
    echo "EMAIL_PROVIDER_MASTER_KEY must be valid base64" >&2
    exit 1
  fi
  local decoded_key_bytes
  decoded_key_bytes="$(printf '%s' "${EMAIL_PROVIDER_MASTER_KEY}" | base64 --decode | wc -c)"
  decoded_key_bytes="${decoded_key_bytes//[[:space:]]/}"
  if [[ "${decoded_key_bytes}" != "32" ]]; then
    echo "EMAIL_PROVIDER_MASTER_KEY must be base64 encoding of exactly 32 bytes" >&2
    exit 1
  fi

  if [[ "${EMAIL_PROVIDER_FEEDBACK_ENABLED:-false}" == "true" ]]; then
    local feedback_token="${EMAIL_PROVIDER_FEEDBACK_TOKEN:-}"
    if (( ${#feedback_token} < 32 )) || [[ "${feedback_token}" == *CHANGE_ME* ]]; then
      echo "Set a secure EMAIL_PROVIDER_FEEDBACK_TOKEN (at least 32 characters)" >&2
      exit 1
    fi
  fi

  if [[ "${EMAIL_PROVIDER_DELIVERY_ENABLED:-false}" == "true" ]]; then
    local unsubscribe_key="${EMAIL_PROVIDER_UNSUBSCRIBE_SIGNING_KEY:-}"
    if (( ${#unsubscribe_key} < 32 )); then
      echo "Configure EMAIL_PROVIDER_UNSUBSCRIBE_SIGNING_KEY before enabling delivery" >&2
      exit 1
    fi
    echo "WARNING: EMAIL_PROVIDER_DELIVERY_ENABLED=true; verify Postfix, DNS, IP reputation and abuse gates first." >&2
  fi

  if [[ "${EMAIL_PROVIDER_PORT:-8010}" != "8010" ]]; then
    echo "EMAIL_PROVIDER_PORT must be 8010 to match k8s/app.yaml probes and service" >&2
    exit 1
  fi
}

ensure_namespace() {
  kubectl apply -f "${K8S_DIR}/namespace.yaml"
}

apply_env() {
  load_env
  validate_env
  require_command kubectl
  ensure_namespace

  kubectl -n "${NAMESPACE}" create secret generic "${SECRET_NAME}" \
    --from-env-file="${ENV_FILE}" \
    --dry-run=client -o yaml | kubectl apply -f -
}

build_image() {
  require_command docker
  require_command k3s
  if [[ "${EUID}" -ne 0 ]]; then
    require_command sudo
  fi

  (
    local archive
    archive="$(mktemp "${TMPDIR:-/tmp}/email-provider-image.XXXXXXXX.tar")"
    trap 'rm -f "${archive}"' EXIT
    docker build -t "${APP_IMAGE}" "${ROOT_DIR}"
    docker save "${APP_IMAGE}" -o "${archive}"
    if [[ "${EUID}" -eq 0 ]]; then
      k3s ctr images import "${archive}"
    else
      sudo k3s ctr images import "${archive}"
    fi
  )
}

check_existing_postgres_mount() {
  local previous_mount
  previous_mount="$(kubectl -n "${NAMESPACE}" get statefulset email-postgres \
    -o jsonpath='{.spec.template.spec.containers[0].volumeMounts[0].mountPath}' \
    2>/dev/null || true)"
  if [[ "${previous_mount}" == "/var/lib/postgresql/data" ]]; then
    echo "BLOCKED: existing email-postgres uses the pre-PostgreSQL-18 volume path." >&2
    echo "Back up and verify the actual PGDATA, migrate/recover it, then apply postgres.yaml explicitly." >&2
    echo "The helper will not auto-change the existing database volume mount." >&2
    exit 1
  fi
}

apply_dependencies() {
  check_existing_postgres_mount
  kubectl apply -f "${K8S_DIR}/postgres.yaml"
  kubectl apply -f "${K8S_DIR}/redis.yaml"

  if [[ "${EMAIL_PROVIDER_KAFKA_MODE}" == "local" ]]; then
    kubectl apply -f "${K8S_DIR}/kafka.yaml"
  else
    # Changing Kafka mode must not silently delete an existing broker or its queues.
    if kubectl -n "${NAMESPACE}" get statefulset email-kafka >/dev/null 2>&1; then
      echo "NOTICE: Existing local Kafka remains present. Stop/remove it explicitly after migrating data." >&2
    fi
  fi
}

wait_for_dependencies() {
  kubectl -n "${NAMESPACE}" rollout status statefulset/email-postgres --timeout=180s
  kubectl -n "${NAMESPACE}" rollout status statefulset/email-redis --timeout=120s

  if [[ "${EMAIL_PROVIDER_KAFKA_MODE}" == "local" ]]; then
    kubectl -n "${NAMESPACE}" rollout status statefulset/email-kafka --timeout=240s
  fi
}

start() {
  require_command kubectl
  apply_env
  build_image
  apply_dependencies
  # kubectl apply may keep a previous scale-to-zero replica setting.
  scale_if_exists statefulset email-postgres 1
  scale_if_exists statefulset email-redis 1
  if [[ "${EMAIL_PROVIDER_KAFKA_MODE}" == "local" ]]; then
    scale_if_exists statefulset email-kafka 1
  fi
  wait_for_dependencies
  kubectl apply -f "${K8S_DIR}/app.yaml"
  scale_if_exists deployment email-provider 1
  kubectl -n "${NAMESPACE}" rollout status deployment/email-provider --timeout=240s
  status
}

scale_if_exists() {
  local kind="$1"
  local name="$2"
  local replicas="$3"

  if kubectl -n "${NAMESPACE}" get "${kind}" "${name}" >/dev/null 2>&1; then
    kubectl -n "${NAMESPACE}" scale "${kind}" "${name}" --replicas="${replicas}"
  fi
}

stop() {
  require_command kubectl
  scale_if_exists deployment email-provider 0
  scale_if_exists statefulset email-kafka 0
  scale_if_exists statefulset email-redis 0
  scale_if_exists statefulset email-postgres 0
  status
}

workload_for_service() {
  case "${1:-}" in
    app) echo "deployment/email-provider" ;;
    postgres) echo "statefulset/email-postgres" ;;
    redis) echo "statefulset/email-redis" ;;
    kafka) echo "statefulset/email-kafka" ;;
    *)
      usage
      exit 1
      ;;
  esac
}

manifest_for_service() {
  case "${1:-}" in
    app) echo "${K8S_DIR}/app.yaml" ;;
    postgres) echo "${K8S_DIR}/postgres.yaml" ;;
    redis) echo "${K8S_DIR}/redis.yaml" ;;
    kafka) echo "${K8S_DIR}/kafka.yaml" ;;
    *)
      usage
      exit 1
      ;;
  esac
}

restart_service() {
  local service="$1"
  local workload
  local manifest

  if [[ "${service}" == "kafka" && "${EMAIL_PROVIDER_KAFKA_MODE}" != "local" ]]; then
    echo "Kafka is configured as external; no local Kafka workload to restart." >&2
    exit 1
  fi

  if [[ "${service}" == "app" ]]; then
    build_image
  fi

  if [[ "${service}" == "postgres" ]]; then
    check_existing_postgres_mount
  fi
  manifest="$(manifest_for_service "${service}")"
  workload="$(workload_for_service "${service}")"
  kubectl apply -f "${manifest}"
  scale_if_exists "${workload%%/*}" "${workload#*/}" 1
  kubectl -n "${NAMESPACE}" rollout restart "${workload}"
  kubectl -n "${NAMESPACE}" rollout status "${workload}" --timeout=240s
}

restart() {
  local service="${1:-all}"

  require_command kubectl
  apply_env

  if [[ "${service}" == "all" ]]; then
    build_image
    apply_dependencies
    scale_if_exists statefulset email-postgres 1
    scale_if_exists statefulset email-redis 1
    if [[ "${EMAIL_PROVIDER_KAFKA_MODE}" == "local" ]]; then
      scale_if_exists statefulset email-kafka 1
    fi
    # Match UnifyDesk App: never restart the database unless requested explicitly.
    wait_for_dependencies
    kubectl apply -f "${K8S_DIR}/app.yaml"
    scale_if_exists deployment email-provider 1
    kubectl -n "${NAMESPACE}" rollout restart deployment/email-provider
    kubectl -n "${NAMESPACE}" rollout status deployment/email-provider --timeout=240s
  else
    restart_service "${service}"
  fi

  status
}

delete_all() {
  require_command kubectl
  read -r -p "Delete the email-platform workloads but KEEP persistent volumes? Type DELETE to continue: " confirmation
  if [[ "${confirmation}" != "DELETE" ]]; then
    echo "Aborted."
    exit 1
  fi

  kubectl delete -f "${K8S_DIR}/app.yaml" --ignore-not-found
  kubectl delete -f "${K8S_DIR}/kafka.yaml" --ignore-not-found
  kubectl delete -f "${K8S_DIR}/redis.yaml" --ignore-not-found
  kubectl delete -f "${K8S_DIR}/postgres.yaml" --ignore-not-found
  kubectl -n "${NAMESPACE}" delete secret "${SECRET_NAME}" --ignore-not-found
  status
}

clear_db() {
  require_command kubectl
  load_env
  validate_env

  read -r -p "This deletes ALL email-provider application data. Type DELETE to continue: " confirmation
  if [[ "${confirmation}" != "DELETE" ]]; then
    echo "Aborted."
    exit 1
  fi

  local app_replicas
  app_replicas="$(kubectl -n "${NAMESPACE}" get deployment email-provider -o jsonpath='{.spec.replicas}' 2>/dev/null || true)"
  if [[ "${app_replicas:-0}" != "0" ]]; then
    echo "Refusing to clear the DB while email-provider is running. Run ./k8s.sh stop first." >&2
    exit 1
  fi
  scale_if_exists statefulset email-postgres 1
  kubectl -n "${NAMESPACE}" rollout status statefulset/email-postgres --timeout=180s
  kubectl -n "${NAMESPACE}" exec -i statefulset/email-postgres -- \
    sh -ec 'PGPASSWORD="$POSTGRES_PASSWORD" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1' <<'SQL'
DO $$
DECLARE
  truncate_statement text;
BEGIN
  SELECT 'TRUNCATE TABLE ' ||
         string_agg(format('%I.%I', schemaname, tablename), ', ') ||
         ' RESTART IDENTITY CASCADE'
    INTO truncate_statement
  FROM pg_tables
  WHERE schemaname = 'public'
    AND tablename <> 'flyway_schema_history';

  IF truncate_statement IS NOT NULL THEN
    EXECUTE truncate_statement;
  END IF;
END $$;
SQL

  echo "Email-provider database application data deleted."
}

selector_for_service() {
  case "${1:-}" in
    app) echo "app=email-provider" ;;
    postgres) echo "app=email-postgres" ;;
    redis) echo "app=email-redis" ;;
    kafka) echo "app=email-kafka" ;;
    *)
      usage
      exit 1
      ;;
  esac
}

logs() {
  require_command kubectl
  local service="${1:-}"
  local tail="${2:-100}"
  local selector

  selector="$(selector_for_service "${service}")"
  kubectl -n "${NAMESPACE}" logs -l "${selector}" --all-containers=true --tail="${tail}" -f
}

describe() {
  require_command kubectl
  local service="${1:-}"
  local selector

  selector="$(selector_for_service "${service}")"
  kubectl -n "${NAMESPACE}" describe pods -l "${selector}"
}

status() {
  require_command kubectl
  echo "Namespace: ${NAMESPACE}"
  echo
  echo "Pods"
  kubectl -n "${NAMESPACE}" get pods -o wide || true
  echo
  echo "Services"
  kubectl -n "${NAMESPACE}" get svc || true
  echo
  echo "Workloads"
  kubectl -n "${NAMESPACE}" get deployment,statefulset || true
  echo
  echo "Persistent volumes"
  kubectl -n "${NAMESPACE}" get pvc || true
}

render_top() {
  if [[ -t 1 && -n "${TERM:-}" ]]; then clear; fi
  date
  echo
  status
  echo
  echo "Resource usage"
  kubectl -n "${NAMESPACE}" top pods 2>/dev/null || echo "metrics-server is not available"
  echo
  echo "Host Postfix"
  systemctl is-active postfix 2>/dev/null || echo "postfix is not active or systemd is unavailable"
}

top_view() {
  local interval="${1:-1}"
  local mode="${2:-stream}"

  if ! [[ "${interval}" =~ ^[0-9]+$ ]] || [[ "${interval}" -lt 1 ]]; then
    echo "interval_seconds must be a positive number" >&2
    exit 1
  fi

  case "${mode}" in
    stream)
      while true; do
        render_top
        sleep "${interval}"
      done
      ;;
    once)
      render_top
      ;;
    *)
      echo "mode must be stream or once" >&2
      exit 1
      ;;
  esac
}

validate_only() {
  require_command base64
  load_env
  validate_env
  echo "Email provider configuration validated (no secrets displayed)."
}

port_forward() {
  require_command kubectl
  local local_port="${1:-18010}"
  if ! [[ "${local_port}" =~ ^[0-9]+$ ]] || (( local_port < 1024 || local_port > 65535 )); then
    echo "local_port must be between 1024 and 65535" >&2
    exit 1
  fi
  echo "Local console: http://127.0.0.1:${local_port}/console/index.html"
  kubectl -n "${NAMESPACE}" port-forward --address 127.0.0.1 \
    svc/email-provider "${local_port}:8010"
}

command="${1:-}"
case "${command}" in
  start) start ;;
  stop) stop ;;
  restart) shift; restart "$@" ;;
  delete) delete_all ;;
  clear-db) clear_db ;;
  logs) shift; logs "$@" ;;
  describe) shift; describe "$@" ;;
  status) status ;;
  top) shift; top_view "$@" ;;
  build) build_image ;;
  apply-env) apply_env ;;
  validate) validate_only ;;
  port-forward) shift; port_forward "$@" ;;
  *) usage; exit 1 ;;
esac
