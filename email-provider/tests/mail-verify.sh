#!/usr/bin/env bash
# Run locally/QA on an isolated developer machine; no cloud CI minutes required.
# Requires JDK 25, Maven wrapper, Node 24, and any DB/services required by tests.
set -Eeuo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
backend="$root/backend"
frontend="$root/frontend"
echo "== UnifyDesk Mail: R1–R4 verification =="

if ! command -v java >/dev/null || ! java -version 2>&1 | grep -q '"25'; then
  echo "ERROR: JDK 25 is required." >&2
  exit 2
fi
if ! command -v node >/dev/null || [[ "$(node -p 'process.versions.node.split(".")[0]')" != "24" ]]; then
  echo "ERROR: Node 24 is required for frontend build parity." >&2
  exit 2
fi

cd "$backend"
bash ./mvnw -B spotless:check
bash ./mvnw -B clean test
bash ./mvnw -B package -DskipTests

cd "$frontend"
if [[ -f package-lock.json ]]; then
  npm ci --no-audit --no-fund
else
  npm install --no-audit --no-fund
fi

VITE_MAIL_UI_ENABLED=false npm run build
VITE_MAIL_UI_ENABLED=true npm run build
echo "Local build/unit checks completed. Manual PostgreSQL, scanner, tenant and disaster-recovery QA remain mandatory."
