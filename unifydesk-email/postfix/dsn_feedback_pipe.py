#!/usr/bin/env python3
"""Post a raw inbound DSN to email-provider using the VERP envelope recipient."""

import os
import sys
import urllib.request

API_URL = os.environ.get(
    "EMAIL_PROVIDER_DSN_URL",
    "http://127.0.0.1:8010/internal/v1/feedback/dsn",
)
TOKEN = os.environ.get("EMAIL_PROVIDER_FEEDBACK_TOKEN", "")


def main():
    if not TOKEN:
        raise SystemExit("EMAIL_PROVIDER_FEEDBACK_TOKEN is required")
    if len(sys.argv) != 2:
        raise SystemExit("usage: dsn_feedback_pipe.py <envelope-recipient>")

    envelope_recipient = sys.argv[1]
    body = sys.stdin.buffer.read()
    request = urllib.request.Request(
        API_URL,
        data=body,
        headers={
            "Content-Type": "message/rfc822",
            "X-UnifyDesk-Feedback-Token": TOKEN,
            "X-UnifyDesk-Envelope-Recipient": envelope_recipient,
        },
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=15) as response:
        response.read()


if __name__ == "__main__":
    main()
