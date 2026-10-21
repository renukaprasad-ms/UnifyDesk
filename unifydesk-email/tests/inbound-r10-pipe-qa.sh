#!/usr/bin/env bash
# Offline R10 pipe QA. Tests a TEMP COPY with path-only substitutions.
# No live Postfix, DB, SMTP, tokens, public network or MX access.
set -Eeuo pipefail
export LC_ALL=C
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
original="$repo_root/postfix/inbound-r10/udmail-inbound-pipe.sh"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
printf '%s\n' 'test-fake' > "$work/inbound-curl.conf"
printf '%s\n' 'mx1' > "$work/inbound-node-id"
sed -e "s|/usr/bin/curl|$work/fake-curl|g" \
  -e "s|/etc/unifydesk-mail/inbound-curl.conf|$work/inbound-curl.conf|g" \
  -e "s|/etc/unifydesk-mail/inbound-node-id|$work/inbound-node-id|g" \
  "$original" > "$work/pipe"
bash -n "$work/pipe"
cat > "$work/fake-curl" <<'MOCK'
#!/usr/bin/env bash
set -Eeuo pipefail
printf '%s\n' "$@" > "$R10_FAKE_LOG"
cat >/dev/null
if [[ "${R10_FAKE_EXIT:-0}" != 0 ]]; then exit "$R10_FAKE_EXIT"; fi
printf '%s' "${R10_FAKE_STATUS:-202}"
MOCK
chmod 0700 "$work/fake-curl"
export R10_FAKE_LOG="$work/headers"
fixture=$'From: sender@example.net\r\nTo: owner@example.com\r\nSubject: R10 QA\r\n\r\nhello\r\n'
invoke() {
  local expected="$1" status="$2" client_exit="$3" recipient="${4:-owner@example.com}"
  local actual=0
  printf '%s' "$fixture" | R10_FAKE_STATUS="$status" R10_FAKE_EXIT="$client_exit" \
    bash "$work/pipe" '3Pt2mN2VXxznjll' "$recipient" \
    >"$work/stdout" 2>"$work/stderr" || actual=$?
  if [[ "$actual" != "$expected" ]]; then
    printf 'exit was %s, expected %s\n' "$actual" "$expected" >&2
    cat "$work/stderr" >&2
    exit 1
  fi
}
invoke 0 202 0
grep -qFx 'X-UnifyDesk-Envelope-To: owner@example.com' "$work/headers"
key="$(grep '^X-UnifyDesk-Delivery-Key: mx-mx1-' "$work/headers")"
[[ -n "$key" && ! -s "$work/stdout" ]]
invoke 0 202 0
[[ "$(grep '^X-UnifyDesk-Delivery-Key: mx-mx1-' "$work/headers")" == "$key" ]]
invoke 75 503 0
invoke 75 302 0
invoke 75 200 0
invoke 75 000 7
invoke 0 202 0 other@example.com
other="$(grep '^X-UnifyDesk-Delivery-Key: mx-mx1-' "$work/headers")"
[[ "$key" != "$other" ]]
rm -f "$work/headers"
bad=0
printf '%s' "$fixture" | bash "$work/pipe" '3Pt2mN2VXxznjll' \
  'invalid@localhost' >/dev/null 2>&1 || bad=$?
[[ "$bad" == 75 && ! -e "$work/headers" ]]
bad=0
printf '%s' "$fixture" | bash "$work/pipe" 'bad/id' \
  'owner@example.com' >/dev/null 2>&1 || bad=$?
[[ "$bad" == 75 && ! -e "$work/headers" ]]
echo "PASS: R10 pipe 202-only ACK, defer-on-error, stable keys, strict inputs"
