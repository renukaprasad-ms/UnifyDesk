# Managed SMS Research — India DLT and MSG91

Research snapshot: **2026-09-27**  
Preference: primary TRAI and official MSG91 documentation.  
Labels used below: **CONFIRMED**, **ARCHITECTURE DECISION**, **UNRESOLVED**, **REAL-ACCOUNT TEST**.

## 1. Regulatory baseline

### CONFIRMED — Principal Entity requirements

TRAI's Advice to Senders says Principal Entities sending bulk/commercial communication must register as a PE, obtain a Header, register Content Templates, and transmit PE ID + Header + Content ID to TSPs. Consent templates/consumer consent apply where required.

Source:
- https://www.trai.gov.in/advice-to-senders

Implication: UnifyDesk cannot treat “sender text” as an arbitrary alias. The PE/Header/Template tuple is compliance data and must be an eligibility gate.

### CONFIRMED — PE–TM chain is enforced

TRAI's 30-Nov-2024 direction requires Access Providers to reject messages from 11-Dec-2024 where the complete PE–TM chain is not defined or does not match the pre-defined chain.

Sources:
- https://www.trai.gov.in/sites/default/files/2024-12/Direction_30112024.pdf
- https://trai.gov.in/sites/default/files/2024-12/PR_No.92of2024.pdf

MSG91's current DLT process also places PE–TM chain initiation before Header and Content Template onboarding:
- https://msg91.com/help/dlt-registration-in-india/dlt-process

Implication: “customer DLT approved” is not sufficient. UnifyDesk needs an ACTIVE chain assertion/evidence and must fail closed when the chain is absent/suspended.

### CONFIRMED — current message classification

The 2025 Second Amendment revised definitions for Promotional, Service and Transactional communication. Promotional content mixed into another type is treated as Promotional. Transactional communication is tightly tied to a customer-initiated transaction and time window; Service covers specified service/customer communications and certain explicit-consent transaction facilitation.

Sources:
- https://trai.gov.in/telecom-commercial-communications-customer-preference-second-amendment-regulations-2025
- https://www.trai.gov.in/sites/default/files/2025-02/Regulation_12022025_0.pdf

Architecture stores the regulatory category rather than old provider “route” folklore. Provider route/config is derived from the approved binding, not customer input.

### CONFIRMED — promotional opt-out and preferences

The 2025 framework requires an opt-out mechanism in promotional messages as specified by TRAI and delivery remains subject to Preference/Consent Register rules. Promotional content mixed with Service/Transactional content is promotional.

Source:
- https://www.trai.gov.in/sites/default/files/2025-02/Regulation_12022025_0.pdf

Architecture implication: a Promotional template cannot become ACTIVE unless its approved fixed template already satisfies the required opt-out form for the current rule set. UnifyDesk must not append arbitrary text after DLT approval.

### CONFIRMED — 2026 Third Amendment is newly notified, staged

TRAI lists the Third Amendment dated 18-Sep-2026. The final notification states most provisions commence after 30 days, specified regulations after 60 days, and Annexure changes after 90 days from Gazette publication.

Official index:
- https://trai.gov.in/release-publication/regulations/amendments-page/7617

As of 2026-09-27, architecture must not pretend all Third Amendment provisions are already operational. A compliance ruleset/version field is therefore mandatory so policy can be updated by effective date without rewriting historical sends.

### CONFIRMED — CTA whitelisting

MSG91's current India DLT guidance says URLs/APKs/OTT/WABA links/callback numbers in DLT SMS must be whitelisted; non-whitelisted CTA content can be rejected. Static and dynamic CTA rules differ, subdomains may require separate upload, and IP-based URLs are not accepted.

Sources:
- https://msg91.com/help/dlt-registration-in-india/cta-whitelisting-faqs
- https://msg91.com/help/dlt-registration-in-india/how-to-whitelist-urls-and-callback-numbers-on-dlt

Architecture implication: CTA declarations belong to the approved template version. Runtime URL/callback variables must be validated against the approved variable tag/CTA policy.

### CONFIRMED — variable tagging and scrubbing

MSG91 documents typed DLT variables and current validation examples (numeric, URL, callback, email, alphanumeric), with operator-specific registration mechanics. It also documents DLT scrubbing failures when PE/Header/Template/content do not match.

Sources:
- https://msg91.com/help/dlt-registration-in-india/variable-tagging-on-dlt-sms-templates
- https://msg91.com/help/delivery-report/dlt-related-failure-reasons

Architecture implication: store a variable schema per exact template version and reject values locally before provider submission. Provider/DLT scrubbing remains authoritative.

## 2. Can a B2B platform send for customer PEs?

### CONFIRMED — MSG91 explicitly documents a B2B platform model

MSG91 says a B2B platform can ask each client to complete DLT registration and then:
- map the client's DLT-approved Header and DLT Entity ID in the MSG91 account whose AuthKey sends; or
- for supported APIs, pass PE_ID;
- map/pass the client's DLT Template ID.

Source:
- https://msg91.com/help/dlt-registration-in-india/dlt-key-points-for-b2b-platforms

This directly establishes technical feasibility for platform-owned MSG91 infrastructure carrying traffic for customer PEs.

### CONFIRMED — provider-side PE/Header and template mapping

MSG91 documents mapping a PE ID to an approved Header/Sender ID and mapping an approved DLT Template ID to an MSG91 SMS template/Flow.

Sources:
- https://msg91.com/help/dlt-registration-in-india/map-your-dlt-entity-pe-id-with-dlt-approved-header-sender-id
- https://msg91.com/help/dlt-registration-in-india/map-approved-dlt-template-id-with-respective-flow-id-on-msg91-panel

### CONFIRMED — subaccount/client isolation exists

MSG91 Subaccount supports child accounts while master billing/control remains with the master account. Official reseller API documentation also exposes Add Client operations.

Sources:
- https://msg91.com/help/all-about-subaccount-in-msg91
- https://docs.msg91.com/subaccount-1
- https://api.msg91.com/apidoc/reseller/add-clients.php

### ARCHITECTURE DECISION — production topology

Preferred:
UnifyDesk master/subaccount
→ one provider child/client per UnifyDesk company/PE
→ only that company's PE/Header/Templates mapped in that child
→ child credentials retained only by UnifyDesk server-side
→ master billing remains platform-owned.

Fallback:
one MSG91 master account with multiple customer PE/Header/Template mappings, only after MSG91 confirms the commercial/account arrangement and exact PE–TM chain implementation in writing.

### UNRESOLVED — UnifyDesk's exact DLT telemarketer registration role

TRAI describes Registered Telemarketers as entities registered with Access Providers and engaged in transmission/delivery/scrubbing/aggregation. Managed SMS is an aggregation/orchestration product and therefore must not launch under an assumed role.

Source:
- https://trai.gov.in/hi/node/10413

Before production, obtain from MSG91/access-provider onboarding:
- UnifyDesk's required TM registration/classification;
- the TM IDs that must appear in each customer's chain;
- whether the chain terminates directly at MSG91 or includes UnifyDesk + MSG91 as separate TMs;
- evidence fields and renewal/suspension behavior.

This is an external production blocker, not something code can infer.

## 3. Provider template/Flow automation

### CONFIRMED

MSG91's current SMS API catalog exposes:
- Send SMS;
- Add Template;
- Add Template Version;
- Get Template Details/Versions;
- Mark Default;
- Logs and Analytics.

Source:
- https://docs.msg91.com/sms

MSG91 also documents Flow/template creation and DLT Template ID mapping in its panel/help flow.

### ARCHITECTURE DECISION

B1 implements provider provisioning behind a small adapter:
- ensure tenant/client account;
- ensure Header/PE mapping;
- ensure provider SMS template/Flow binding for exact DLT template version;
- poll/refresh binding status;
- never infer ACTIVE solely from local input.

### REAL-ACCOUNT TEST

The exact modern API request/response contract for creating an SMS template with all India DLT fields, and whether all Header/PE mappings are exposed through public APIs, must be exercised with the production-contract MSG91 account before enabling automated provisioning.

If a capability is dashboard/manual only, the provisioning operation goes to ACTION_REQUIRED/ADMIN_REVIEW instead of fabricating automation.

## 4. DLR/webhooks

### CONFIRMED

MSG91 Webhook (New) supports SMS event types including request received, report received, API failed, delivered, failed, rejected, template events and URL click events. DLR payloads can include requestId, CRQID, status, failure reason, credit, senderId and DLT Template Entity ID. Webhook retries can cause duplicate callbacks.

Sources:
- https://msg91.com/help/webhook-new/how-to-receive-sms-delivery-reports-via-webhook-new
- https://msg91.com/help/webhook-new

### UNRESOLVED / SECURITY BLOCKER

Public help currently documents configurable headers but does not establish a cryptographic webhook-signature scheme equivalent to Stripe/SNS signatures.

Therefore production trust is:
1. high-entropy per-provider-account webhook secret carried in a configured header if MSG91 supports it;
2. dedicated unguessable endpoint/token bound to one provider account;
3. TLS only;
4. strict payload/schema/account/correlation validation;
5. replay/idempotency store;
6. optional provider IP allowlist only if MSG91 publishes stable ranges;
7. no state mutation from an untrusted callback.

Before production, confirm with MSG91 whether Webhook (New) supports a signed/HMAC payload. If yes, signature verification becomes mandatory.

## 5. Correlation

MSG91 documents CRQID support and echoes it in DLRs. Old documentation limits CRQID to alphanumeric and 52 chars.

Source:
- https://msg91.com/help/webhooks/how-can-i-get-the-delivery-reports-on-my-webhook-url

Architecture uses a compact alphanumeric opaque correlation token mapped server-side to company/run/recipient/attempt. Do not put PII or raw internal composite data into CRQID.

## 6. Balance and rate limits

### CONFIRMED

MSG91 exposes balances by SMS type in its account UI/One API product. Webhook DLR can expose credit charged. General 429 guidance says rate limiting may apply and high-volume users should coordinate with an account manager.

Sources:
- https://msg91.com/help/text-sms/how-to-check-balance-in-msg91
- https://msg91.com/help/all-about-the-429-rate-limit-error

### ARCHITECTURE DECISION

Provider limits/balance are dynamic account facts, not hardcoded constants. UnifyDesk applies its own conservative global/tenant/campaign token buckets and wallet reserve floor, with provider 429/credit errors as backpressure.

### REAL-ACCOUNT TEST

Confirm:
- contracted throughput per master/child account;
- whether child wallet balance or master balance is authoritative for sends;
- whether a balance API exists for the selected account product;
- whether provider acceptance means credits are already consumed;
- refund/credit behavior for downstream rejected/failed DLRs.

## 7. What customer must supply vs what UnifyDesk can do

Customer action:
- register business as PE on a DLT portal;
- complete KYC;
- declare/approve the required PE–TM chain;
- request/obtain Header approvals;
- request/obtain DLT Content Template IDs;
- register consent template/consent where applicable;
- whitelist CTA values where applicable;
- resolve operator/DLT rejections requiring PE action.

UnifyDesk:
- collect PE ID and non-secret compliance evidence;
- validate company ownership/workflow state;
- provision or map MSG91 child/client account;
- map PE/Header in MSG91 where supported;
- create/sync MSG91 SMS template/Flow from an already DLT-approved exact template;
- track provider binding status;
- expose simple action-required states;
- send and reconcile DLRs;
- meter/bill;
- rate-limit and suspend abuse.

UnifyDesk must not claim it “approves” a DLT Header or Content Template. It records and verifies upstream approval state.

## 8. Findings that must be rechecked before GA

- final operational interpretation/effective dates of the 2026 Third Amendment;
- UnifyDesk's required TM classification/registration and exact PE–TM chain;
- contracted MSG91 master/subaccount structure and child credential semantics;
- complete modern SMS template provisioning API;
- webhook authentication/signature capability;
- balance/credit/refund semantics;
- provider throughput and per-child isolation guarantees.
