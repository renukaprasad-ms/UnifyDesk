#!/usr/bin/env bash
# Run on the DEDICATED staging Postfix host, not your production MX.
# No emails are sent unless R10_SEND_LOCAL_FIXTURE=YES is explicitly set.
set -Eeuo pipefail
export LC_ALL=C
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
bash -n "$root/postfix/inbound-r10/udmail-inbound-pipe.sh"
command -v postconf >/dev/null || { echo "Postfix unavailable" >&2; exit 2; }
command -v postmap >/dev/null || { echo "Postfix postmap unavailable" >&2; exit 2; }
postconf -m | grep -qw pgsql || { echo "postfix-pgsql is missing" >&2; exit 2; }
echo "R10: adapter syntax and PostgreSQL map support checked"

# The map files must be installed on the dedicated Postfix test instance.
domain_map=/etc/postfix/pgsql-udmail-domains.cf
mailbox_map=/etc/postfix/pgsql-udmail-recipients.cf
if [[ -n "${R10_TEST_DOMAIN:-}" ]]; then
  if [[ ! "${R10_TEST_DOMAIN}" =~ ^[a-z0-9-]+(\.[a-z0-9-]+)+$ ]]; then
    echo "Invalid test domain" >&2; exit 2
  fi
  [[ -n "$(postmap -q "$R10_TEST_DOMAIN" "pgsql:$domain_map")" ]] ||
    { echo "Test domain not admitted; check V22/domain approval" >&2; exit 1; }
  [[ -n "${R10_TEST_ACCEPT_RCPT:-}" ]] ||
    { echo "R10_TEST_ACCEPT_RCPT required" >&2; exit 2; }
  [[ -n "$(postmap -q "$R10_TEST_ACCEPT_RCPT" "pgsql:$mailbox_map")" ]] ||
    { echo "Test mailbox not admitted" >&2; exit 1; }
  if [[ -n "$(postmap -q "missing-localpart-should-not-exist@$R10_TEST_DOMAIN" "pgsql:$mailbox_map")" ]]; then
    echo "Unsafe catch-all recipient map detected" >&2; exit 1
  fi
  echo "R10: admitted domain/recipient and rejected nonexistent recipient"
else
  echo "Set R10_TEST_DOMAIN and R10_TEST_ACCEPT_RCPT to test live pgsql maps"
fi

if [[ "${R10_SEND_LOCAL_FIXTURE:-NO}" == YES ]]; then
  [[ -n "${R10_TEST_ACCEPT_RCPT:-}" ]] ||
    { echo "Explicit allowed test recipient required" >&2; exit 2; }
  command -v swaks >/dev/null ||
    { echo "Install swaks to run the isolated SMTP test" >&2; exit 2; }
  # This is a REAL staging delivery; never use customer addresses.
  # Keep the inbound instance on a dedicated staging port by default.
  port="${R10_SMTP_PORT:-2526}"
  if [[ ! "$port" =~ ^[0-9]{2,5}$ ]] || ((port<1 || port>65535)); then
    echo "Invalid staging SMTP port" >&2; exit 2
  fi
  swaks --server 127.0.0.1 --port "$port" --from r10-probe@example.invalid \
    --to "$R10_TEST_ACCEPT_RCPT" --timeout 25 \
    --header "Subject: UnifyDesk R10 private SMTP test" \
    --body "Fixture only. Verify one PENDING_SCAN row, READY/INBOX, idempotency."
  echo "Inspect Postfix queue + mail DB + scanner, not just SMTP 250"
fi
