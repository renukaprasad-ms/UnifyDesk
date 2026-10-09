# Email provider: internal authorization and branding

The service is a **private delivery plane**, not an end-user API. UnifyDesk's authenticated
backend must enforce company/workspace authorization and invoke these endpoints server-to-server.

- Configure `EMAIL_PROVIDER_INTERNAL_API_TOKEN` as a random secret of at least 32 UTF-8
  bytes. Send `Authorization: Bearer <token>` from the trusted backend. Never expose it
  to the frontend, and never send it over public HTTP.
- Absent or short secrets deny API requests with HTTP 503. Wrong or missing tokens yield 401.
  Only health endpoints are unauthenticated.
- Postfix feedback uses its own `EMAIL_PROVIDER_FEEDBACK_TOKEN`; its internal endpoints
  are exempt from the service API token but require the separate token and private routing.
- TLS/mTLS and network ACLs remain mandatory for traffic between nodes.
  The shared token does not carry per-tenant permissions.
- Switch feedback scripts and controller together: header names changed from
  `X-Reachlyo-*` to `X-UnifyDesk-*`.
- Review and replace example mail hostname, bounce domain, SPF/DKIM/DMARC and A/PTR/EHLO
  records with actual values before enabling sending.
