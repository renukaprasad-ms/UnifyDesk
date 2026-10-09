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
  ./k8s.sh mailpit [local_port]      # k3d-only SMTP sandbox web UI

Services:
  app | postgres | redis | kafka | mailpit (k3d only)

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
  ./k8s.sh mailpit 18025

Notes:
  - In WSL k3d, images are imported with k3d and mail is captured by Mailpit.
  - Native K3s continues using the host Postfix (not bundled).
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
  local line key value cr
  printf -v cr '%b' '\015'
  # Preserve everything after the FIRST "=" (including base64 padding).
  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%${cr}}"
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

current_runtime() {
  local context
  context="$(kubectl config current-context 2>/dev/null || true)"
  if [[ "${context}" == k3d-* ]]; then
    echo k3d
  else
    echo k3s
  fi
}

current_cluster_name() {
  local context
  context="$(kubectl config current-context)"
  [[ "${context}" == k3d-* ]] || {
    echo "Current kubectl context is not a k3d cluster: ${context}" >&2
    return 1
  }
  echo "${context#k3d-}"
}

app_manifest() {
  if [[ "$(current_runtime)" == "k3d" ]]; then
    echo "${K8S_DIR}/app-k3d.yaml"
  else
    echo "${K8S_DIR}/app.yaml"
  fi
}

build_image() {
  require_command kubectl
  require_command docker
  if ! docker info >/dev/null 2>&1; then
    echo "Docker daemon unavailable. Start Docker Desktop with WSL integration." >&2
    exit 1
  fi

  local runtime cluster
  runtime="$(current_runtime)"
  if [[ "${runtime}" == "k3d" ]]; then
    require_command k3d
    cluster="$(current_cluster_name)"
    # Refuse to build when kubectl is pointed at a stopped/wrong k3d cluster.
    if ! kubectl get nodes >/dev/null 2>&1; then
      echo "Current k3d cluster ${cluster} is not reachable." >&2
      exit 1
    fi
  fi
  docker build -t "${APP_IMAGE}" "${ROOT_DIR}"

  if [[ "${runtime}" == "k3d" ]]; then
    echo "Importing ${APP_IMAGE} into local k3d cluster ${cluster}"
    k3d image import "${APP_IMAGE}" -c "${cluster}"
    return
  fi

  require_command k3s
  if [[ "${EUID}" -ne 0 ]]; then
    require_command sudo
  fi

  (
    local archive
    archive="$(mktemp "${TMPDIR:-/tmp}/email-provider-image.XXXXXXXX.tar")"
    trap 'rm -f "${archive}"' EXIT
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
  if [[ "$(current_runtime)" == "k3d" ]]; then
    kubectl apply -f "${K8S_DIR}/mailpit.yaml"
  fi

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
  if [[ "$(current_runtime)" == "k3d" ]]; then
    kubectl -n "${NAMESPACE}" rollout status deployment/email-mailpit --timeout=120s
  fi

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
  if [[ "$(current_runtime)" == "k3d" ]]; then
    scale_if_exists deployment email-mailpit 1
  fi
  if [[ "${EMAIL_PROVIDER_KAFKA_MODE}" == "local" ]]; then
    scale_if_exists statefulset email-kafka 1
  fi
  wait_for_dependencies
  kubectl apply -f "$(app_manifest)"
  scale_if_exists deployment email-provider 1
  # Reusing image:latest requires a new pod for the imported image to take effect.
  kubectl -n "${NAMESPACE}" rollout restart deployment/email-provider
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
  if [[ "$(current_runtime)" == "k3d" ]]; then
    scale_if_exists deployment email-mailpit 0
  fi
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
    mailpit) echo "deployment/email-mailpit" ;;
    *)
      usage
      exit 1
      ;;
  esac
}

manifest_for_service() {
  case "${1:-}" in
    app) app_manifest ;;
    mailpit) echo "${K8S_DIR}/mailpit.yaml" ;;
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

  if [[ "${service}" == "mailpit" && "$(current_runtime)" != "k3d" ]]; then
    echo "Mailpit is only deployed in the local k3d development cluster." >&2
    exit 1
  fi

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
    if [[ "$(current_runtime)" == "k3d" ]]; then
      scale_if_exists deployment email-mailpit 1
    fi
    if [[ "${EMAIL_PROVIDER_KAFKA_MODE}" == "local" ]]; then
      scale_if_exists statefulset email-kafka 1
    fi
    # Match UnifyDesk App: never restart the database unless requested explicitly.
    wait_for_dependencies
    kubectl apply -f "$(app_manifest)"
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

  kubectl delete -f "$(app_manifest)" --ignore-not-found
  if [[ "$(current_runtime)" == "k3d" ]]; then
    kubectl delete -f "${K8S_DIR}/mailpit.yaml" --ignore-not-found
  fi
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
    mailpit) echo "app=email-mailpit" ;;
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
  if [[ "$(current_runtime)" == "k3d" ]]; then
    echo "Local SMTP sandbox: Mailpit (not public email delivery)"
  else
    echo "Host Postfix"
    systemctl is-active postfix 2>/dev/null || echo "postfix is not active or systemd is unavailable"
  fi
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

mailpit_ui() {
  require_command kubectl
  if [[ "$(current_runtime)" != "k3d" ]]; then
    echo "Mailpit sandbox UI only exists with k3d." >&2
    exit 1
  fi
  local local_port="${1:-18025}"
  if ! [[ "${local_port}" =~ ^[0-9]+$ ]] || (( local_port < 1024 || local_port > 65535 )); then
    echo "local_port must be between 1024 and 65535" >&2
    exit 1
  fi
  echo "Local SMTP sandbox: http://127.0.0.1:${local_port}"
  kubectl -n "${NAMESPACE}" port-forward --address 127.0.0.1 \
    svc/email-mailpit "${local_port}:8025"
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
  mailpit) shift; mailpit_ui "$@" ;;
  *) usage; exit 1 ;;
esac
