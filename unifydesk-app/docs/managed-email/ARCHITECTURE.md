# Managed Email Architecture

Status: Round-1 architecture frozen  
Baseline: dev @ 8178d6831140f7bd8cd67d33e66e229c10c5f0b2  
MVP provider: internal Amazon SES  
Customer-facing provider name: UnifyDesk Managed Email

## 1. Scope

Managed Email is a control plane, policy layer, and managed delivery mode around the existing Broadcast engine.

It does not replace:

- BroadcastMessage
- BroadcastRun
- BroadcastOutbox
- Kafka preparation
- BroadcastBatch
- BroadcastRecipient
- BroadcastDeliveryAttempt
- BroadcastStats
- Broadcast retry/recovery
- BroadcastEmailTransportRegistry

The managed feature adds:

- company-scoped SES tenant provisioning;
- domain and hosted-subdomain onboarding;
- DNS/DKIM verification lifecycle;
- Managed Email sender creation;
- managed SES credentials/tenant/configuration-set context;
- account + company + plan/campaign rate controls;
- SES event ingestion and reconciliation;
- application/provider suppression coordination;
- usage reservation/metering;
- reputation and anti-abuse controls;
- admin/runtime suspension controls.

## 2. Existing integration boundary

The existing model is retained:

BroadcastSenderConnection
- provider = AMAZON_SES
- managementMode = MESSAGING_MANAGED
- region = platform Managed Email region
- encryptedCredentials = null
- no customer role ARN / access key
- one managed connection per company

BroadcastSender
- provider = AMAZON_SES
- one row per customer-visible From address
- associated with the company's managed connection
- associated through managed_email_sender_bindings with exactly one ACTIVE managed domain

The same company can still have CUSTOMER_MANAGED SES/SendGrid/Azure/MSG91 senders in parallel.

## 3. Primary topology

One region for MVP:

UnifyDesk backend
  |
  +-- Managed Email control plane
  |     |
  |     +-- SES Tenant (1/company)
  |     +-- SES Configuration Set (1/company)
  |     +-- SES domain identities
  |     +-- hosted DNS automation
  |
  +-- existing Broadcast engine
        |
        +-- managed-send credential strategy
        +-- SES SendEmail with tenant context
        +-- company configuration set
        +-- internal correlation tags
              |
              v
            Amazon SES
              |
              +-- provider acceptance
              +-- delivery/bounce/complaint/open/click/delay events
                      |
                      v
                SNS HTTPS event channel
                      |
                      v
             Managed Email event reconciler
                      |
                      +-- recipient delivery projection
                      +-- application suppression
                      +-- tenant suppression
                      +-- usage reconciliation
                      +-- reputation/abuse signals

SES and AWS names are internal. Customer-facing responses use UnifyDesk terminology.

## 4. SES tenant model

MVP maps one UnifyDesk Company to one SES tenant.

Provider resource names are opaque/stable and never derived from company display names. Example internal naming convention:

- tenant: ud-c-<opaque-stable-id>
- configuration set: ud-c-<opaque-stable-id>

The provider identifier is stored server-side only.

Tenant lifecycle:

PROVISIONING
-> ACTIVE
-> SUSPENDED
-> ACTIVE

PROVISIONING/ACTIVE/SUSPENDED
-> DELETING
-> DELETED

ERROR is a recoverable operational state, not a terminal ownership state.

A tenant is not sendable until:

- tenant exists;
- company configuration set exists;
- required tenant resource associations exist;
- managed connection is active;
- platform sending is enabled;
- company policy is not suspended.

Company rename does not rename or replace the tenant.

## 5. Configuration-set model

One configuration set per company/tenant.

Reasons:

- SES tenant sending requires resource associations;
- deterministic company event boundary;
- per-company operational visibility;
- future sending-pool/dedicated-IP assignment;
- future company-specific reputation options;
- simpler deprovisioning and diagnostics.

All Managed Email sends specify that configuration set.

The configuration set sends these event types to the platform SES event destination:

- SEND
- DELIVERY
- BOUNCE
- COMPLAINT
- REJECT
- OPEN
- CLICK
- DELIVERY_DELAY
- SUBSCRIPTION
- RENDERING_FAILURE where supported by current SES API

The destination is the shared platform event channel; one company does not get its own public webhook.

## 6. Customer domain flow

Customer requests example.com.

Backend:

1. normalize the domain using IDNA ASCII form;
2. reject public suffixes, URLs, paths, email addresses, wildcard labels and invalid names;
3. enforce global active-claim uniqueness;
4. create ManagedEmailDomain in PROVISIONING;
5. enqueue a durable provisioning operation and commit;
6. provisioning worker ensures company tenant/config set;
7. create an SES domain identity using Easy DKIM;
8. associate identity with the company SES tenant;
9. persist the exact DKIM DNS requirements returned by SES;
10. transition domain to PENDING_DNS;
11. return DNS records without exposing SES tenant/config-set details;
12. verification worker periodically checks SES identity/DKIM state;
13. when verification and DKIM are successful, mark domain ACTIVE.

MVP required customer DNS records:

- the Easy DKIM CNAME records returned by SES.

MVP does not require a separate SPF TXT record on the customer's visible From domain because SES's default MAIL FROM provides SPF authentication. Custom MAIL FROM is deferred.

DMARC is checked/recommended, not required for own-domain activation. The API returns DMARC assessment/warnings separately from required records.

A verified SES domain permits multiple From addresses under that exact domain. UnifyDesk still validates every sender locally and never trusts SES inheritance as authorization.

## 7. Hosted UnifyDesk domain flow

Customer asks for a managed domain.

The system reserves:

<generated-name>.unifydesk.in

Example display company:
ABC Technologies Pvt Ltd

Possible assigned domain:
abc-technologies-k7m4q2.unifydesk.in

The suffix is an opaque random reservation suffix, not the raw company ID.

Hosted subdomain provisioning:

1. normalize company display name to a candidate slug;
2. apply reserved/prohibited-name rules;
3. append cryptographically random short suffix;
4. insert immutable reservation under DB uniqueness;
5. create SES identity for the exact hosted subdomain;
6. associate identity to the company tenant;
7. publish SES DKIM CNAME records automatically into UnifyDesk DNS;
8. publish explicit DMARC policy for that hosted subdomain;
9. poll SES until identity and DKIM become active;
10. mark domain ACTIVE.

The customer performs no DNS work.

Hosted reservation rules:

- reservation ownership is company-scoped;
- company rename never changes an existing hosted domain;
- duplicate company names receive different suffixes;
- retired hosted names are never reassigned;
- a replacement domain is a new reservation;
- old DNS and SES resources may be removed, but the name tombstone remains;
- platform/vendor/security/protocol/profanity names are blocked;
- trademark disputes are handled by operational policy/manual review, not automatic trademark inference.

## 8. Hosted slug algorithm

Input is the current company display name only as a human-readable prefix.

Algorithm:

1. Unicode NFKD normalization;
2. transliterate/remove combining marks where safe;
3. lowercase;
4. replace runs outside [a-z0-9] with hyphen;
5. collapse duplicate hyphens;
6. trim leading/trailing hyphens;
7. if empty, use "company";
8. cap readable prefix length so final DNS label remains <= 63 octets;
9. reject/replace reserved prefix;
10. append "-" + secure random lowercase base32 suffix;
11. atomically reserve;
12. on the extremely unlikely collision, generate a new suffix.

Example reserved categories:

- DNS/web: www, api, app, cdn, static, assets, dns, ns, mx
- email: mail, email, smtp, imap, pop, dmarc, bounce, feedback, postmaster
- security/system: admin, administrator, root, security, abuse, auth, oauth, login
- environment: dev, test, staging, prod, production
- product/platform: unifydesk, ses, aws, amazon
- operational: support, billing, status, help, verify, track, click, open, unsubscribe

The definitive list is code-owned/configurable and must be tested.

## 9. Managed sender flow

Sender creation is local after the domain is ACTIVE.

Request:
- domainId
- localPart
- optional displayName
- optional replyToAddress

Validation:

- caller has BROADCAST_MODIFY;
- domain lookup is by id AND companyId;
- domain state is ACTIVE;
- domain belongs to the company's ACTIVE managed tenant;
- localPart passes the existing email normalization rules;
- resulting address uses the exact managed domain;
- duplicate company/provider/from-address is rejected;
- sender count entitlement is enforced;
- abusive/reserved local parts may be policy-restricted;
- no caller-provided full arbitrary From domain is accepted.

Creation:

- reuse/create exactly one MESSAGING_MANAGED AMAZON_SES BroadcastSenderConnection for the company;
- create BroadcastSender;
- create SenderIdentity on the existing ChannelConnection;
- create managed_email_sender_bindings(sender_id, domain_id, company_id).

No SES email-address identity is created because the domain identity already authorizes addresses beneath it.

## 10. Broadcast launch eligibility

Before a MESSAGING_MANAGED run is launched, the backend must verify:

- company workspace is available;
- Managed Email entitlement is enabled;
- BroadcastSender company matches the requested company;
- BroadcastSender status is ACTIVE;
- connection company matches;
- connection mode is MESSAGING_MANAGED;
- managed sender binding matches same company/sender;
- managed domain is ACTIVE;
- managed tenant is ACTIVE;
- platform global kill switch is enabled;
- company sending is not suspended;
- sender/domain are not suspended;
- billing quota can reserve requested recipients;
- campaign-size policy allows the launch.

The frontend is never an authorization boundary.

A domain that later loses verification prevents new launches and new provider submissions. Already in-flight provider requests cannot be recalled.

## 11. Managed sending credential strategy

CUSTOMER_MANAGED SES continues to use the current AmazonSesClientManager credential behavior.

MESSAGING_MANAGED SES uses platform workload credentials only:

- no encrypted customer credentials;
- no access key received from the user;
- no role ARN supplied by the customer;
- one configured Managed Email AWS region in MVP.

AmazonSesBroadcastEmailTransport should delegate credential/context selection rather than grow a large conditional.

Conceptually:

SesSendContextResolver
  CUSTOMER_MANAGED -> current per-connection client, current behavior
  MESSAGING_MANAGED -> platform client + tenant/config-set context

Only one AMAZON_SES transport remains registered.

## 12. Correlation contract

Every managed SES send carries internal SES message tags:

- ud_recipient_id
- ud_run_id
- ud_sender_id
- ud_domain_id
- ud_company_id

The accepted SES message ID is still persisted on BroadcastRecipient.

Event reconciliation requires both:

1. internal correlation identifiers; and
2. consistency with the stored provider message ID and ownership graph.

No event may mutate a recipient merely because one tag or correlation value matches.

## 13. Event architecture

MVP uses SES configuration-set event publishing to a platform-owned SNS topic with one HTTPS consumer endpoint.

Why:

- supported directly by SES configuration sets;
- no per-company public callback;
- does not require a new queue client dependency in Round 1;
- SNS provides delivery retries;
- backend can authenticate SNS envelopes cryptographically;
- duplicate and out-of-order handling remains application-controlled.

Webhook trust requirements:

- HTTPS only;
- expected TopicArn allowlist;
- expected AWS region;
- verify SNS SignatureVersion and signature;
- validate SigningCertURL host/scheme;
- validate timestamp/replay window;
- idempotency on SNS MessageId and normalized provider-event key;
- limit body size and parse depth;
- never accept companyId from URL/request parameters;
- derive ownership from stored managed resources and signed event content;
- subscription confirmation, if supported automatically, is allowed only after the same signature/topic/host validation.

Future hardening may replace HTTPS SNS delivery with durable queue fanout without changing the normalized event contract.

## 14. Event state handling

Provider event ledger is immutable/idempotent.

A separate recipient delivery projection records:

- deliveredAt
- complainedAt
- firstOpenAt
- lastOpenAt
- openCount
- firstClickAt
- lastClickAt
- clickCount
- lastDelayAt
- lastEventAt

Core BroadcastRecipient state is not expanded for analytics in Round 1.

Rules:

- SEND confirms provider acceptance but never regresses another state.
- DELIVERY updates delivery projection; core ACCEPTED_BY_PROVIDER remains valid.
- BOUNCE hard/permanent may transition an accepted recipient to BOUNCED and creates application suppression.
- COMPLAINT creates complaint suppression and tenant protection signal; it does not rewrite unrelated historical states.
- REJECT is recorded and reconciled only where ownership/message correlation is exact.
- OPEN/CLICK update analytics projection even if DELIVERY arrived later/earlier.
- DELIVERY_DELAY is informational/reputation signal unless later terminal event arrives.
- duplicates do nothing after first normalized event.
- late success cannot clear a hard bounce/complaint suppression.

## 15. Suppression model

Three layers exist for different reasons.

### UnifyDesk application suppression

Authoritative product-level send eligibility:

- hard bounce
- complaint
- unsubscribe/user opt-out
- admin block
- invalid address
- future compliance suppression

Checked before provider submission.

### SES tenant suppression

Provider-level containment for:
- BOUNCE
- COMPLAINT

Scope is tenant-specific for Managed Email.

It is defense-in-depth, not the sole product truth.

### SES account/global provider behavior

Account/provider suppression still exists and may reject addresses. It is not used as the only tenant-visible suppression model.

Unsubscribe remains an application/compliance decision and is not modeled as a provider bounce.

## 16. Rate limiting and quotas

The existing connection-scoped BroadcastRateLimiter is not sufficient for Managed Email because all managed companies share the same SES account/region quota.

Managed Email adds a hierarchical distributed limiter.

All required buckets are evaluated atomically before sending:

1. SES account/region bucket;
2. company/tenant bucket;
3. plan/tier bucket if configured;
4. campaign/run bucket if configured.

Additional rolling/fixed counters enforce:

- per-hour company cap;
- per-day company cap;
- new-tenant ramp cap;
- campaign-size cap.

The account rate is bounded by:
min(current SES account max send rate, platform safety ceiling)

Company rates are always <= account rate.

All backend pods share the same Redis keys/scripts.

A new company does not inherit the entire account quota.

## 17. Billing and usage

Customer billing is based on provider acceptance, not final inbox delivery.

A recipient is billable when SES accepts the SendEmail request for Managed Email.

Not billable:
- suppressed before send;
- quota-blocked;
- validation failure;
- definite provider rejection before acceptance;
- cancelled before provider submission.

Still billable after acceptance:
- later bounce;
- later complaint;
- later unsubscribe;
- later delivery failure outside provider acceptance boundary.

UNKNOWN:
- reserve usage until reconciled;
- do not finalize billable usage until provider acceptance is proven.

Usage architecture:

- reserve one unit before a provider attempt after application suppression/policy checks;
- COMMIT reservation when acceptance is confirmed;
- RELEASE on definite pre-acceptance failure;
- keep reservation on UNKNOWN;
- reconcile UNKNOWN through provider events / background reconciliation;
- append idempotent usage ledger events;
- maintain monthly materialized company counters.

Unique usage key is recipient/provider-attempt based so duplicate callbacks/retries cannot double bill.

Existing plan architecture supplies entitlement values such as:
- MANAGED_EMAIL_ENABLED
- MANAGED_EMAIL_DOMAINS
- MANAGED_EMAIL_SENDERS
- MANAGED_EMAIL_MESSAGES_MONTHLY

Commercial prices/overage rates remain outside this architecture decision.

## 18. Anti-abuse and reputation protection

Managed Email must default deny high-risk volume.

Tenant/account safety controls include:

- managed-email entitlement gate;
- platform global kill switch;
- company/tenant status gate;
- domain status gate;
- sender status gate;
- low new-tenant hourly/daily limits;
- maximum campaign size for untrusted tenants;
- graduated ramping;
- configurable manual approval threshold;
- bounce-rate observation windows;
- complaint-rate observation windows;
- automatic tenant suspension at configured hard thresholds;
- suspicious sending velocity detection;
- admin suspension/resume;
- existing Broadcast pause/cancel for run-level intervention;
- audit trail for policy state changes.

Exact production percentage thresholds are operational configuration and must be set below provider enforcement limits after deliverability review. Threshold logic must use a minimum sample size so one bounce on a tiny send does not produce a meaningless percentage suspension.

Purchased-list/spam/phishing policy is a product/compliance control, but the technical platform must have a fast kill path for a compromised or malicious tenant.

## 19. Dedicated IP strategy

MVP uses SES shared sending infrastructure.

Do not allocate dedicated IPs for every tenant.

Architecture leaves a future extension:
company configuration set -> dedicated/managed sending pool

Possible premium/high-volume tiers can use SES standard or managed dedicated IPs after volume/reputation justify them.

## 20. Region strategy

MVP uses one configured SES region.

Reasons:

- SES identities are regional;
- quotas are regional;
- event resources are regional;
- tenant/config-set resource management is simpler;
- no need to duplicate DKIM/identity lifecycle for MVP;
- operational failure modes stay understandable.

Region is configured by platform deployment and persisted on ManagedEmailTenant/connection.

Future multi-region requires:
- identity replication/provisioning in target region;
- tenant/config-set duplication;
- independent quotas;
- independent event path;
- deterministic routing/failover;
- DNS/DKIM strategy such as current SES multi-region DKIM facilities.

No transparent cross-region failover is attempted in MVP.

## 21. Custom MAIL FROM

Deferred from MVP.

Default SES MAIL FROM already provides SPF authentication. Easy DKIM supplies aligned authentication for DMARC.

Custom MAIL FROM would add:
- additional DNS MX/TXT;
- fallback behavior choices;
- more onboarding failure states;
- more lifecycle work.

A future version can add a per-domain MAIL FROM subdomain when deliverability need justifies it.

## 22. DMARC

Own domain:
- not a hard activation requirement;
- backend assesses existing DMARC best-effort;
- UI shows absent/weak-policy warning;
- UnifyDesk never overwrites customer DMARC.

Hosted UnifyDesk subdomain:
- UnifyDesk publishes an explicit managed DMARC record;
- policy is platform-controlled and versionable;
- DKIM alignment is the primary DMARC authentication path in MVP.

## 23. Reliable provisioning workflow

No long transaction includes an AWS/DNS request.

Pattern:

API transaction:
- validate authorization/ownership;
- create/update desired local resource state;
- create unique ManagedEmailProvisioningOperation;
- commit;
- return current state.

Worker:
- claim operation with bounded batch and SKIP LOCKED/lease;
- perform one or more idempotent provider steps;
- persist checkpoint/state in short DB transaction;
- retry transient errors with capped exponential backoff;
- mark terminal policy/validation failures distinctly.

Provider names are deterministic from stored opaque IDs so retry after "AWS success + DB failure" can converge by get-or-create/get-and-associate.

Double click/concurrent request:
- idempotency key + DB uniqueness returns/continues same desired resource.

Delete:
- mark non-sendable first;
- enqueue provider cleanup;
- never delete historical Broadcast rows needed for audit;
- hosted reservation tombstone remains forever.

## 24. Domain loss / lifecycle behavior

Verification worker periodically rechecks active identities.

If a previously active identity is no longer sendable:

ACTIVE -> SUSPENDED_VERIFICATION

Effects:
- new sender creation denied;
- new Broadcast launch denied;
- worker denies provider submissions not already in flight;
- customer sees remediation;
- refresh/poll may restore ACTIVE after provider verification succeeds.

Domain deletion:
- disables bound senders;
- blocks launches immediately;
- asynchronously removes provider identity/resource association;
- retains historical sender/run references.

Company suspension:
- Managed Email sends stop regardless of domain state.

Plan expiration:
- Managed Email launch and send gates fail closed according to entitlement policy.

## 25. Observability

Metrics/logs must avoid message bodies, credentials and recipient lists.

Required dimensions:
- region
- managed tenant opaque ID
- company ID where allowed in internal metrics
- operation type
- result class
- event type
- suspension reason
- provider status class

Metrics:
- provisioning latency/failures/retries
- domain verification age
- sends accepted/rejected/unknown
- event ingestion lag
- event duplicate count
- unmatched event count
- hard bounce/complaint rate
- account rate utilization
- tenant throttling
- usage reservation age
- unknown usage reconciliation backlog

Alerts:
- SES account sending disabled;
- global bounce/complaint health degradation;
- SNS callback failures;
- unmatched/cross-scope event attempts;
- provisioning retry exhaustion;
- usage reservation backlog;
- account quota saturation.

## 26. Round-1 integration boundary

B1 owns control-plane truth:
- tenant/domain/DNS/provisioning/sender creation and lifecycle API.

B2 owns send-plane truth:
- managed send context;
- launch/send eligibility integration;
- hierarchical limits;
- event reconciliation;
- suppression feedback;
- usage/metering;
- abuse/reputation runtime controls.

F1 consumes only API-CONTRACT.md:
- no dependency on backend implementation completion.

Shared hot files are assigned to one backend worker in TASKS.md. Workers must not independently "help" by editing files owned by another worker.
