#!/usr/bin/env python3
"""Read Postfix smtp(8) log lines from stdin and forward delivery outcomes."""

import hashlib
import json
import os
import re
import sys
import urllib.error
import urllib.request
from datetime import datetime, timezone

API_URL = os.environ.get(
    "EMAIL_PROVIDER_FEEDBACK_URL",
    "http://127.0.0.1:8010/internal/v1/feedback/postfix",
)
TOKEN = os.environ.get("EMAIL_PROVIDER_FEEDBACK_TOKEN", "")
PATTERN = re.compile(
    r"\b(?P<queue>[A-F0-9]+):\s+to=<(?P<recipient>[^>]+)>.*?"
    r"dsn=(?P<dsn>[^,\s]+).*?status=(?P<status>sent|deferred|bounced)"
    r"\s+\((?P<diagnostic>.*)\)\s*$",
    re.IGNORECASE,
)

EVENTS = {
    "sent": "DELIVERED",
    "deferred": "DEFERRED",
    "bounced": "BOUNCED",
}


def post(payload):
    body = json.dumps(payload, separators=(",", ":")).encode("utf-8")
    request = urllib.request.Request(
        API_URL,
        data=body,
        headers={
            "Content-Type": "application/json",
            "X-UnifyDesk-Feedback-Token": TOKEN,
        },
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=10) as response:
        response.read()


def main():
    if not TOKEN:
        raise SystemExit("EMAIL_PROVIDER_FEEDBACK_TOKEN is required")

    for line in sys.stdin:
        match = PATTERN.search(line)
        if not match:
            continue

        status = match.group("status").lower()
        payload = {
            "queueId": match.group("queue").upper(),
            "eventType": EVENTS[status],
            "source": "POSTFIX_LOG",
            "externalEventId": hashlib.sha256(line.encode("utf-8")).hexdigest(),
            "smtpStatusCode": match.group("dsn"),
            "diagnostic": match.group("diagnostic"),
            "occurredAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        }
        try:
            post(payload)
        except (urllib.error.URLError, TimeoutError) as error:
            print(f"feedback post failed: {error}", file=sys.stderr)


if __name__ == "__main__":
    main()
