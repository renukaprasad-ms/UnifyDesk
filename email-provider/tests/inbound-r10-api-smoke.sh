#!/usr/bin/env bash
# R10 PRIVATE HTTP intake test, opt-in only. It stores one synthetic MIME
# message twice in an explicitly approved staging mailbox, without public SMTP.
set -Eeuo pipefail
export LC_ALL=C
if [[ "${R10_API_SMOKE_ENABLE:-NO}" != YES ]]; then
  echo "No mail submitted. Set R10_API_SMOKE_ENABLE=YES after preparing a TEST mailbox." >&2
  exit 2
fi
recipient="${R10_TEST_RECIPIENT:-}"
if [[ ! "$recipient" =~ ^[A-Za-z0-9][A-Za-z0-9._+-]{0,63}@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)+$ ]]; then
  echo "R10_TEST_RECIPIENT must be an approved synthetic test mailbox" >&2; exit 2
fi
# No remote endpoint overrides or Cloudflare public URL are supported.
endpoint=http://127.0.0.1:8010/internal/v1/mail/inbound
curl_config="${R10_INTERNAL_CURL_CONFIG:-/etc/unifydesk-mail/inbound-curl.conf}"
if [[ ! -r "$curl_config" ]]; then
  echo "R10 private curl credential config is not readable" >&2; exit 2
fi
for cmd in curl python3 openssl; do
  command -v "$cmd" >/dev/null || { echo "Missing $cmd" >&2; exit 2; }
done
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
chmod 0700 "$work"
# Random key avoids collision with earlier tests; both submissions use the
# SAME MIME file and SAME key. No customer content is read.
random="$(openssl rand -hex 12)"
key="r10-api-smoke-$random"
printf 'From: R10 Test <sender@example.invalid>\r\nTo: %s\r\nSubject: R10 private intake test %s\r\nMessage-ID: <%s@example.invalid>\r\nMIME-Version: 1.0\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\nSynthetic R10-only test body.\r\n' \
  "$recipient" "$key" "$key" > "$work/fixture.eml"
post() {
  local output="$1" result code=000
  # Credentials are read by curl from a protected local config file, not
  # supplied as command-line tokens or saved into this test directory.
  if ! code="$(curl --config "$curl_config" \
      --url "$endpoint" --proto '=http' --noproxy '*' \
      --request POST --data-binary "@$work/fixture.eml" \
      --header "Content-Type: message/rfc822" \
      --header "X-UnifyDesk-Envelope-To: $recipient" \
      --header "X-UnifyDesk-Delivery-Key: $key" \
      --connect-timeout 3 --max-time 45 --retry 0 --silent --show-error \
      --output "$output" --write-out '%{http_code}')"; then
    echo "Local backend connection failed; do not enable MX" >&2; exit 1
  fi
  if [[ "$code" != 202 ]]; then
    echo "Backend returned $code rather than durable 202; check private logs" >&2
    exit 1
  fi
}
post "$work/first.json"
post "$work/retry.json"
python3 - "$work/first.json" "$work/retry.json" <<'PY'
import json,sys
first,retry=[json.load(open(path,encoding="utf-8")) for path in sys.argv[1:]]
for data in (first,retry):
    if data.get("status") is not True or data.get("status_code")!=202:
        raise SystemExit("Not a successful durable intake response")
    if not str(data.get("data",{}).get("id","")).isdigit():
        raise SystemExit("Missing durable intake ID")
a,b=first["data"],retry["data"]
if a["id"]!=b["id"] or a["duplicate"] is not False or b["duplicate"] is not True:
    raise SystemExit("Idempotency check failed: different job or duplicate flag")
print("PASS: backend returned 202 twice, one durable job ID, duplicate=true on retry")
print("Intake job ID:",a["id"],"Latest status:",b.get("state"))
PY
# Optional read-only PostgreSQL verification: pass a staging DSN and use
# ~/.pgpass or PGPASSFILE; don't put DB passwords in command arguments.
if [[ -n "${R10_QA_PG_DSN:-}" ]]; then
  command -v psql >/dev/null || { echo "psql required for database verification" >&2; exit 2; }
  root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
  psql "$R10_QA_PG_DSN" -v ON_ERROR_STOP=1 -v delivery_key="$key" \
    -f "$root/tests/inbound-r10-verify.sql"
fi
# Optional true mailbox read-path test: this uses a NORMAL mailbox console
# session (not the internal MTA bearer) to prove explicit ACL and scan promotion.
if [[ -n "${R10_TEST_MAILBOX_ID:-}" || -n "${R10_CONSOLE_CURL_CONFIG:-}" ]]; then
  mailbox_id="${R10_TEST_MAILBOX_ID:-}"
  console_config="${R10_CONSOLE_CURL_CONFIG:-}"
  if [[ ! "$mailbox_id" =~ ^[1-9][0-9]{0,18}$ || ! -r "$console_config" ]]; then
    echo "Both R10_TEST_MAILBOX_ID and readable R10_CONSOLE_CURL_CONFIG required" >&2
    exit 2
  fi
  # At most 90 seconds for async ClamAV/Rspamd. Never expose console token
  # in process arguments or logs. No action here modifies the mailbox.
  found=""
  for attempt in {1..18}; do
    for folder in INBOX SPAM; do
      curl --config "$console_config" \
        --url "http://127.0.0.1:8010/console/v1/mail/mailboxes/$mailbox_id/messages?folder=$folder&limit=100" \
        --proto '=http' --noproxy '*' --retry 0 \
        --connect-timeout 3 --max-time 10 --silent --show-error --fail \
        --output "$work/$folder.json" ||
        { echo "Console mailbox read failed: verify session and ACL" >&2; exit 1; }
    done
    if found="$(python3 - "$key" "$work/INBOX.json" "$work/SPAM.json" <<'PY'
import json,sys
needle=sys.argv[1]
for path,folder in zip(sys.argv[2:],("INBOX","SPAM")):
    result=json.load(open(path,encoding="utf-8"))
    if result.get("status") is not True: raise SystemExit("Mailbox read not authorized")
    for m in result.get("data",[]):
        if needle in (m.get("subject") or "") and m.get("folder")==folder:
            print(m["id"]+":"+folder)
            raise SystemExit(0)
raise SystemExit(1)
PY
)"; then
      break
    fi
    sleep 5
  done
  if [[ -z "$found" ]]; then
    echo "MIME did not appear in Inbox or Spam within 90s; inspect scanner jobs" >&2
    exit 1
  fi
  message_id="${found%%:*}"
  folder="${found#*:}"
  curl --config "$console_config" \
    --url "http://127.0.0.1:8010/console/v1/mail/mailboxes/$mailbox_id/messages/$message_id/preview" \
    --proto '=http' --noproxy '*' --retry 0 \
    --connect-timeout 3 --max-time 15 --silent --show-error --fail \
    --output "$work/preview.json"
  python3 - "$work/preview.json" <<'PY'
import json,sys
result=json.load(open(sys.argv[1],encoding="utf-8"))
data=result.get("data") or {}
if result.get("status") is not True or data.get("textAvailable") is not True:
    raise SystemExit("Plain-text preview was not authorized or decoded")
if "Synthetic R10-only test body." not in (data.get("plainText") or ""):
    raise SystemExit("Preview body does not match test MIME")
print("PASS: scanner promoted MIME and authorized reader returned expected plain-text")
PY
  printf 'Mailbox visible in %s, message ID %s\n' "$folder" "$message_id"
fi
echo "R10 local API smoke complete; public SMTP/port 25 remain untested."

