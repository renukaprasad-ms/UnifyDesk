# Security

## Tenant isolation

Every API reference is resolved under the authenticated company/workspace.

Never authorize by:
- resource id alone;
- company id supplied only in an async payload;
- provider-supplied tenant metadata without authoritative local correlation.

Workers must recover company ownership from server-owned run/snapshot/sender/connection rows.

## Cross-company frontend state

R1 must use a tenant generation/epoch for journey-local async state.

An async response may mutate state only when:
`capturedCompanyId == currentCompanyId && capturedEpoch == currentEpoch`.

This applies to:
- sender/readiness loads;
- audience preview;
- draft loads/saves;
- scheduled/send mutations;
- recipient pages;
- Inbox detail/snapshot;
- realtime callbacks;
- delayed timers/pollers.

The A -> B -> A sequence must not reactivate an old A request merely because the company id matches again. The epoch must change on every workspace transition.

## Audience ownership

Contact, List, Segment, snapshot, identity and sender references must all belong to the same company.

A source version/hash mismatch after freeze is a conflict/failure, never a reason to read a foreign or newer source silently.

## Provider credentials

Continue current encrypted credential storage. Never return:
- access keys;
- API keys;
- Meta access tokens;
- webhook secrets.

Customer UI gets product readiness and safe error codes. Internal/admin diagnostics may retain provider detail.

## Webhooks

Managed Email:
- preserve SNS signature/certificate/topic verification;
- preserve recipient/run/company/sender/domain correlation.

Managed SMS:
- preserve account token/correlation/request-id/replay-window checks.

WhatsApp:
- preserve Meta signature/trusted phone mapping and durable webhook event processing.

Any new direct-provider webhook must be provider-authenticated before it can mutate Broadcast/Contact state.

## Suppression / consent

Canonical Contact consent and suppression are safety data.

R1 provider feedback bridge:
- must be idempotent;
- must not allow a foreign provider connection to suppress another company's Contact;
- must not lift an immutable/legal/provider suppression;
- must preserve stronger existing suppression when events conflict.

## Inbox Contact association

Resolve using trusted conversation company id plus normalized identity.

Do not:
- create Contact based only on display name;
- match phone substrings;
- match unnormalized email text;
- cross company boundaries;
- expose whether an identity exists in another company.

If no exact identity exists, return UNMATCHED.

## WhatsApp Broadcast

Only an ACTIVE company-owned Meta connection/sender may be used.

Marketing Broadcast requires an approved/fresh template and validated parameters. Free-form session-window rules used by Inbox replies are not a substitute for Broadcast template requirements.

Reuse existing distributed per-phone limiter and provider cooldown.

## IDOR regression matrix

SEC/QA must test foreign:
- Contact;
- List;
- Segment;
- audience snapshot;
- Broadcast;
- sender;
- provider connection;
- Inbox conversation;
- Contact-link record.

Expected response must not reveal foreign resource existence.

## Logging

Do not log provider credentials, full webhook secrets, or full recipient payloads. Prefer company/run/recipient IDs and safe error codes.

## Verification

At the audit SHA, CI did not execute. Security source review is not executable proof. SEC/QA must run the authorization/concurrency/webhook suites on an allocated runner and suitable Postgres/Redis environment.
