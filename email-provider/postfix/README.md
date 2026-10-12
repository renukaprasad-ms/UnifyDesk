# Postfix handoff

The Spring service hands a fully-built and DKIM-signed RFC 5322 message to a trusted local Postfix instance over SMTP.

## State meaning

A local Postfix `250` response means **HANDED_OFF**, not DELIVERED. Postfix then owns remote MX routing and retries. Final delivery/bounce state must later be reconciled from Postfix DSN/log feedback.

## Production prerequisites

- Dedicated/static public IPv4.
- Outbound TCP/25 permitted.
- PTR/rDNS under your control.
- Forward A record and PTR matching the SMTP EHLO hostname.
- Postfix reachable only from trusted application hosts.
- STARTTLS for outbound remote delivery.
- Queue/log/DSN ingestion added before relying on final delivery analytics.

Copy `main.cf.example` into the target mail node only after reviewing it for that host.

The email-provider quarantines ambiguous SMTP submissions as `UNKNOWN` and never
blindly retries them. Inspect the Postfix queue and feedback before reconciliation.
