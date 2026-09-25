#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${ROOT_DIR}/.env"
K8S_DIR="${ROOT_DIR}/k8s"
NAMESPACE="email-platform"
SECRET_NAME="email-provider-env"
APP_IMAGE="email-provider:latest"

usage() {
  cat <<'EOF'
Usage:
  ./email-k8s.sh start
  ./email-k8s.sh stop
  ./email-k8s.sh restart [service|all]
  ./email-k8s.sh delete
  ./email-k8s.sh clear-db
  ./email-k8s.sh logs <service> [tail]
  ./email-k8s.sh describe <service>
  ./email-k8s.sh status
  ./email-k8s.sh top [interval_seconds] [stream|once]
  ./email-k8s.sh build
  ./email-k8s.sh apply-env

Services:
  app | postgres | redis | kafka

Examples:
  ./email-k8s.sh start
  ./email-k8s.sh restart
  ./email-k8s.sh restart app
  ./email-k8s.sh logs app
  ./email-k8s.sh logs postgres 200
  ./email-k8s.sh describe redis
  ./email-k8s.sh status
  ./email-k8s.sh top
  ./email-k8s.sh top 5
  ./email-k8s.sh top 1 once

Notes:
  - Postfix runs on the EC2 host, not in Kubernetes.
  - Set EMAIL_PROVIDER_KAFKA_MODE=local to run the bundled Kafka StatefulSet.
  - Set EMAIL_PROVIDER_KAFKA_MODE=external to use KAFKA_BOOTSTRAP_SERVERS only.
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
  while IFS='=' read -r key value || [[ -n "${key}" ]]; do
    key="${key%$'\r'}"
    value="${value%$'\r'}"
    [[ -z "${key}" || "${key}" == \#* ]] && continue
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
    EMAIL_PROVIDER_FEEDBACK_TOKEN
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
}

ensure_namespace() {
  kubectl apply -f "${K8S_DIR}/namespace.yaml"
}

apply_env() {
  load_env
  validate_env
  ensure_namespace

  kubectl -n "${NAMESPACE}" create secret generic "${SECRET_NAME}"     --from-env-file="${ENV_FILE}"     --dry-run=client     -o yaml | kubectl apply -f -
}

build_image() {
  require_command docker
  docker build -t "${APP_IMAGE}" "${ROOT_DIR}"
  docker save "${APP_IMAGE}" -o /tmp/email-provider.tar

  if command -v k3s >/dev/null 2>&1; then
    sudo k3s ctr images import /tmp/email-provider.tar
  else
    echo "k3s command not found; import ${APP_IMAGE} into your Kubernetes runtime manually." >&2
    exit 1
  fi

  rm -f /tmp/email-provider.tar
}

apply_dependencies() {
  kubectl apply -f "${K8S_DIR}/postgres.yaml"
  kubectl apply -f "${K8S_DIR}/redis.yaml"

  if [[ "${EMAIL_PROVIDER_KAFKA_MODE}" == "local" ]]; then
    kubectl apply -f "${K8S_DIR}/kafka.yaml"
  else
    kubectl delete -f "${K8S_DIR}/kafka.yaml" --ignore-not-found >/dev/null 2>&1 || true
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
  load_env
  validate_env
  apply_env
  build_image
  apply_dependencies
  wait_for_dependencies
  kubectl apply -f "${K8S_DIR}/app.yaml"
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

  manifest="$(manifest_for_service "${service}")"
  workload="$(workload_for_service "${service}")"
  kubectl apply -f "${manifest}"
  kubectl -n "${NAMESPACE}" rollout restart "${workload}"
  kubectl -n "${NAMESPACE}" rollout status "${workload}" --timeout=240s
}

restart() {
  local service="${1:-all}"

  require_command kubectl
  load_env
  validate_env
  apply_env

  if [[ "${service}" == "all" ]]; then
    build_image
    apply_dependencies
    kubectl apply -f "${K8S_DIR}/app.yaml"

    kubectl -n "${NAMESPACE}" rollout restart statefulset/email-postgres
    kubectl -n "${NAMESPACE}" rollout restart statefulset/email-redis
    if [[ "${EMAIL_PROVIDER_KAFKA_MODE}" == "local" ]]; then
      kubectl -n "${NAMESPACE}" rollout restart statefulset/email-kafka
    fi
    wait_for_dependencies

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

  kubectl -n "${NAMESPACE}" exec -i statefulset/email-postgres --     sh -ec 'PGPASSWORD="$POSTGRES_PASSWORD" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1' <<'SQL'
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
  clear
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
  *) usage; exit 1 ;;
esac
