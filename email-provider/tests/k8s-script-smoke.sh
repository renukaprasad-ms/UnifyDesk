#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TEMP_DIR}"' EXIT
mkdir -p "${TEMP_DIR}/bin"
export EMAIL_PROVIDER_ENV_FILE="${TEMP_DIR}/test.env"
export EMAIL_K8S_TEST_LOG="${TEMP_DIR}/actions.log"
export PATH="${TEMP_DIR}/bin:${PATH}"

cat > "${EMAIL_PROVIDER_ENV_FILE}" <<'EOF'
EMAIL_PROVIDER_DB_NAME=email_provider_test
SPRING_DATASOURCE_USERNAME=email_provider_test
SPRING_DATASOURCE_PASSWORD=safe-test-database-password
EMAIL_PROVIDER_REDIS_PASSWORD=safe-test-redis-password
EMAIL_PROVIDER_REDIS_MAXMEMORY=96mb
SNOWFLAKE_NODE_ID=100
EMAIL_PROVIDER_KAFKA_MODE=local
EMAIL_PROVIDER_KAFKA_ENABLED=true
EMAIL_PROVIDER_FEEDBACK_ENABLED=false
EMAIL_PROVIDER_DELIVERY_ENABLED=false
EMAIL_PROVIDER_INTERNAL_API_TOKEN=a-long-unique-test-internal-token-12345678901234567890
EOF
printf '0123456789abcdef0123456789abcdef' | base64 -w 0 |
  awk '{print "EMAIL_PROVIDER_MASTER_KEY=" $0}' >> "${EMAIL_PROVIDER_ENV_FILE}"

cat > "${TEMP_DIR}/bin/kubectl" <<'EOF'
#!/usr/bin/env bash
printf 'kubectl %s\n' "$*" >> "${EMAIL_K8S_TEST_LOG}"
if [[ "$*" == "config current-context" && -n "${EMAIL_PROVIDER_MOCK_CONTEXT:-}" ]]; then
  printf '%s\n' "${EMAIL_PROVIDER_MOCK_CONTEXT}"
fi
if [[ " $* " == *" create secret generic "* ]]; then
  printf 'apiVersion: v1\nkind: Secret\nmetadata:\n  name: email-provider-env\n'
fi
exit 0
EOF
cat > "${TEMP_DIR}/bin/docker" <<'EOF'
#!/usr/bin/env bash
printf 'docker %s\n' "$*" >> "${EMAIL_K8S_TEST_LOG}"
exit 0
EOF
cat > "${TEMP_DIR}/bin/k3s" <<'EOF'
#!/usr/bin/env bash
printf 'k3s %s\n' "$*" >> "${EMAIL_K8S_TEST_LOG}"
exit 0
EOF
cat > "${TEMP_DIR}/bin/k3d" <<'EOF'
#!/usr/bin/env bash
printf 'k3d %s\n' "$*" >> "${EMAIL_K8S_TEST_LOG}"
exit 0
EOF
cat > "${TEMP_DIR}/bin/sudo" <<'EOF'
#!/usr/bin/env bash
exec "$@"
EOF
# Reproduce strict decoders that reject missing base64 '=' padding.
# The .env reader must keep the original value byte-for-byte.
cat > "${TEMP_DIR}/bin/base64" <<'EOF'
#!/usr/bin/env bash
if [[ "${1:-}" == "--decode" ]]; then
  input="$(cat)"
  if (( ${#input} % 4 != 0 )); then
    echo "base64 padding lost" >&2
    exit 1
  fi
  printf '%s' "${input}" | /usr/bin/base64 --decode
else
  /usr/bin/base64 "$@"
fi
EOF
chmod +x "${TEMP_DIR}/bin/"*

bash -n "${ROOT_DIR}/k8s.sh"
bash -n "${ROOT_DIR}/email-k8s.sh"
"${ROOT_DIR}/k8s.sh" validate
cp "${EMAIL_PROVIDER_ENV_FILE}" "${TEMP_DIR}/bad.env"
sed -i 's/a-long-unique-test-internal-token-12345678901234567890/short/' "${TEMP_DIR}/bad.env"
if EMAIL_PROVIDER_ENV_FILE="${TEMP_DIR}/bad.env" "${ROOT_DIR}/k8s.sh" validate >/dev/null 2>&1; then
  echo "ERROR: accepted invalid internal API secret" >&2
  exit 1
fi
"${ROOT_DIR}/k8s.sh" status >/dev/null
"${ROOT_DIR}/k8s.sh" stop >/dev/null
"${ROOT_DIR}/k8s.sh" start >/dev/null
grep -q 'scale statefulset email-postgres --replicas=1' "${EMAIL_K8S_TEST_LOG}"
grep -q 'scale deployment email-provider --replicas=1' "${EMAIL_K8S_TEST_LOG}"
: > "${EMAIL_K8S_TEST_LOG}"
"${ROOT_DIR}/email-k8s.sh" restart >/dev/null
grep -q 'rollout restart deployment/email-provider' "${EMAIL_K8S_TEST_LOG}"
if grep -q 'rollout restart statefulset/email-postgres' "${EMAIL_K8S_TEST_LOG}"; then
  echo "ERROR: restart all unexpectedly restarted Postgres" >&2
  exit 1
fi

: > "${EMAIL_K8S_TEST_LOG}"
"${ROOT_DIR}/k8s.sh" port-forward 18010 >/dev/null
grep -q 'port-forward --address 127.0.0.1 svc/email-console 18010:8080' "${EMAIL_K8S_TEST_LOG}"

echo 'PASS: shell syntax, environment validation, start/stop scaling, restart safety, alias, local port-forward'

# WSL/Docker Desktop has a k3d cluster instead of a native k3s executable.
export EMAIL_PROVIDER_MOCK_CONTEXT=k3d-unifydesk-local
: > "${EMAIL_K8S_TEST_LOG}"
"${ROOT_DIR}/k8s.sh" start >/dev/null
grep -q 'k3d image import email-provider:latest -c unifydesk-local' "${EMAIL_K8S_TEST_LOG}"
grep -q 'k3d image import email-console:latest -c unifydesk-local' "${EMAIL_K8S_TEST_LOG}"
grep -q 'docker build -t email-provider:latest .*/email-provider/backend' "${EMAIL_K8S_TEST_LOG}"
grep -q 'docker build -t email-console:latest .*/email-provider/frontend' "${EMAIL_K8S_TEST_LOG}"
grep -q 'kubectl apply -f .*k8s/frontend.yaml' "${EMAIL_K8S_TEST_LOG}"
grep -q 'rollout status deployment/email-console' "${EMAIL_K8S_TEST_LOG}"
grep -q 'kubectl apply -f .*k8s/mailpit.yaml' "${EMAIL_K8S_TEST_LOG}"
grep -q 'kubectl apply -f .*k8s/app-k3d.yaml' "${EMAIL_K8S_TEST_LOG}"
if grep -q '^k3s ctr images import' "${EMAIL_K8S_TEST_LOG}"; then
  echo "ERROR: attempted native k3s import while connected to k3d" >&2
  exit 1
fi

: > "${EMAIL_K8S_TEST_LOG}"
"${ROOT_DIR}/k8s.sh" mailpit 18025 >/dev/null
grep -q 'port-forward --address 127.0.0.1 svc/email-mailpit 18025:8025' "${EMAIL_K8S_TEST_LOG}"
: > "${EMAIL_K8S_TEST_LOG}"
"${ROOT_DIR}/k8s.sh" restart app >/dev/null
grep -q 'k3d image import email-provider:latest -c unifydesk-local' "${EMAIL_K8S_TEST_LOG}"
grep -q 'kubectl apply -f .*k8s/app-k3d.yaml' "${EMAIL_K8S_TEST_LOG}"
unset EMAIL_PROVIDER_MOCK_CONTEXT
echo 'PASS: local k3d context, image import, isolated app config, Mailpit UI and restarts'

# The runtime image must have a numeric non-root UID/GID so Kubernetes'
# runAsNonRoot check can validate the container rather than rejecting a USER name.
grep -q '^USER 10001:10001$' "${ROOT_DIR}/backend/Dockerfile"
grep -q 'addgroup -S -g 10001 emailprovider' "${ROOT_DIR}/Dockerfile"
for app_manifest in "${ROOT_DIR}/k8s/app.yaml" "${ROOT_DIR}/k8s/app-k3d.yaml"; do
  grep -q 'runAsNonRoot: true' "${app_manifest}"
  grep -q 'runAsUser: 10001' "${app_manifest}"
  grep -q 'runAsGroup: 10001' "${app_manifest}"
done
echo 'PASS: numeric non-root container identity in native K3s and local k3d'

: > "${EMAIL_K8S_TEST_LOG}"
"${ROOT_DIR}/k8s.sh" api-port-forward 18012 >/dev/null
grep -q 'port-forward --address 127.0.0.1 svc/email-provider 18012:8010' "${EMAIL_K8S_TEST_LOG}"
: > "${EMAIL_K8S_TEST_LOG}"
"${ROOT_DIR}/k8s.sh" restart frontend >/dev/null
grep -q 'docker build -t email-console:latest .*/email-provider/frontend' "${EMAIL_K8S_TEST_LOG}"
grep -q 'rollout restart deployment/email-console' "${EMAIL_K8S_TEST_LOG}"
if grep -q 'rollout restart deployment/email-provider' "${EMAIL_K8S_TEST_LOG}"; then
  echo "ERROR: frontend restart restarted backend" >&2
  exit 1
fi
if grep -q 'rollout restart statefulset/email-postgres' "${EMAIL_K8S_TEST_LOG}"; then
  echo "ERROR: frontend restart restarted Postgres" >&2
  exit 1
fi
grep -q 'runAsNonRoot: true' "${ROOT_DIR}/k8s/frontend.yaml"
grep -q 'proxy_pass http://email-provider.email-platform.svc.cluster.local:8010;' "${ROOT_DIR}/frontend/nginx.conf"
echo 'PASS: separate frontend image, port-forward and non-disruptive restart'
