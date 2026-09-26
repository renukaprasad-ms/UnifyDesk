# Managed Email Security and Abuse Architecture

Status: Round-1 security requirements frozen  
Threat model date: 2026-09-26

Managed Email sends using UnifyDesk-owned provider infrastructure. A security mistake can affect customer data, platform reputation, provider standing and billing. These controls are release requirements.

## 1. Trust boundaries

Untrusted:
- browser/user request;
- path IDs and request-body IDs;
- domain/company display names;
- DNS state;
- inbound HTTPS event payload before SNS verification;
- recipient lists and template variables;
- customer-selected sender local part;
- provider error text rendered to users.

Trusted only after validation:
- authenticated current user;
- CompanyAuthorizationService result;
- rows loaded through company-scoped repository predicates;
- cryptographically verified SNS message from configured TopicArn;
- provider state fetched with platform IAM credentials;
- relational ownership graph.

Never trusted:
- frontend hiding/disabling a control;
- companyId/senderId/domainId supplied by a caller on its own;
- provider tags without a verified SNS envelope;
- a matching recipient correlation string without provider-message/ownership checks.

## 2. Tenant isolation invariant

Every customer operation follows:

currentUser
-> require company permission
-> load requested resource by resourceId AND companyId
-> validate parent resource company
-> perform mutation

Forbidden patterns:
- findById(domainId) then compare later;
- findById(senderId) without company predicate;
- accepting tenantId from request and looking it up globally;
- using SNS tag companyId as the sole company lookup;
- joining a sender/domain without verifying the same company across the graph.

Cross-company substitutions that must fail:

Company A request:
- Company B domainId;
- Company B senderId;
- Company B managed connectionId;
- Company B runId;
- Company B recipientId;
- Company B hosted domain;
- Company B usage resource.

Tests must prove no read and no write occurs.

## 3. Sender spoofing prevention

Managed sender creation accepts domainId + localPart, not arbitrary full From address.

Server:
1. company-scoped domain lookup;
2. require ACTIVE domain;
3. normalize/validate local part;
4. construct localPart@normalizedDomain;
5. persist sender binding to that domain.

At send:
- sender binding exists;
- binding company equals run company;
- domain company equals run company;
- domain ACTIVE;
- tenant ACTIVE;
- fromAddress domain equals bound domain.

A user who verified example.com cannot send as:
- ceo@amazon.com;
- offers@other-tenant.com;
- a subdomain not separately permitted by UnifyDesk policy.

Do not rely on SES deciding whether a From address is allowed.

## 4. DNS/domain claim threats

### Duplicate claim

Database partial uniqueness is final authority. A pre-check is only UX.

Two simultaneous companies claiming example.com:
only one live claim can commit.

### Stale verification

Verification is not assumed permanent.

Active domains are periodically refreshed from provider state. Loss of verification changes local domain to SUSPENDED_VERIFICATION and blocks new sends.

### Ownership transfer

Customer-owned domain:
- previous claim must be fully released;
- provider identity/association cleaned up;
- new customer performs fresh verification.

Hosted UnifyDesk domain:
never reassigned.

### DNS race

Never activate because a client says records were added. Activation is based on provider verification/DKIM state fetched by UnifyDesk.

### Delete/re-add

Deleting a customer domain does not make old sender rows silently usable. All bound senders are disabled/non-sendable first.

## 5. Hosted-subdomain abuse

Controls:
- normalized readable prefix;
- random suffix;
- permanent DB reservation;
- reserved names;
- profanity/abuse filter;
- rate limit hosted-domain creation;
- domain entitlement;
- audit creation/deletion;
- manual/admin block capability.

Block categories include system/security/email/protocol/platform/vendor names.

Do not automatically make legal trademark determinations. Known high-risk platform/vendor impersonation names may be reserved; other disputes are handled operationally.

## 6. AWS credential security

Managed Email customer APIs never accept or return AWS credentials.

Use workload identity / instance role / pod role.

Separate roles when deployment supports it:

Provisioning role:
- SES tenant lifecycle;
- configuration set/event destination lifecycle;
- identity lifecycle;
- tenant resource association;
- tenant suppression settings;
- read account/identity/tenant state;
- DNS write only for the UnifyDesk hosted zone if DNS is AWS-hosted.

Sending role:
- SES SendEmail for configured Managed Email region/resources;
- minimal read needed for send health.

The application must not use broad administrator credentials.

No AWS access key is stored in:
- broadcast_sender_connections.encrypted_credentials for MESSAGING_MANAGED;
- provisioning operation JSON;
- frontend state;
- logs.

Customer-managed provider credentials continue existing encrypted handling and are outside this managed trust model.

## 7. Provider event authentication

SNS endpoint is unauthenticated by user session but is NOT anonymous-trusted.

Before parsing SES event semantics:
- enforce POST/allowed SNS message types;
- strict request size;
- parse JSON with bounded depth;
- validate TopicArn against exact configured allowlist;
- validate SignatureVersion;
- validate SigningCertURL uses https;
- validate hostname is legitimate AWS SNS hostname for expected partition/region;
- prevent redirects to arbitrary hosts;
- fetch/cache certificate safely;
- verify canonical SNS message signature;
- validate timestamp within replay policy;
- dedupe SNS MessageId.

Only after signature passes is the nested SES event trusted enough for correlation.

Invalid signatures:
- return non-success appropriate to retry policy;
- log safe reason without event PII;
- increment security metric.

## 8. Event correlation safety

Signed provider event still does not authorize a cross-company mutation by itself.

Require:
- internal recipient/run/sender/domain/company tags;
- recipient exists;
- recipient.company_id == tag company;
- recipient.run_id == tag run;
- recipient.sender_id == tag sender;
- managed sender binding points to tag domain;
- domain/tenant company matches;
- stored provider_message_id equals SES mail.messageId where acceptance is already recorded.

For an UNKNOWN recipient whose provider message ID was not committed locally, use a separate controlled reconciliation path and enough signed correlation evidence before linking.

On any mismatch:
- persist REJECTED_SCOPE_MISMATCH security/event audit where safe;
- do not mutate recipient/suppression/usage;
- alert on repeated mismatches.

## 9. Replay and idempotency

Two uniqueness layers:

1. SNS MessageId;
2. normalized SES event key.

All state mutations are idempotent.

Repeated:
- bounce does not duplicate suppression;
- complaint does not duplicate suppression;
- SEND does not double bill;
- DELIVERY does not increment an impossible state count twice;
- open/click handling uses event identity appropriate to engagement event.

Out-of-order:
- late delivery never clears bounce/complaint;
- open/click can exist before delivery;
- SEND never regresses a later state.

## 10. Suppression security/compliance

Application suppression checked before send.

Never allow a user to bypass suppression by:
- using another sender under same company;
- re-creating a sender;
- renaming domain;
- retrying same Broadcast;
- switching managed connection.

Application suppression key remains company + channel + normalized recipient unless future compliance policy requires platform-global suppression.

Complaints:
- application COMPLAINT suppression;
- tenant provider suppression;
- abuse/reputation signal.

Hard bounce:
- application HARD_BOUNCE suppression;
- tenant provider suppression;
- abuse/reputation signal.

Unsubscribe:
- application suppression;
- no need to pretend it is a provider bounce.

## 11. Sending abuse controls

Release cannot allow a fresh tenant to consume the platform account quota.

Mandatory controls:
- MANAGED_EMAIL_ENABLED entitlement;
- global platform kill switch;
- tenant/company send status;
- domain status;
- sender status;
- monthly billing quota;
- account-region token bucket;
- tenant token bucket;
- hourly/day caps;
- campaign-size cap;
- NEW/RAMPING/ESTABLISHED stages;
- manual approval path for high volume;
- hard bounce and complaint monitoring;
- automatic restriction/suspension;
- admin kill switch;
- existing Broadcast pause/cancel.

Threshold values:
- configuration, not schema constants;
- reviewed by deliverability operations;
- stricter than waiting for provider enforcement;
- use minimum sample size and observation window.

## 12. Compromised-account containment

Signals/actions:
- sudden send velocity;
- rapid sender/domain creation;
- abnormal campaign-size increase;
- spike in bounce/complaint;
- repeated rejected/forbidden sender attempts;
- repeated domain claims;
- high-risk admin/manual signal.

Containment should be able to:
- stop new launches;
- stop new delivery claims;
- disable tenant sending;
- disable sender;
- disable domain;
- pause run;
- platform-wide stop managed sending.

Already in-flight SES request cannot be recalled.

## 13. Provisioning abuse / brute force

Rate limit:
- create customer domain;
- create hosted domain;
- verification refresh;
- sender creation;
- delete/recreate cycles.

Idempotency:
same legitimate user retry should not create more provider resources.

Do not reveal whether another company owns a domain beyond a safe "domain unavailable/already registered" response.

## 14. Enumeration

Customer APIs never list cross-company:
- domains;
- senders;
- usage;
- DNS records;
- deliverability;
- event records.

Prefer 404-style non-enumerating behavior for foreign IDs if consistent with existing API patterns.

Provider opaque identifiers never appear in customer URLs.

## 15. Billing abuse

Quota reservation is atomic under concurrency.

Attack:
many pods simultaneously attempt sends at final available quota.

Defense:
- one DB/Redis atomic reservation boundary;
- idempotent recipient reservation key;
- no "check then increment" race.

UNKNOWN reservations cannot be released merely because client requests retry.

Admin adjustments are append-only ledger events with actor/audit metadata.

## 16. Data/log privacy

Do not log:
- AWS credentials;
- raw SNS payload wholesale;
- recipient lists;
- message bodies;
- DKIM private material;
- full arbitrary headers;
- auth cookies/tokens.

Safe structured logging:
- opaque resource IDs;
- company ID;
- event type;
- provider error code/status class;
- counts;
- lifecycle state.

DNS CNAME values are not secrets but should still be company-scoped in APIs.

## 17. Provider error handling

Do not surface raw AWS exception bodies containing internal names.

Map to UnifyDesk-safe errors.

Internal logs may contain sanitized AWS request ID/error code, but not credentials or full customer data.

Ambiguous send errors remain UNKNOWN; do not automatically resend.

## 18. Company suspension / plan expiry

Authorization may allow a suspended workspace to be viewed under existing rules, but sending policy must fail closed.

If company:
- suspended;
- no active/trial entitlement;
- managed-email disabled;
- monthly limit reached;
then no new managed provider submission.

Read-only history can remain accessible according to existing company permissions.

## 19. Security test minimum

B1:
- foreign domain read/update/delete rejected;
- foreign sender create/delete rejected;
- duplicate live customer-domain claim race;
- hosted reservation collision;
- retired hosted name cannot be reused;
- sender local part cannot escape bound domain;
- deleted/suspended domain cannot create sender;
- provider credentials absent from DTO/log fixtures;
- idempotent provisioning under double click.

B2:
- forged SNS rejected;
- wrong TopicArn rejected;
- invalid cert URL rejected;
- replay duplicate safe;
- valid event with cross-company tags rejected;
- provider message ID mismatch rejected;
- hard bounce duplicate safe;
- complaint duplicate safe;
- out-of-order delivery/bounce safe;
- concurrent monthly quota cannot exceed limit;
- account limiter shared across companies/pods;
- tenant limiter isolates noisy tenant;
- global/company/sender kill switch denies send;
- UNKNOWN does not get blind retry or premature billing commit.

F1:
- does not accept manually typed arbitrary From domain;
- foreign IDs never assumed safe;
- provider internals not displayed;
- required vs recommended DNS labels are clear;
- suspended/error states do not offer send-ready UI.

## 20. QA/SEC Round after integration

QA/SEC receives an integrated branch only after B1+B2+F1 are reviewed.

Security review must include:
- endpoint authorization matrix;
- IDOR testing;
- SNS signature/replay tests;
- DNS ownership state transitions;
- AWS IAM review;
- provider resource cleanup/reclaim tests;
- abuse simulation;
- distributed rate limit/concurrency;
- usage/billing reconciliation;
- dependency/CVE scan;
- secret/log scan;
- destructive company/domain lifecycle tests.

The Architecture Lead owns remediation design and Round-2 task decomposition after the QA/SEC report.
