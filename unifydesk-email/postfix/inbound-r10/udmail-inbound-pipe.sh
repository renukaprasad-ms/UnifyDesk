#!/usr/bin/env bash
# Dedicated, private R10 Postfix pipe; install only after isolated QA.
# Postfix pipe(8) supplies EXACTLY queue_id and one envelope recipient.
# API bearer token stays in protected curl config, never argv or logs.
set -Eeuo pipefail
export LC_ALL=C
if [[ "$#" -ne 2 ]]; then
  echo "UnifyDesk inbound: incorrect pipe recipient count" >&2
  exit 75
fi
queue_id="$1"
recipient="$2"
if [[ ! "$queue_id" =~ ^[A-Za-z0-9]{5,64}$ ]]; then
  echo "UnifyDesk inbound: invalid Postfix queue identifier" >&2
  exit 75
fi
# Same safe local-part format as InboundIntakeService.parseRecipient.
if [[ ! "$recipient" =~ ^[A-Za-z0-9][A-Za-z0-9._+-]{0,63}@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)+$ ]]; then
  echo "UnifyDesk inbound: unsupported envelope recipient" >&2
  exit 75
fi
credentials=/etc/unifydesk-mail/inbound-curl.conf
node_file=/etc/unifydesk-mail/inbound-node-id
if [[ ! -f "$credentials" || ! -r "$credentials" || ! -f "$node_file" ]]; then
  echo "UnifyDesk inbound: private adapter not configured" >&2
  exit 75
fi
IFS= read -r node_id < "$node_file" || true
if [[ ! "${node_id:-}" =~ ^[A-Za-z0-9][A-Za-z0-9-]{0,23}$ ]]; then
  echo "UnifyDesk inbound: stable node ID unavailable" >&2
  exit 75
fi
# Postfix retries have the same queue ID + recipient. Different recipient
# and node IDs cannot share the delivery key.
recipient_digest="$(printf '%s' "${recipient,,}" | sha256sum)"
recipient_digest="${recipient_digest:0:24}"
delivery_key="mx-${node_id}-${queue_id}-${recipient_digest}"
# Loopback-only bridge to the private email-provider backend. No HTTP
# redirects, no shell-buffered mail body, no public proxy or cloud function.
if http_code="$(/usr/bin/curl --config "$credentials" \
    --url "http://127.0.0.1:8010/internal/v1/mail/inbound" \
    --proto '=http' --noproxy '*' \
    --request POST --upload-file - \
    --header "Content-Type: message/rfc822" \
    --header "X-UnifyDesk-Envelope-To: $recipient" \
    --header "X-UnifyDesk-Delivery-Key: $delivery_key" \
    --connect-timeout 3 --max-time 40 --retry 0 \
    --output /dev/null --write-out '%{http_code}' \
    --silent --show-error)"; then
  if [[ "$http_code" == 202 ]]; then
    exit 0
  fi
fi
# sysexits EX_TEMPFAIL=75: do NOT tell Postfix delivery succeeded unless
# Spring durably staged the MIME and answered 202. Postfix can retry.
echo "UnifyDesk inbound: private intake did not acknowledge durable acceptance" >&2
exit 75
