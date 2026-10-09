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
printf 'test-only-32-byte-key-must-be-long' | base64 -w 0 |
  awk '{print "EMAIL_PROVIDER_MASTER_KEY=" $0}' >> "${EMAIL_PROVIDER_ENV_FILE}"

cat > "${TEMP_DIR}/bin/kubectl" <<'EOF'
#!/usr/bin/env bash
printf 'kubectl %s\n' "$*" >> "${EMAIL_K8S_TEST_LOG}"
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
cat > "${TEMP_DIR}/bin/sudo" <<'EOF'
#!/usr/bin/env bash
exec "$@"
EOF
chmod +x "${TEMP_DIR}/bin/"*

bash -n "${ROOT_DIR}/k8s.sh"
bash -n "${ROOT_DIR}/email-k8s.sh"
"${ROOT_DIR}/k8s.sh" validate
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
grep -q 'port-forward --address 127.0.0.1 svc/email-provider 18010:8010' "${EMAIL_K8S_TEST_LOG}"

echo 'PASS: shell syntax, environment validation, start/stop scaling, restart safety, alias, local port-forward'
