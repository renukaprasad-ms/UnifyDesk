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
  ./k8s.sh restart
  ./k8s.sh delete
  ./k8s.sh logs <service> [tail]
  ./k8s.sh describe <service>
  ./k8s.sh top [interval_seconds] [stream|once]

Services:
  backend | frontend | postgres | redis | kafka

Examples:
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
  docker build -t "${BACKEND_IMAGE}" "${ROOT_DIR}/backend"
  docker build \
    -t "${FRONTEND_IMAGE}" \
    --build-arg VITE_API_BASE_URL="${K8S_VITE_API_BASE_URL:-}" \
    "${ROOT_DIR}/frontend"

  docker save "${BACKEND_IMAGE}" -o /tmp/messaging-backend.tar
  docker save "${FRONTEND_IMAGE}" -o /tmp/messaging-frontend.tar

  sudo k3s ctr images import /tmp/messaging-backend.tar
  sudo k3s ctr images import /tmp/messaging-frontend.tar
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

restart() {
  load_env

  apply_env
  build_and_load_images
  kubectl delete deployment postgres redis kafka --ignore-not-found
  kubectl apply -f "${ROOT_DIR}/k8s/dependencies.yaml"
  kubectl apply -f "${ROOT_DIR}/k8s/backend.yaml"
  kubectl apply -f "${ROOT_DIR}/k8s/frontend.yaml"
  kubectl rollout restart deployment/messaging-frontend
  kubectl delete pod -l app=messaging-backend --ignore-not-found
  kubectl get pods
}

delete_all() {
  kubectl delete -f "${ROOT_DIR}/k8s/frontend.yaml" --ignore-not-found
  kubectl delete -f "${ROOT_DIR}/k8s/backend.yaml" --ignore-not-found
  kubectl delete -f "${ROOT_DIR}/k8s/dependencies.yaml" --ignore-not-found
  kubectl delete secret messaging-env --ignore-not-found
  kubectl get pods
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
  restart) restart ;;
  delete) delete_all ;;
  logs) shift; logs "$@" ;;
  describe) shift; describe "$@" ;;
  top) shift; top_view "$@" ;;
  *) usage; exit 1 ;;
esac
