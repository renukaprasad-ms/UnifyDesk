# Go-Live Checklist

Use this checklist against one exact release-candidate SHA. Unchecked items are not implicitly satisfied.

## Governance and source

- [ ] Exact release candidate SHA recorded.
- [ ] `dev` protected.
- [ ] `main` protected.
- [ ] Required status checks configured and enforced.
- [ ] Release PR reviewed.
- [ ] Open P0 findings = 0.
- [ ] Accepted P1 risks have owner/date/rationale.
- [ ] Release tag/version prepared.

## Build and test evidence

- [ ] Backend Spotless executed.
- [ ] Backend unit tests executed.
- [ ] Backend integration tests executed.
- [ ] Backend package executed.
- [ ] Frontend install/test/build/lint/format executed.
- [ ] Email-provider checks executed if included.
- [ ] Website build executed if included.
- [ ] No zero-step workflow result counted as PASS.

## Database

- [ ] Clean Flyway migration executed through current head.
- [ ] Representative supported upgrade migration executed.
- [ ] Flyway validation executed.
- [ ] `FLYWAY_BASELINE_ON_MIGRATE=false` explicitly configured for production.
- [ ] Pre-deploy database backup created.
- [ ] Restore procedure rehearsed and evidence recorded.
- [ ] Migration rollback/forward-fix classification reviewed.

## Security

- [ ] SEC/QA reviewed exact candidate SHA.
- [ ] JWT signing keys provisioned securely.
- [ ] Cookie `Secure=true` in HTTPS production.
- [ ] Cookie domain/SameSite behavior tested.
- [ ] CSRF flow tested from production frontend origin.
- [ ] CORS only allows intended origins.
- [ ] Trusted proxy/forwarded-header behavior explicitly configured.
- [ ] Provider credentials encrypted/secret-managed.
- [ ] Secret rotation procedure exists.
- [ ] Public webhooks validate provider authenticity.
- [ ] OAuth redirect URIs exact and production-approved.
- [ ] Logs reviewed for tokens, credentials and unnecessary PII.
- [ ] Privileged platform-admin flows reviewed.

## Infrastructure

- [ ] Immutable backend image digest recorded.
- [ ] Immutable frontend image digest recorded.
- [ ] Images were built by CI from candidate SHA.
- [ ] TLS/certificates valid.
- [ ] Backend readiness/liveness/startup probes validated.
- [ ] Frontend health probe validated.
- [ ] PostgreSQL production durability/HA approved.
- [ ] Redis production durability/HA approved.
- [ ] Kafka production durability/replication approved.
- [ ] Resource requests/limits based on measured capacity.
- [ ] Rolling/deployment behavior tested.
- [ ] Rollback to previous app image tested.

## Observability and operations

- [ ] Prometheus/metrics scraped.
- [ ] API error/latency dashboards exist.
- [ ] DB connection/latency/storage alerts exist.
- [ ] Redis health alerts exist.
- [ ] Kafka lag/retry/DLT alerts exist.
- [ ] Broadcast/provider failure alerts exist.
- [ ] Notification worker/delivery alerts exist.
- [ ] Webhook failures/replay alerts exist.
- [ ] Central logs available with retention/access policy.
- [ ] Incident owner/on-call path defined.
- [ ] Backup failure and capacity alerts exist.

## Provider validation

Enable/check only providers included in release.

### Gmail / Microsoft / mailbox channels

- [ ] OAuth connect executed.
- [ ] Initial sync executed.
- [ ] Incremental/realtime callback executed.
- [ ] Send/reply/read state executed.
- [ ] Token refresh/revocation path exercised.

### Managed Email / SES

- [ ] SES production account/region approved.
- [ ] Hosted/customer domain provisioning executed.
- [ ] DNS/DKIM verification executed.
- [ ] Send executed.
- [ ] Bounce/complaint/delivery event executed.
- [ ] Rate/daily safety cap behavior exercised.
- [ ] Suppression/reputation behavior verified.

### Managed SMS / MSG91 / DLT

- [ ] Provider commercial/account topology confirmed.
- [ ] PE/Header/template/Flow production path exercised.
- [ ] Send executed.
- [ ] Delivery webhook verified/authenticated.
- [ ] Wallet/credit/rate/refund semantics confirmed.
- [ ] DLT compliance ownership approved.

### Meta WhatsApp

- [ ] Embedded Signup executed.
- [ ] WABA/phone authorization verified.
- [ ] Phone registration/subscribed_apps verified.
- [ ] Signed webhook executed.
- [ ] Text/template/media sends executed.
- [ ] Delivered/read callbacks executed.
- [ ] Retry-After/rate limiting behavior verified where safe.
- [ ] Permission revocation/degraded path exercised.

### Google Ads / Meta Ads

- [ ] OAuth/provider account connect executed.
- [ ] Account/resource discovery executed.
- [ ] Representative campaign create/launch executed in approved test account.
- [ ] Insights/status sync executed.
- [ ] Provider errors/partial failure recovery exercised.
- [ ] Billing/cost/permission/approval constraints documented.

## Load and resilience

- [ ] Contacts import/export/bulk representative load executed.
- [ ] Segment/audience snapshot load executed.
- [ ] Broadcast 2k/10k-scale preparation/delivery simulation executed as applicable.
- [ ] Notification fanout/digest load executed.
- [ ] Kafka retry/DLT recovery executed.
- [ ] Redis outage/fail-closed behavior exercised where security/rate limiting depends on it.
- [ ] Provider throttling/backoff behavior exercised.
- [ ] Process restart/idempotency recovery exercised.
- [ ] Staging soak completed.

## Final release

- [ ] Release notes include enabled/disabled capabilities.
- [ ] Production configuration reviewed separately from development defaults.
- [ ] Deploy exact tested image digests.
- [ ] Run post-deploy smoke checks.
- [ ] Run provider smoke checks.
- [ ] Confirm metrics/logs healthy.
- [ ] Record deployment evidence.
- [ ] Keep previous immutable artifacts and rollback procedure available.
