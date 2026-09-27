# Managed SMS Security Model

Status: FROZEN for Round 1

## Trust boundaries

Customer browser
-> authenticated UnifyDesk API
-> managed control/runtime policy
-> platform-held MSG91 credentials
-> MSG91/DLT ecosystem

MSG91 webhook
-> public callback trust gate
-> immutable event inbox
-> asynchronous reconciler

## Core authorization invariant

Every company-scoped resource lookup must include company_id. A valid object ID without matching company ownership grants no access.

Test cross-company substitution for:
tenant, DLT profile, Header, template, template version, sender, provider binding, run, recipient, usage and review evidence.

## Compliance-chain invariant

A provider call is permitted only when:
company == run.company
== sender.company
== tenant.company
== profile.company
== header.company
== templateVersion.company
== providerBinding.company

and every required state is ACTIVE/READY/fresh.

## Raw-ID manipulation

Never accept client-supplied:
- provider child/client ref;
- provider Flow ID;
- provider template ref;
- provider route;
- provider account ID;
- another raw PE/Header/template ID as a runtime override.

These are loaded from server-side bindings only.

## Arbitrary SMS injection

Managed SMS create/update must reject scratch text as authoritative content.
Only immutable approved template + declared variables can produce send content.
Variable substitution:
- only declared keys;
- max lengths;
- declared type;
- no placeholder syntax injection;
- CTA URL/callback validation;
- final body structural validation/fingerprint-compatible rendering.

## Provider secrets

Master/child AuthKeys:
- never returned to frontend;
- never stored in setup-session JSON;
- never placed in managed BroadcastSenderConnection credentials;
- encrypted/secret-managed server-side;
- redacted from exceptions/logging/metrics;
- rotated without changing customer resource IDs.

## Webhook forgery/replay

Before callback state mutation:
- TLS;
- exact endpoint/account association;
- configured secret/header or provider signature;
- size/content-type/schema limits;
- raw-body hash;
- time/replay window where provider timestamps exist;
- unique event key;
- correlation ownership check;
- provider request ID consistency.

Unknown/untrusted callback is persisted only if operationally needed and never mutates recipient/billing state.

## Duplicate sends

Existing Broadcast claim/retry semantics remain authoritative.
Managed UNKNOWN outcomes are not blindly retried.
Correlation is attempt-scoped.
A duplicate DLR cannot trigger a new send.

## Duplicate billing

Reservation and usage idempotency keys are unique.
Only provider acceptance transition can commit the primary charge.
DLR delivery is never another billable transition.
Refund/credit uses an adjustment event with its own idempotency key.

## Distributed rate-limit bypass

All pods use shared Redis atomic scripts.
Required scopes:
platform/provider account/company/run/new-tenant.
No local-memory fallback that permits sending.

## Shared provider-balance exhaustion

Controls:
- global kill switch;
- provider reserve floor/circuit breaker;
- daily absolute platform cap;
- per-company caps;
- per-run campaign cap;
- new-tenant ramp;
- anomaly alert;
- tenant suspension;
- fail closed on known insufficient-credit state.

Customer APIs never reveal total shared wallet/capacity.

## Admin/manual-review compromise

Admin decisions:
- platform-admin permission;
- explicit action reason;
- before/after audit record;
- optimistic concurrency;
- no self-approval shortcut from company role;
- dangerous suspend/resume/provision actions separately permissioned;
- evidence is non-executable/non-HTML rendered safely.

## PII and message leakage

Do not log:
- full phone numbers;
- full SMS bodies;
- consent evidence bodies;
- AuthKeys;
- webhook secrets;
- raw webhook payload in normal logs.

Use masked destination and opaque correlation IDs. Raw provider event payload, if retained for reconciliation, has restricted DB access and retention.

## Stale compliance

Every release-blocking external/manual fact has checked_at and stale_after.
Launch fails closed if stale:
- PE/profile;
- PE–TM chain;
- Header;
- provider template binding where status can change.

## Historical integrity

Identifiers referenced by a run are snapshotted.
Archive rather than hard-delete.
Never let a new resource with the same visible Header/DLT Template ID inherit historical bindings.

## Provider error privacy

Translate raw provider errors into stable internal codes. Customer responses may say “template is not ready” or “compliance action required”; do not expose AuthKey/account/client/Flow details.

## Security test invariants

QA/SEC must prove:
1. Company A cannot read/use Company B PE/Header/template/sender.
2. A managed request cannot override provider identifiers.
3. Raw body cannot bypass approved template.
4. Invalid/extra variables cannot alter fixed text.
5. stale/suspended chain blocks launch and delivery.
6. a forged/duplicate webhook cannot mutate recipient or billing.
7. concurrent acceptance produces one charge.
8. UNKNOWN is not automatically resent.
9. Redis/policy outage does not bypass rate limits.
10. tenant cap cannot exhaust another tenant's reserved allowance.
11. admin review is audited and authorization-scoped.
12. logs/customer APIs contain no provider secret/topology or full recipient/body PII.
