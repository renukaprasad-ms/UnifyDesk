# UnifyDesk Email API Product — R1 contract and operator workflow

This is a **standalone email-delivery product** with its own PostgreSQL, Redis, Kafka,
customer organization/project identifiers and credentials. It is not part of the
UnifyDesk App monolith. Hostnames are proposed only; deployment is not provisioned.

## Private administration — internal token

Admin routes are protected by `EMAIL_PROVIDER_INTERNAL_API_TOKEN`.
They must be reachable **only through the private trusted admin network**.
This is temporary operator provisioning for the first milestone, not the eventual
customer console or self-service user login. No customer should receive this token.

- `POST /internal/v1/email-product/organizations`: `{"name":"Demo Co","contactEmail":"ops@example.com"}`
- `GET /internal/v1/email-product/organizations/{organizationId}` and `POST /internal/v1/email-product/organizations/{organizationId}/status`: fetch/suspend/restore organization.
- `POST /internal/v1/email-product/organizations/{organizationId}/projects`: `{"name":"Marketing"}`
- `GET /internal/v1/email-product/organizations/{organizationId}/projects`
- `GET /internal/v1/email-product/projects/{projectId}` and `POST /internal/v1/email-product/projects/{projectId}/status`: fetch/suspend/restore project. Status payload is `{"status":"SUSPENDED"}`. Suspended projects lose sending capability.
- `POST /internal/v1/email-product/projects/{projectId}/keys`:
  `{"name":"UnifyDesk App","scopes":["SEND","READ","DOMAINS"]}`
- `GET /internal/v1/email-product/projects/{projectId}/keys`: metadata only, no raw secrets
- `DELETE /internal/v1/email-product/projects/{projectId}/keys/{keyId}`: revoke immediately
- `POST /internal/v1/email-product/projects/{projectId}/sending`: `{"enabled":true}`

The project is **not allowed to send by default**. Activate sending explicitly only
after reviewing DNS, account reputation, subscription/consent, abuse risk and quotas.
Keys use `ude_live_<12 hex chars>_<random secret>` format; the complete key is
shown **once** at issuance and only its SHA-256 digest is stored.

## Public project API — project scoped bearer key

Every `/v1/` call requires `Authorization: Bearer ude_live_...`.
The key implicitly identifies the project; the caller never passes companyId.
All reads/writes route through that project identifier.

- `POST /v1/domains` — DOMAINS scope, create sender domain and return DNS records.
- `GET /v1/domains`, `GET /v1/domains/{id}` — DOMAINS scope.
- `POST /v1/domains/{id}/verify` — DOMAINS scope.
- `POST /v1/emails` — SEND scope, **Idempotency-Key required**, max 100 recipients.
- `GET /v1/emails/{id}` — READ scope.
- `GET /v1/emails/{id}/events` — READ scope; per-recipient provider feedback.
- `POST /v1/email-batches` — SEND scope, **Idempotency-Key required**, up to 1000
  unique recipients per batch, automatically split into BCC-only chunks of 100
  recipients. Each recipient gets a separate SMTP submission.
- `GET /v1/email-batches/{id}` — READ scope, lists underlying chunk/message states.
- `GET /v1/reputation` — READ scope; date-bounded accepted/delivered/bounce metrics.
- `POST /v1/validations/jobs` — VALIDATE scope, max 1,000 addresses/job.
- `GET /v1/validations/jobs/{id}` and `GET /v1/validations/jobs/{id}/results` — VALIDATE scope.

Example batch request:

```http
POST /v1/email-batches HTTP/1.1
Authorization: Bearer ude_live_<project-key>
Idempotency-Key: campaign-123-part-1
Content-Type: application/json

{
  "sendingDomainId": 123456,
  "from": "news@verified-domain.example",
  "subject": "Hello",
  "text": "You requested this update.",
  "recipients": ["customer1@example.net", "customer2@example.org"]
}
```

Response is HTTP 202 with `data.batchId` and per-chunk message IDs. Accepted means
durably queued, **not delivered**. Poll batch/message status until the mail
node and feedback system have reconciled final recipient delivery. Unknown
Postfix outcomes must not be blindly re-sent.

Initial Redis admission caps are 1,000 recipients/project/day and
10,000 platform-wide/day (configurable), fail closed if Redis is unavailable.
Delivery workers also enforce platform/company/domain per-second caps.
Quota reservations may remain consumed on transaction failure until expiry;
a reconciliation worker is future work.

## UnifyDesk App integration

`UnifyDeskEmailApiClient` is a backend-only HTTP adapter scaffold within
`unifydesk-app/backend`, accepting a project-specific API key per call.
**Do not put API keys in the React app or a public connection DTO.**
Map each UnifyDesk workspace to an explicitly provisioned Email product project;
store the secret using the App's encrypted credential storage.

**Not yet wired** to production Broadcast route selection. Existing SES sending
must remain unchanged. Before enabling broadcasts through this product:
- Verify workspace marketing consent, unsubscribe, sender domain, PAYG reservation,
  abuse caps and campaign idempotency in UnifyDesk App.
- Wire each workspace's encrypted Email project key and sender/domain mapping
  to the broadcast connection/provider registry.
- Consume signed provider feedback (not yet implemented) or poll status
  conservatively, and reconcile App billing exactly once.
- On network timeout, treat result as UNKNOWN until the same idempotency key
  is reconciled; do not create another campaign ID automatically.

## React customer console

The React frontend in `email-provider/frontend/` now supports session-based login and activation, projects, sender-domain verification/DNS records, scoped API keys, webhooks, usage and account views. Initial organizations/owner invitations still require the trusted internal API over a loopback-only operator tunnel. The Nginx frontend does not proxy `/internal/v1/` or project-key `/v1/` routes.

## Not implemented yet / release gates

Self-service registration/SSO, automated customer onboarding and pricing,
full audit trails, attachment payloads, advanced
mail templates, subscription/usage billing, multiple sending IP pools and
IP warm-up, opt-out enforcement in product APIs, bounce reconciliation at scale,
detailed per-recipient bulk query, live host Postfix/DNS and end-to-end QA.
Do not enable unrestricted customer sending until these are implemented.
GitHub Actions configuration is intentionally unchanged.
