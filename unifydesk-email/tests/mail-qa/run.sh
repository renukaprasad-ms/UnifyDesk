#!/usr/bin/env bash
# Disposable container only: synthetic data, loopback SMTP, no customer DNS.
set -Eeuo pipefail
[[ -f /.dockerenv && "${MAIL_QA_DISPOSABLE:-}" == YES && "${PGDATABASE:-}" == mail_qa ]] || {
  echo 'This runner only supports its disposable Docker Compose database.' >&2; exit 2;
}
cd /workspace
export SNOWFLAKE_NODE_ID=997
bash tests/inbound-r10-pipe-qa.sh
redis-server --bind 127.0.0.1 --daemonize yes
mkdir -p /tmp/mail-objects /tmp/clamdb /run/clamav /run/rspamd
# A small local signature proves that the actual clamd engine rejects a fixture.
# This is not an up-to-date production virus database or a scanner capacity test.
printf '%s\n' 'UnifyDesk.QA.Marker:0:*:556e6966794465736b205141206d616c776172652066697874757265' > /tmp/clamdb/qa.ndb
cat > /tmp/clamd.conf <<'EOF'
Foreground yes
DatabaseDirectory /tmp/clamdb
TCPSocket 3310
TCPAddr 127.0.0.1
LocalSocket /tmp/clamd.sock
PidFile /tmp/clamd.pid
LogFile /tmp/clamd.log
User root
StreamMaxLength 12M
EOF
clamd --config-file=/tmp/clamd.conf > /tmp/clamd-start.log 2>&1 &
clam_pid=$!
rspamd -f -u root -g root > /tmp/rspamd.log 2>&1 &
spam_pid=$!
cleanup() {
  if [[ -n "${app_pid:-}" ]]; then kill "$app_pid" 2>/dev/null || true; fi
  postfix stop >/dev/null 2>&1 || true
  kill "$clam_pid" "$spam_pid" 2>/dev/null || true
}
trap cleanup EXIT
python3 - <<'PY'
import socket,time
for port in (3310,11333):
    for _ in range(60):
        try:
            with socket.create_connection(('127.0.0.1',port),1): break
        except OSError: time.sleep(1)
    else: raise SystemExit(f'Actual scanner did not start on {port}')
PY
export MAIL_QA_REAL_SCANNERS=YES
cd backend
bash mvnw -B spotless:check clean test package > /tmp/mail-backend-qa.log 2>&1 || {
  tail -100 /tmp/mail-backend-qa.log; exit 1;
}
tail -25 /tmp/mail-backend-qa.log
grep 'Tests run:' /tmp/mail-backend-qa.log | tail -1
export SPRING_DATASOURCE_URL="$MAIL_QA_PG_URL"
dropdb --if-exists mail_qa_runtime
createdb mail_qa_runtime
export PGDATABASE=mail_qa_runtime
export SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/mail_qa_runtime
export SPRING_DATASOURCE_USERNAME="$MAIL_QA_PG_USER"
export SPRING_DATASOURCE_PASSWORD="$MAIL_QA_PG_PASSWORD"
export FLYWAY_BASELINE_ON_MIGRATE=false
export EMAIL_PROVIDER_INTERNAL_API_TOKEN=local-disposable-internal-token-000000000000
export EMAIL_PROVIDER_MASTER_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=
export EMAIL_CONSOLE_MFA_ENROLLMENT_ENABLED=true
export EMAIL_MAIL_INBOUND_ENABLED=true EMAIL_MAIL_SCANNER_ENABLED=true
export EMAIL_MAIL_ACTIVATION_ENABLED=true EMAIL_MAIL_COMPOSE_ENABLED=true
export EMAIL_MAIL_OPS_ENABLED=true
export EMAIL_MAIL_INBOUND_SPOOL_PATH=/tmp/mail-objects
export EMAIL_MAIL_SCAN_INTERVAL_MS=500
java -jar target/email-provider-0.0.1-SNAPSHOT.jar > /tmp/mail-app.log 2>&1 &
app_pid=$!
for i in $(seq 1 90); do
  if curl -fsS http://127.0.0.1:8010/actuator/health/liveness >/dev/null 2>&1; then break; fi
  if ! kill -0 "$app_pid" 2>/dev/null; then tail -100 /tmp/mail-app.log; exit 1; fi
  sleep 1
done
cd /workspace
python3 tests/mail-qa/http-smtp-qa.py
bash tests/mail-qa/restore-drill.sh
echo 'PASS: disposable Linux, PostgreSQL, real scanners, HTTP, Postfix and encrypted restore QA'
