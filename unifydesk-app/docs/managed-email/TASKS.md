# Managed Email Round-1 Tasks and File Ownership

Status: FROZEN  
Architecture Lead: this chat  
Worker base SHA: published in the latest INTEGRATION_STATUS.md after this manifest is frozen  
Architecture branch parent dev SHA: 471dfe619c7e4ae58f4eaf58f78fed608ec4c553
Application-code baseline underneath the documentation-only PR #119: 8178d6831140f7bd8cd67d33e66e229c10c5f0b2

The worker base contains:
- all architecture/research/security/API/data-model documents except this TASKS commit;
- Lead-owned AWS SDK prerequisite bump to 2.54.17.

Workers must read this file from docs/managed-email-architecture-r1 even though this manifest commit is intentionally one commit after the frozen worker base.

## 0. Integration rules

Architecture branch:
docs/managed-email-architecture-r1

This architecture branch must be merged to dev before Round-1 worker PRs are merged.

All three implementation branches are created from EXACT worker base SHA:

SEE_LATEST_INTEGRATION_STATUS_AFTER_ARCHITECTURE_MERGE

Do not branch from a later moving dev tip.

Branches:

B1:
feature/managed-email-control-plane

B2:
feature/managed-email-delivery-events

F1:
feature/managed-email-ui

All three may start immediately and work in parallel.

Workers do not cherry-pick each other's commits.

Workers do not change API-CONTRACT.md, DATA-MODEL.md, DECISIONS.md, ARCHITECTURE.md or this file. Proposed deviations are reported to Architecture Lead.

After the three PRs are ready:
1. Architecture Lead reviews each independently.
2. Architecture Lead merges/rebases into one integration candidate.
3. Lead owns V58__managed_email_cross_module_constraints.sql and any tiny integration adapters.
4. Lead resolves migration number drift caused by unrelated dev work.
5. QA/SEC starts only after the integrated candidate exists.

## 1. Shared contracts

### Database contract

B1 produces these tables from V56:
- managed_email_tenants
- managed_email_domains
- managed_email_dns_records
- managed_email_sender_bindings
- managed_email_provisioning_operations

B2 is allowed to QUERY the above table/column contract using a B2-owned JdbcTemplate repository, but B2 may not map B1 JPA entities or edit B1 migration.

This is deliberate:
B2 compiles without B1 Java classes.

B2 V57 produces:
- managed_email_usage_periods
- managed_email_usage_events
- managed_email_provider_events
- managed_email_recipient_delivery
- managed_email_reputation_snapshots

V57 must reference only tables present in the shared base when declaring foreign keys. Cross-module FK constraints to B1 tables are deferred to Lead-owned V58 after integration.

### Runtime configuration contract

B1 owns adding the shared app.managed-email configuration block and .env.example entries.

Required keys include at least:

- APP_MANAGED_EMAIL_ENABLED
- APP_MANAGED_EMAIL_AWS_REGION
- APP_MANAGED_EMAIL_HOSTED_BASE_DOMAIN
- APP_MANAGED_EMAIL_SNS_TOPIC_ARN
- APP_MANAGED_EMAIL_CLOUDFLARE_ZONE_ID
- APP_MANAGED_EMAIL_CLOUDFLARE_API_TOKEN
- APP_MANAGED_EMAIL_PROVISIONING_POLL_INTERVAL
- APP_MANAGED_EMAIL_PROVISIONING_STALE_AFTER
- APP_MANAGED_EMAIL_VERIFICATION_REFRESH_INTERVAL
- APP_MANAGED_EMAIL_PLATFORM_SENDING_ENABLED
- APP_MANAGED_EMAIL_ACCOUNT_RATE_SAFETY_CAP
- APP_MANAGED_EMAIL_NEW_TENANT_HOURLY_CAP
- APP_MANAGED_EMAIL_NEW_TENANT_DAILY_CAP
- APP_MANAGED_EMAIL_EVENT_REPLAY_WINDOW

B2 binds its own configuration-properties class to app.managed-email and must compile even if optional runtime values are absent in unit tests. Sending must fail closed when required production configuration is absent.

### HTTP contract

F1 uses API-CONTRACT.md exactly.

F1 never imports/generated Java DTOs.

B1 and B2 expose independent controllers under the same base path:
- B1: control-plane endpoints.
- B2: usage and deliverability endpoints.

No backend worker waits for the frontend.

## 2. B1 — Backend Control Plane

Branch:
feature/managed-email-control-plane

Base:
SEE_LATEST_INTEGRATION_STATUS_AFTER_ARCHITECTURE_MERGE

### Mission

Implement the Managed Email control plane:
company tenant, SES resource provisioning, customer domain, hosted UnifyDesk domain, DNS lifecycle, managed sender creation, deprovisioning and control-plane APIs.

B1 does NOT implement delivery-path sending, provider-event processing, metering or runtime rate limiting.

### New directories/files B1 owns

All new production files under:

unifydesk-app/backend/src/main/java/com/messaging/managedemail/control/**

Suggested subpackages:
- config
- controller
- dto
- entity
- enums
- repository
- service
- provisioning
- aws
- dns
- scheduler

B1 owns tests under:

unifydesk-app/backend/src/test/java/com/messaging/managedemail/control/**

B1 owns migration:

unifydesk-app/backend/src/main/resources/db/migration/V56__managed_email_control_plane.sql

### Existing files B1 MAY modify

Only these existing files unless Architecture Lead explicitly expands ownership:

- unifydesk-app/backend/src/main/resources/application.yaml
- unifydesk-app/.env.example
- unifydesk-app/backend/src/main/java/com/messaging/broadcast/repository/BroadcastSenderConnectionRepository.java
- unifydesk-app/backend/src/main/java/com/messaging/broadcast/repository/BroadcastSenderRepository.java

The AWS SDK version in pom.xml was already upgraded by the Architecture Lead. B1 must not independently change pom.xml.

### B1 APIs produced

From API-CONTRACT.md:

- GET /api/v1/companies/{companyId}/managed-email/overview
- GET /api/v1/companies/{companyId}/managed-email/domains
- POST /api/v1/companies/{companyId}/managed-email/domains/customer
- POST /api/v1/companies/{companyId}/managed-email/domains/hosted
- GET /api/v1/companies/{companyId}/managed-email/domains/{domainId}
- POST /api/v1/companies/{companyId}/managed-email/domains/{domainId}/refresh
- DELETE /api/v1/companies/{companyId}/managed-email/domains/{domainId}
- GET /api/v1/companies/{companyId}/managed-email/senders
- POST /api/v1/companies/{companyId}/managed-email/senders
- DELETE /api/v1/companies/{companyId}/managed-email/senders/{senderId}

### B1 APIs consumed

No B2 HTTP API.

B1 may call:
- AWS SES v2;
- configured hosted DNS provider.

B1 does not call B2 Java services.

### B1 implementation requirements

#### Tenant

- one managed_email_tenant per company;
- deterministic opaque SES tenant name;
- create with tenant-level suppression scope TENANT and reasons BOUNCE + COMPLAINT;
- default SES reputation policy may remain Standard; architecture must expose provider sending/reputation status and allow later policy changes;
- one company configuration set;
- enable configuration-set reputation metrics where appropriate;
- configure SES event publishing to the shared platform SNS topic;
- associate configuration set with tenant;
- no customer credentials.

#### Domain

Customer domain:
- normalize IDNA/punycode;
- exact active claim uniqueness;
- Easy DKIM;
- persist returned required records;
- associate identity to tenant;
- poll verification;
- ACTIVE only on provider verification + DKIM success;
- DMARC assessment is advisory.

Hosted:
- readable slug + secure random suffix;
- permanent reservation;
- automatic SES identity;
- automatic DNS;
- explicit managed DMARC;
- no user DNS step.

#### Hosted DNS abstraction

Create a small ManagedDnsProvider interface.

Round-1 concrete adapter:
Cloudflare DNS for the configured UnifyDesk zone.

Use current Cloudflare v4 DNS Records API with API token, not Global API Key.

Token must be scoped to DNS Edit for the intended zone.

DKIM CNAME records must be DNS-only / not proxied.

Persist Cloudflare record IDs only as provider diagnostics needed for idempotent update/delete; do not expose them to customers.

If deployment is not yet on Cloudflare, the adapter remains disabled/fail-closed until zone credentials are configured; do not silently fake ACTIVE hosted domains.

#### Provisioning

- API transaction writes desired local state + durable operation then commits;
- no SES/Cloudflare network call inside the API DB transaction;
- worker uses bounded claim/lease;
- retry transient provider failures;
- deterministic get-or-create reconciliation after ambiguous provider outcome;
- double-click idempotency;
- cleanup operations retry;
- deletion makes resources non-sendable locally before provider cleanup.

#### Managed connection / sender

- create/reuse one BroadcastSenderConnection per company:
  provider AMAZON_SES;
  managementMode MESSAGING_MANAGED;
  authMode IAM_ROLE as platform-workload sentinel;
  encryptedCredentials NULL;
  roleArn NULL;
  region configured managed region.
- create matching generic ChannelConnection;
- multiple BroadcastSender rows may use the same managed connection;
- sender creation constructs From address server-side from localPart + ACTIVE domain;
- create SenderIdentity;
- create managed_email_sender_bindings;
- no per-address SES identity.

### B1 entitlement ownership

V56 establishes safe-default entitlement definitions:
- MANAGED_EMAIL_ENABLED
- MANAGED_EMAIL_DOMAINS
- MANAGED_EMAIL_SENDERS

Default must fail closed for existing plans unless a plan explicitly grants a value.

Do not invent commercial pricing.

### B1 required tests

At minimum:

- company authorization on every endpoint;
- foreign domain ID rejected;
- foreign sender ID rejected;
- concurrent duplicate customer-domain claims;
- idempotent same-company replay;
- domain parser: URL/path/email/public-suffix/IDN cases;
- Easy DKIM record persistence;
- AWS timeout retry;
- AWS success then local retry convergence;
- hosted slug normalization;
- duplicate display names;
- reserved/prohibited names;
- permanent hosted tombstone/no reuse;
- Cloudflare create/update/delete idempotency;
- Cloudflare CNAME not proxied;
- ACTIVE only after verification + DKIM;
- verification loss -> non-sendable status;
- multiple senders under one domain;
- sender cannot escape domain;
- no AWS credentials in DTOs;
- no customer-managed sender regression.

### B1 acceptance criteria

- clean Flyway migration from frozen base;
- backend compiles/tests on B1 branch without B2 code;
- own-domain onboarding can reach PENDING_DNS from mocked provider;
- hosted flow can reach ACTIVE from mocked SES + DNS;
- exact API contract implemented;
- managed sender appears in existing Broadcast sender list because it reuses existing sender tables;
- no existing Broadcast execution file changed;
- no B2-owned file changed.

### B1 explicitly FORBIDDEN files/scopes

Do not modify:

- unifydesk-app/backend/pom.xml
- BroadcastLaunchService.java
- BroadcastDeliveryService.java
- BroadcastRateLimiter.java
- BroadcastDeliveryStateService.java
- BroadcastSuppressionService.java
- BroadcastRecipientRepository.java
- BroadcastEmailCommand.java
- AmazonSesBroadcastEmailTransport.java
- AmazonSesClientManager.java
- B2 migration V57
- any file under com/messaging/managedemail/delivery
- any file under com/messaging/managedemail/events
- any file under com/messaging/managedemail/metering
- any file under com/messaging/managedemail/reputation
- frontend files
- managed-email architecture docs

If B1 discovers a required forbidden change, document it in the PR as an integration request; do not edit it.

## 3. B2 — Backend Delivery / Events / Metering

Branch:
feature/managed-email-delivery-events

Base:
SEE_LATEST_INTEGRATION_STATUS_AFTER_ARCHITECTURE_MERGE

### Mission

Implement Managed Email's runtime send plane:
managed SES send context, launch/send eligibility, account/company limits, usage reservation/metering, authenticated SES/SNS events, delivery projection, suppression feedback, reputation and runtime suspension.

B2 does NOT implement tenant/domain provisioning or customer domain CRUD.

### New directories/files B2 owns

All new production files under:

- unifydesk-app/backend/src/main/java/com/messaging/managedemail/delivery/**
- unifydesk-app/backend/src/main/java/com/messaging/managedemail/events/**
- unifydesk-app/backend/src/main/java/com/messaging/managedemail/metering/**
- unifydesk-app/backend/src/main/java/com/messaging/managedemail/reputation/**
- unifydesk-app/backend/src/main/java/com/messaging/managedemail/runtime/**

B2 tests:

- unifydesk-app/backend/src/test/java/com/messaging/managedemail/delivery/**
- unifydesk-app/backend/src/test/java/com/messaging/managedemail/events/**
- unifydesk-app/backend/src/test/java/com/messaging/managedemail/metering/**
- unifydesk-app/backend/src/test/java/com/messaging/managedemail/reputation/**

B2 migration:

unifydesk-app/backend/src/main/resources/db/migration/V57__managed_email_delivery_usage.sql

### Existing files B2 MAY modify

Only:

- unifydesk-app/backend/src/main/java/com/messaging/broadcast/service/BroadcastLaunchService.java
- unifydesk-app/backend/src/main/java/com/messaging/broadcast/service/BroadcastDeliveryService.java
- unifydesk-app/backend/src/main/java/com/messaging/broadcast/transport/BroadcastEmailCommand.java
- unifydesk-app/backend/src/main/java/com/messaging/broadcast/transport/AmazonSesBroadcastEmailTransport.java
- unifydesk-app/backend/src/main/java/com/messaging/broadcast/repository/BroadcastGroupMemberRepository.java

B2 may update existing backend tests under com/messaging/broadcast/** only where necessary to cover/compile these owned production changes.

B2 should prefer new Managed Email services over changing generic Broadcast behavior.

### B2 APIs produced

- GET /api/v1/companies/{companyId}/managed-email/usage
- GET /api/v1/companies/{companyId}/managed-email/deliverability

Internal provider endpoint:
- POST /api/v1/webhooks/managed-email/ses

The provider endpoint is not a company/user API and never accepts companyId as authorization input.

### B2 interfaces/contracts consumed

B2 consumes B1's DATABASE contract only, through a B2-owned JdbcTemplate runtime repository.

It must not import:
- B1 entities;
- B1 repositories;
- B1 services.

Expected runtime read projection:
- managed tenant status/company/region/provider tenant name/config set name;
- managed sender binding;
- managed domain state;
- managed connection management mode.

On B2 branch tests, mock this runtime repository. Do not duplicate B1 tables in V57.

### B2 managed SES send design

Keep one registered AMAZON_SES BroadcastEmailTransport.

Add a focused send-context resolver:

CUSTOMER_MANAGED:
- current AmazonSesClientManager behavior unchanged.

MESSAGING_MANAGED:
- platform/default workload credentials;
- configured Managed Email region;
- SES tenant name from runtime projection;
- company configuration set;
- internal correlation tags;
- no customer encrypted credentials.

Tenant-qualified SES SendEmail must set current SDK TenantName and configurationSetName.

Email tags include at least:
- ud_company_id
- ud_run_id
- ud_sender_id
- ud_domain_id
- ud_recipient_id or opaque recipient correlation

Extend BroadcastEmailCommand only as needed, retaining an overload/backward compatibility so existing provider tests/callers do not break unnecessarily.

### B2 launch guard

For MESSAGING_MANAGED only:

- validate managed runtime graph;
- tenant ACTIVE + sending enabled;
- domain ACTIVE;
- sender/binding/company/run relationship;
- platform kill switch;
- Managed Email entitlement;
- campaign-size/ramp policy;
- monthly quota ability.

CUSTOMER_MANAGED launch behavior must remain unchanged.

Use member count query in BroadcastGroupMemberRepository if needed for pre-launch campaign cap.

### B2 delivery guard

Immediately before provider submission, re-check enough mutable state to fail closed:
- platform sending;
- tenant/company;
- domain;
- sender;
- quota reservation;
- application suppression remains existing preparation/send behavior.

A sender/domain becoming suspended after launch must stop subsequent provider submissions.

### B2 distributed limiter

Do NOT repurpose the existing connection-only limiter for all providers.

Implement ManagedEmailRateLimiter with Redis Lua that atomically verifies/consumes required managed buckets:

- SES account + region;
- company/tenant;
- optional plan/tier;
- optional run/campaign.

Add safe hourly/day counters and new-tenant ramp limits.

All pods share keys.

No tenant can consume the full account bucket by default.

When one required bucket denies:
- no partial token consumption;
- recipient is released/retried according to existing non-failure rate-limit semantics.

### B2 metering

V57 owns entitlement definition:
MANAGED_EMAIL_MESSAGES_MONTHLY

Safe default must fail closed unless plan config explicitly enables useful volume.

Implement idempotent reservation ledger:

- RESERVE before provider attempt;
- COMMIT on SES acceptance;
- RELEASE on definite pre-acceptance failure;
- keep reservation on UNKNOWN;
- background reconciliation for stale reservations/accepted recipients/events;
- UTC monthly period resolver in Round 1;
- atomic quota check + reservation.

Do not double bill retry/duplicate event.

Provider bounce/complaint after accepted remains billable.

### B2 provider events

SES configuration-set event -> shared SNS HTTPS endpoint.

Before nested SES processing:
- validate allowed TopicArn;
- validate expected region/partition;
- validate SigningCertURL;
- verify SNS signature;
- validate timestamp/replay window;
- dedupe SNS MessageId;
- body/depth limits.

If auto-confirming SubscriptionConfirmation:
- first verify signature/topic;
- validate SubscribeURL HTTPS AWS SNS host;
- never follow arbitrary URL.

Normalize provider events into V57 ledger.

Supported:
- SEND
- DELIVERY
- BOUNCE
- COMPLAINT
- REJECT
- OPEN
- CLICK
- DELIVERY_DELAY
- SUBSCRIPTION
- rendering failure if received.

### B2 event correlation

Never mutate by raw company tag alone.

Require the signed event correlation set and validate against:
- recipient;
- run;
- sender;
- managed binding/domain;
- company;
- stored provider message ID where available.

Mismatch:
- record rejected-scope event safely;
- no recipient/suppression/usage mutation;
- metric/alert.

Out-of-order/duplicates must be safe.

### B2 suppression

Do not modify generic BroadcastSuppressionService unless Architecture Lead later asks.

Implement B2-owned ManagedEmailSuppressionFeedbackService using the existing broadcast_suppressions schema and same company+email semantics.

Hard bounce:
- upsert HARD_BOUNCE;
- transition core recipient to BOUNCED safely when appropriate;
- update stats once;
- tenant/provider feedback signal.

Complaint:
- upsert COMPLAINT;
- delivery projection complainedAt;
- reputation signal.

Manual unsubscribe/admin suppression stays existing product truth.

### B2 reputation/abuse runtime

- calculate bounded-window accepted/bounce/complaint snapshots;
- minimum sample threshold;
- configurable warning/throttle/suspend thresholds;
- consume provider tenant sending/reputation status when available;
- never wait for AWS account enforcement;
- auto-suspend local managed tenant/runtime policy when hard limit triggers;
- expose safe /deliverability aggregate;
- no other-tenant/account internals in response.

Exact percentages remain config, not migration constants.

### B2 required tests

- CUSTOMER_MANAGED SES behavior unchanged;
- managed SendEmail uses platform client, TenantName, configuration set;
- managed send has correlation tags;
- managed sender cannot use customer credential path;
- launch rejects inactive tenant/domain;
- state change after launch blocks delivery;
- account limiter shared across two companies;
- company limiter isolates noisy tenant;
- no partial token consumption on rejected hierarchy;
- concurrent monthly reservation cannot exceed limit;
- reserve/commit/release idempotency;
- UNKNOWN holds reservation;
- reconciliation commits/releases correctly;
- forged SNS rejected;
- wrong TopicArn rejected;
- bad/HTTP cert URL rejected;
- replay duplicate safe;
- out-of-order delivery/bounce safe;
- cross-company/mismatched IDs do not mutate;
- provider-message mismatch rejected;
- hard bounce suppression once;
- complaint suppression once;
- open/click counts safe;
- event payload does not leak body/header data;
- global kill switch denies managed sends.

### B2 acceptance criteria

- V57 migrates independently from frozen base;
- backend compiles/tests without B1 Java code;
- no B1 tables are created in V57;
- managed runtime repository is mockable and uses frozen SQL/table contract;
- existing customer-managed providers regressions pass;
- tenant-qualified SES request is present;
- billing is idempotent;
- event endpoint is cryptographically authenticated;
- no cross-company event mutation possible under tests;
- no B1-owned file changed.

### B2 explicitly FORBIDDEN files/scopes

Do not modify:

- unifydesk-app/backend/pom.xml
- unifydesk-app/backend/src/main/resources/application.yaml
- unifydesk-app/.env.example
- BroadcastSenderService.java
- BroadcastSenderPersistenceService.java
- BroadcastSenderConnectionRepository.java
- BroadcastSenderRepository.java
- AmazonSesClientManager.java
- B1 migration V56
- B1 control package
- B1 entity/repository/service files
- frontend files
- managed-email architecture docs

Do not create V58.

If B2 needs a change in a forbidden file, add an integration request to the PR rather than editing it.

## 4. F1 — Frontend

Branch:
feature/managed-email-ui

Base:
SEE_LATEST_INTEGRATION_STATUS_AFTER_ARCHITECTURE_MERGE

### Mission

Implement the complete non-technical Managed Email onboarding/management experience against API-CONTRACT.md without waiting for backend code.

### New files F1 owns

- unifydesk-app/frontend/src/features/managedEmail/**
- unifydesk-app/frontend/src/components/managed-email/**

F1 may add colocated frontend tests for those files.

### Existing files F1 MAY modify

- unifydesk-app/frontend/src/pages/company/CompanyConnections.tsx
- unifydesk-app/frontend/src/components/broadcast/BroadcastEmailProviderPickerModal.tsx
- unifydesk-app/frontend/src/features/broadcast/types.ts
- unifydesk-app/frontend/src/features/broadcast/broadcastApi.ts

Prefer a separate managedEmailApi.ts and managed-email types rather than making broadcastApi a giant feature client.

F1 may modify shared frontend CSS only if required by existing responsive patterns:
- unifydesk-app/frontend/src/index.css

No other existing file unless required to register a route that does not already fit CompanyConnections; document any additional route file in the PR.

### F1 API consumed

All endpoints in API-CONTRACT.md.

F1 does not consume AWS/Cloudflare APIs directly.

F1 never stores provider credentials.

### Required UX

Current "Broadcast Email" experience becomes:

Primary:
Use UnifyDesk Managed Email

Secondary:
Connect my own provider

The existing SES/SendGrid/Azure/MSG91 provider picker remains behind the secondary advanced path.

Managed flow:

Landing:
- simple explanation;
- "I have my own domain";
- "Use an UnifyDesk domain".

Own domain:
- enter bare domain;
- provisioning state;
- show required DNS records in copyable rows;
- clearly label required DKIM vs recommended DMARC;
- refresh/check verification;
- ACTIVE success;
- create sender.

Hosted:
- generate/reserve;
- show assigned domain;
- automatic setup progress;
- no DNS instructions;
- ACTIVE;
- create sender.

Sender:
- choose active managed domain;
- local-part input plus fixed @domain suffix;
- display name;
- reply-to;
- never arbitrary From-domain text field.

Management:
- list domains with status;
- list senders;
- show suspension/error/remediation;
- delete/retire with confirmation;
- usage summary via B2 /usage;
- deliverability status via B2 /deliverability.

Existing Broadcast composer:
managed sender should look like "UnifyDesk Managed Email" when managementMode=MESSAGING_MANAGED while retaining the existing sender ID.

### F1 provider abstraction rule

Do not display to Managed Email customers:
- Amazon SES;
- SES tenant;
- AWS region;
- configuration set;
- AWS account quota;
- Cloudflare;
- AWS credentials.

Allowed:
DNS values that naturally point to provider-hosted names because the customer must publish them.

### F1 state handling

Must render:
- PROVISIONING;
- PENDING_DNS;
- VERIFYING;
- ACTIVE;
- SUSPENDED_VERIFICATION;
- SUSPENDED_POLICY;
- ERROR;
- DELETING/RETIRED where returned.

Polling:
- only while non-terminal setup detail is open;
- starts around 3–5s;
- backs off;
- stops on unmount/ACTIVE/terminal;
- manual refresh separate.

### F1 independence requirement

Implement API client/types from the frozen contract now.

Use mocks/fixtures/MSW-style test mocks if the repo already has a preferred mechanism.

Do not wait for B1/B2 endpoint implementation.

Do not invent alternate fields because a backend branch is unavailable.

### F1 required tests

- managed path is primary;
- own-provider path still accessible;
- own-domain create states;
- DNS copy UI;
- required vs recommended labels;
- hosted-domain flow has no DNS task;
- polling stops correctly;
- sender From domain cannot be edited;
- ACTIVE required to create sender;
- suspension disables ready-to-send actions;
- provider internals absent;
- responsive/mobile behavior;
- existing provider connection flows remain usable.

### F1 acceptance criteria

- frontend builds/tests without backend branches;
- complete flow works against mocked frozen API;
- no backend files changed;
- no provider credentials requested;
- no second Broadcast composer/workflow created;
- current Company Connections visual patterns are reused;
- own provider flow still works.

### F1 explicitly FORBIDDEN files/scopes

Do not modify:
- any backend source;
- any Flyway migration;
- pom.xml;
- architecture docs;
- Kubernetes/deployment files;
- provider credentials/server configuration.

## 5. Architecture Lead — integration ownership after Round 1

The Lead, not B1 or B2, owns cross-worker reconciliation.

Lead tasks:

- review both backend PRs for contract compliance;
- review F1 against API contract;
- create V58__managed_email_cross_module_constraints.sql after B1+B2 are together;
- add cross-module FKs/indexes;
- resolve application config binding mismatches;
- resolve any tiny adapter required between B1 database projection and B2 runtime repository;
- run/check merged migration order;
- ensure architecture docs match actual implementation;
- ensure no one added a second Broadcast engine;
- ensure provider internals are absent from customer UI/API;
- produce integrated QA/SEC candidate;
- hand QA-SEC-PLAN.md to the QA/SEC chat.

## 6. Merge-conflict policy

If two workers discover they both need one file:

1. Neither worker adds an unreviewed duplicate edit.
2. The worker who owns the file implements the change.
3. The other worker codes to the frozen contract/mocks.
4. If no owner exists, Architecture Lead assigns one or makes an integration patch.

No worker may solve a dependency by cherry-picking the other worker's whole branch.

## 7. Definition of Round-1 complete

Round 1 is complete only when:

- B1 PR meets control-plane acceptance criteria;
- B2 PR meets delivery/events/metering criteria;
- F1 PR meets UX/API criteria;
- Architecture Lead review passes;
- integrated schema includes Lead V58;
- existing customer-managed Broadcast still works;
- Managed Email uses the existing run/outbox/Kafka/batch/recipient engine;
- QA/SEC candidate is created.

Only then does QA/SEC execute the review plan.

## 8. Round-2 rule

QA/SEC does not directly redesign architecture.

It reports findings.

Architecture Lead:
- updates ADRs/contracts if necessary;
- decides which findings are B1/B2/F1/Lead-owned;
- creates explicit Round-2 tasks and branches;
- avoids overlapping fixes;
- performs final architecture review before release.
