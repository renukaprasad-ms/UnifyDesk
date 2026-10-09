#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${ROOT_DIR}/.env"
BACKEND_IMAGE="messaging-backend:latest"
FRONTEND_IMAGE="messaging-frontend:latest"

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
  ./k8s.sh top [interval_seconds] [stream|once]

Services:
  backend | frontend | postgres | redis | kafka

Examples:
  ./k8s.sh restart
  ./k8s.sh restart backend
  ./k8s.sh restart postgres
  ./k8s.sh clear-db
  ./k8s.sh logs backend
  ./k8s.sh logs kafka 200
  ./k8s.sh describe backend
  ./k8s.sh top
  ./k8s.sh top 5
  ./k8s.sh top 1 once
EOF
}

require_env() {
  if [[ ! -f "${ENV_FILE}" ]]; then
    echo "Missing ${ENV_FILE}" >&2
    exit 1
  fi
}

apply_env() {
  require_env
  kubectl create secret generic messaging-env \
    --from-env-file="${ENV_FILE}" \
    --dry-run=client \
    -o yaml | kubectl apply -f -
}

build_and_load_images() {
  build_and_load_service_image backend
  build_and_load_service_image frontend
}

import_image_into_cluster() {
  local image="$1"
  local archive="$2"
  local context

  context="$(kubectl config current-context)"
  if [[ "${context}" == k3d-* ]]; then
    if ! command -v k3d >/dev/null 2>&1; then
      echo "Kubernetes context ${context} requires the k3d CLI to import local Docker images." >&2
      return 1
    fi

    local cluster="${context#k3d-}"
    echo "Importing ${image} into k3d cluster ${cluster}"
    k3d image import "${image}" --cluster "${cluster}"
  elif command -v k3s >/dev/null 2>&1; then
    echo "Importing ${image} into the local k3s container runtime"
    docker save "${image}" -o "${archive}"
    sudo k3s ctr images import "${archive}"
  else
    echo "Cannot import ${image}: Kubernetes context ${context} is not k3d and k3s is not installed locally." >&2
    echo "Select your local k3d/k3s cluster before running this script." >&2
    return 1
  fi
}

build_and_load_service_image() {
  local service="$1"

  case "${service}" in
    backend)
      docker build -t "${BACKEND_IMAGE}" "${ROOT_DIR}/backend"
      import_image_into_cluster "${BACKEND_IMAGE}" "/tmp/messaging-backend.tar"
      ;;
    frontend)
      docker build \
        -t "${FRONTEND_IMAGE}" \
        --build-arg VITE_API_BASE_URL="${K8S_VITE_API_BASE_URL:-}" \
        --build-arg VITE_CASHFREE_MODE="${VITE_CASHFREE_MODE:-sandbox}" \
        "${ROOT_DIR}/frontend"
      import_image_into_cluster "${FRONTEND_IMAGE}" "/tmp/messaging-frontend.tar"
      ;;
  esac
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

scale_if_exists() {
  local kind="$1"
  local name="$2"
  local replicas="$3"

  if kubectl get "${kind}" "${name}" >/dev/null 2>&1; then
    kubectl scale "${kind}" "${name}" --replicas="${replicas}"
  fi
}

start() {
  load_env

  apply_env
  build_and_load_images
  kubectl apply -f "${ROOT_DIR}/k8s/dependencies.yaml"
  kubectl apply -f "${ROOT_DIR}/k8s/backend.yaml"
  kubectl apply -f "${ROOT_DIR}/k8s/frontend.yaml"
  kubectl get pods
}

stop() {
  scale_if_exists statefulset messaging-backend 0
  scale_if_exists deployment messaging-frontend 0
  scale_if_exists deployment postgres 0
  scale_if_exists deployment redis 0
  scale_if_exists deployment kafka 0
  kubectl get pods
}

workload_for_service() {
  local service="${1:-}"

  case "${service}" in
    backend) echo "statefulset/messaging-backend" ;;
    frontend) echo "deployment/messaging-frontend" ;;
    postgres) echo "deployment/postgres" ;;
    redis) echo "deployment/redis" ;;
    kafka) echo "deployment/kafka" ;;
    *)
      usage
      exit 1
      ;;
  esac
}

apply_manifest_for_service() {
  local service="$1"

  case "${service}" in
    backend) kubectl apply -f "${ROOT_DIR}/k8s/backend.yaml" ;;
    frontend) kubectl apply -f "${ROOT_DIR}/k8s/frontend.yaml" ;;
    postgres|redis|kafka) kubectl apply -f "${ROOT_DIR}/k8s/dependencies.yaml" ;;
  esac
}

restart_service() {
  local service="$1"
  local workload

  build_and_load_service_image "${service}"
  apply_manifest_for_service "${service}"
  workload="$(workload_for_service "${service}")"
  kubectl rollout restart "${workload}"
}

restart() {
  local service="${1:-all}"

  load_env

  apply_env

  if [[ "${service}" == "all" ]]; then
    build_and_load_images
    kubectl apply -f "${ROOT_DIR}/k8s/dependencies.yaml"
    kubectl apply -f "${ROOT_DIR}/k8s/backend.yaml"
    kubectl apply -f "${ROOT_DIR}/k8s/frontend.yaml"
    kubectl rollout restart deployment/redis
    kubectl rollout restart deployment/kafka
    kubectl rollout restart statefulset/messaging-backend
    kubectl rollout restart deployment/messaging-frontend
  else
    restart_service "${service}"
  fi

  kubectl get pods
}

delete_all() {
  kubectl delete -f "${ROOT_DIR}/k8s/frontend.yaml" --ignore-not-found
  kubectl delete -f "${ROOT_DIR}/k8s/backend.yaml" --ignore-not-found
  kubectl delete -f "${ROOT_DIR}/k8s/dependencies.yaml" --ignore-not-found
  kubectl delete secret messaging-env --ignore-not-found
  kubectl get pods
}

clear_db() {
  local confirmation

  read -r -p "This will delete all application data from the Postgres database. Type DELETE to continue: " confirmation
  if [[ "${confirmation}" != "DELETE" ]]; then
    echo "Aborted."
    exit 1
  fi

  kubectl exec -i deployment/postgres -- sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -p "$POSTGRES_CONTAINER_PORT" -v ON_ERROR_STOP=1' <<'SQL'
DO $$
DECLARE
  truncate_statement text;
BEGIN
  SELECT 'TRUNCATE TABLE ' || string_agg(format('%I.%I', schemaname, tablename), ', ') || ' RESTART IDENTITY CASCADE'
    INTO truncate_statement
  FROM pg_tables
  WHERE schemaname = 'public'
    AND tablename <> 'flyway_schema_history';

  IF truncate_statement IS NOT NULL THEN
    EXECUTE truncate_statement;
  END IF;
END $$;
SQL

  echo "Database application data deleted."
}

selector_for_service() {
  local service="${1:-}"

  case "${service}" in
    backend) echo "app=messaging-backend" ;;
    frontend) echo "app=messaging-frontend" ;;
    postgres) echo "app=postgres" ;;
    redis) echo "app=redis" ;;
    kafka) echo "app=kafka" ;;
    *)
      usage
      exit 1
      ;;
  esac
}

logs() {
  local service="${1:-}"
  local tail="${2:-100}"
  local selector

  selector="$(selector_for_service "${service}")"

  kubectl logs -l "${selector}" --tail="${tail}" -f
}

describe() {
  local service="${1:-}"
  local selector

  selector="$(selector_for_service "${service}")"

  kubectl describe pods -l "${selector}"
}

render_top() {
  clear
  date
  echo
  echo "Pods"
  kubectl get pods -o wide
  echo
  echo "Services"
  kubectl get svc
  echo
  echo "Workloads"
  kubectl get statefulset,deployment
  echo
  echo "Resource Usage"
  kubectl top pods 2>/dev/null || echo "metrics-server is not available"
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
  top) shift; top_view "$@" ;;
  *) usage; exit 1 ;;
esac
