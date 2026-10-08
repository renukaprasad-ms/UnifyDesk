# MetaReach managed SMS R1 — Implementation and rollout

Branch: `feature/managed-provider-routing-metareach-r1` (draft PR #344)
Original frozen dev base: `ceb90e707faf6f8a9306a2f6204301a366f1489b`.

## Provider switch
Platform > Managed Providers is **Super Admin-only**. Global EMAIL remains AMAZON_SES; future UnifyDesk Email is disabled. Global SMS remains MSG91_SMS by default. SMS companies can receive independent overrides: MSG91_SMS / METAREACH_SMS / inherit global. Route changes are audited and only future managed runs use the selected provider. The final provider is immutable in `managed_sms_run_bindings.delivery_provider`.

BroadcastSenderProvider.MSG91_SMS is retained as the logical managed SMS Broadcast connection for legacy compatibility. The **actual external delivery provider** is the separately frozen run binding. Do not expose the internal provider to customer-managed sender selection.

## MetaReach API
Published bulk SMS API documented at https://metareachmarketing.com/sms-api-documentation.php:
- HTTPS GET `/vb/apikey.php` with `apikey`, `senderid`, `number`, `message`, `templateid`, `route`, `format=json`, optionally `unicode=2`.
- HTTPS GET `/vb/http-credit.php` with `apikey`, `route_id`, `format=json`.
- HTTPS GET `/vb/http-dlr.php` with `apikey`, `msgid`, `format=json`.

Only bulk-SMS DLR polling is documented; do **not** assume RCS/OTP webhook contracts apply to bulk SMS.

GET carries the key/message in the URI. Do not log outbound URIs, request objects, or exception causes containing request URLs. Verify provider support for POST/header authentication before production.

## Backend configuration
All are backend-only. Do not put API keys in the frontend:
```
APP_MANAGED_SMS_ENABLED=true
APP_MANAGED_SMS_PLATFORM_SENDING_ENABLED=true
APP_MANAGED_SMS_METAREACH_ENABLED=false
APP_MANAGED_SMS_METAREACH_CONTRACT_CONFIRMED=false
APP_MANAGED_SMS_METAREACH_API_KEY=
APP_MANAGED_SMS_METAREACH_ROUTE_ID=1
APP_MANAGED_SMS_METAREACH_DLR_POLL_MS=30000
APP_MANAGED_SMS_METAREACH_DLR_RECLAIM_MS=120000
```
**Never flip `CONTRACT_CONFIRMED` to true without provider confirmation** of the specific UnifyDesk master account, multiple-customer PE IDs/PE–TM chain, approved route, and DLR behavior.

## Super Admin onboarding workflow
1. Confirm MetaReach account, DLT PE/TM scope, Header, template ID and route. Never store an unverified provider ID or claim automatic provider mapping.
2. Enable backend config with credentials and confirmed provider contract, validate balance observation from `http-credit.php`.
3. Platform > Managed Providers > MetaReach company authorisation: enter company ID, the *exact* currently active DLT PE ID, and a genuine provider account mapping/evidence reference; provide a meaningful reason.
4. Review existing Header and DLT content template in Managed SMS admin. MetaReach does not use an MSG91 Flow; use the DLT template ID as `providerTemplateRef` and attach verified provider mapping evidence as `providerMappingRef`. Leaving these unverified fails closed.
5. Set the SMS company override to MetaReach, do not change the global default during initial rollout.
6. Recheck the company's Managed SMS status and complete template review under the now-selected provider. Sending requires active current DLT/PE–TM, template/header, reviewed mapping, provider balance and company authorisation.

## Delivery and billing
- The shared Broadcast execution system renders frozen approved SMS and enforces consent/financial and rate policies.
- MetaReach sends one recipient per request, preserving `messageid` + local correlation. A definite provider acceptance commits usage once; failed pre-acceptance request releases it; ambiguous submission is UNKNOWN and is not blindly resent.
- Durable polling jobs track each accepted recipient by message ID and normalized Indian number. DLR statuses are normalized and written to the existing trusted event inbox; the projection service handles recipient delivery, stats and usage. Duplicate receipts are idempotent.
- Pending DLR jobs back off, expire their frequent polling after approximately two hours, and can be repaired from accepted correlations if enrollment failed. Expiry is **not** equivalent to delivery failure.
- Provider wallet snapshots are separate (`metareach:master`) and fresh balance data is mandatory for sending.
- Company switches never rewrite the historical run provider.

## External release blockers
- Provider-approved multi-PE/TM account contract and exact route behavior.
- Live key and sandbox/production response tests, balances, DLT Headers/templates.
- Provider's DLR retention, rate limits, account isolation and webhook availability.
- Successful Maven tests, Flyway V99–V103 and frontend build/lint/format tests. GitHub Actions can fail at runner initialization with no job steps; that does not constitute a pass.
- End-to-end tenant isolation and billable acceptance/reconciliation testing in a real account.

No customer-facing provider credentials or automatic provider fallback.
