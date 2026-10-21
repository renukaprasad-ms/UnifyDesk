# Postfix feedback integration

The application records `HANDED_OFF` when local Postfix accepts a message. Final recipient outcomes are fed back through two independent sources.

## 1. Postfix smtp log agent

Postfix logs remote delivery results such as `status=sent`, `status=deferred` and `status=bounced`. Stream those lines to:

```bash
tail -F /var/log/mail.log | \
  EMAIL_PROVIDER_FEEDBACK_TOKEN='<secret>' \
  EMAIL_PROVIDER_FEEDBACK_URL='http://127.0.0.1:8010/internal/v1/feedback/postfix' \
  python3 /opt/unifydesk/postfix_feedback_agent.py
```

For production, run this as a supervised systemd service instead of an interactive shell.

## 2. Inbound DSN pipe

Use an envelope-from domain such as `bounce.unifydesk.com` and publish its MX record to the feedback mail node. UnifyDesk uses VERP:

```text
bounce+<messageId>.<recipientId>@bounce.unifydesk.com
```

Route mail for that domain into `dsn_feedback_pipe.py`. Example transport concept:

```text
# /etc/postfix/transport
bounce.unifydesk.com unifydesk_dsn:
```

Example pipe service concept:

```text
# /etc/postfix/master.cf
unifydesk_dsn unix  -       n       n       -       -       pipe
  flags=Rq user=unifydesk argv=/opt/unifydesk/dsn_feedback_pipe.py ${recipient}
```

Then run:

```bash
postmap /etc/postfix/transport
postfix reload
```

The exact Postfix deployment must be reviewed for the target mail node.

## Security

The internal endpoints are disabled by default. Enable them with:

```bash
EMAIL_PROVIDER_FEEDBACK_ENABLED=true
EMAIL_PROVIDER_FEEDBACK_TOKEN='<long-random-secret>'
```

Keep the endpoints private to trusted mail nodes/firewalls. The token is an application-layer guard, not a replacement for network isolation or TLS.
