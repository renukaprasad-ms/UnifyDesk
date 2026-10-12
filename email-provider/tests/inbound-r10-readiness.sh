#!/usr/bin/env bash
# Read-only staging preflight. Never starts Postfix, changes k3s, or enables mail.
set -Eeuo pipefail
namespace=email-platform
warnings=0
check() { printf '[CHECK] %s\n' "$*"; }
warn() { printf '[BLOCKED] %s\n' "$*" >&2; warnings=$((warnings+1)); }
pass() { printf '[OK] %s\n' "$*"; }
check "R10 readiness on current host; NO infrastructure will be modified"

if ! command -v kubectl >/dev/null; then
  warn "kubectl unavailable; inspect cluster from the actual Ubuntu/k3s node"
else
  if ! kubectl cluster-info >/dev/null 2>&1; then
    warn "kubectl context cannot connect to k3s"
  else
    for resource in statefulset/email-postgres statefulset/email-redis; do
      if kubectl -n "$namespace" rollout status "$resource" --timeout=5s >/dev/null 2>&1; then
        pass "$resource ready"
      else
        warn "$resource not ready"
      fi
    done
    if kubectl -n "$namespace" rollout status deployment/email-provider --timeout=5s \
        >/dev/null 2>&1; then
      pass "email-provider deployment ready"
    else
      warn "email-provider deployment not yet healthy"
    fi
    status="$(kubectl -n "$namespace" get pvc email-mail-objects \
      -o jsonpath='{.status.phase}' 2>/dev/null || true)"
    if [[ "$status" == Bound ]]; then
      pass "mail-object PVC Bound"
    else
      warn "email-mail-objects PVC not Bound (found: ${status:-missing})"
    fi
  fi
fi

if ! command -v bash >/dev/null; then
  warn "Bash unavailable"
else
  root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
  if bash -n "$root/postfix/inbound-r10/udmail-inbound-pipe.sh"; then
    pass "Postfix pipe syntax"
  else
    warn "Postfix pipe syntax invalid"
  fi
  if bash -n "$root/tests/inbound-r10-api-smoke.sh"; then
    pass "Private intake smoke syntax"
  else
    warn "Private intake smoke syntax invalid"
  fi
fi

if command -v postconf >/dev/null; then
  if postconf -m 2>/dev/null | grep -qx pgsql; then
    pass "Postfix PostgreSQL dictionary type available"
  else
    warn "Postfix pgsql dictionary missing; install postfix-pgsql on MX host"
  fi
else
  warn "Postfix not installed here (expected for app-only node)"
fi

if command -v ss >/dev/null; then
  for port in 2526 8010; do
    if ss -lntH "( sport = :$port )" 2>/dev/null | grep -q .; then
      pass "TCP listener :$port found (verify loopback binding and ownership)"
    else
      warn "No local TCP listener :$port (expected before staging services start)"
    fi
  done
fi
check "Open relay, TLS, private ACL, actual scanners and backed-up disk remain manual gates"
if ((warnings>0)); then
  printf '[SUMMARY] %s staging blockers or missing prerequisites\n' "$warnings"
  exit 1
fi
pass "Basic read-only preflight checks; NOT proof of end-to-end SMTP delivery"
