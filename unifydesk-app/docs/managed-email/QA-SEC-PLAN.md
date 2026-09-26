# Managed Email QA / Security Review Plan

Status: Defined now, executed only after Round-1 integration.

QA/SEC must not begin implementation testing against only B1, B2, or F1 in isolation. The Architecture Lead first reviews and integrates all three branches into a single candidate.

## Entry criteria

- B1 acceptance criteria pass.
- B2 acceptance criteria pass.
- F1 acceptance criteria pass.
- architecture/API deviations are documented.
- Flyway migrations apply from a clean database and from the current dev schema.
- backend compiles/tests.
- frontend builds/tests.
- no unresolved ownership conflicts.
- architecture lead integration migration/patches are applied.

## QA workstreams

### Functional onboarding

Customer-owned domain:
- create;
- DNS records appear;
- incorrect/missing DNS remains pending;
- valid records activate;
- multiple senders can be created;
- delete disables senders.

Hosted domain:
- safe unique name generated;
- no customer DNS work;
- provider provisioning reaches ACTIVE;
- duplicate company display names remain unique;
- rename leaves domain unchanged;
- retired name cannot be reused.

### Broadcast integration

- managed sender appears in existing sender selector;
- CUSTOMER_MANAGED providers still work;
- managed sender launch uses existing run/outbox/Kafka/batch/recipient flow;
- no second execution engine exists;
- scheduled send revalidates managed eligibility;
- pause/resume/cancel behavior remains compatible.

### Events

Exercise:
- SEND
- DELIVERY
- BOUNCE
- COMPLAINT
- REJECT
- OPEN
- CLICK
- DELIVERY_DELAY
- duplicate
- out-of-order
- malformed
- unmatched.

Verify state projection, suppression, metrics, and no cross-company mutation.

### Metering

Verify:
- reserve;
- commit on acceptance;
- release on definite failure;
- UNKNOWN reservation retained;
- reconciliation;
- duplicate acceptance/event;
- period rollover;
- unlimited entitlement;
- hard limit under concurrency;
- admin adjustment if implemented.

### Rate limits / abuse

Simulate:
- many backend workers;
- noisy tenant;
- new tenant ramp;
- account ceiling;
- tenant ceiling;
- hourly/day ceiling;
- campaign ceiling;
- global kill switch;
- company/domain/sender suspension.

Prove Company A cannot consume Company B's allowed tenant rate and aggregate sends cannot exceed platform account policy.

### Lifecycle / recovery

Inject:
- AWS timeout;
- DB failure after AWS success;
- AWS failure after DB commit;
- app crash during provisioning;
- duplicate operation;
- stale worker lease;
- provider verification loss;
- cleanup failure;
- company suspension;
- plan expiration.

Verify convergence and fail-closed sending.

## Security review

### IDOR / tenant isolation

Systematically substitute Company B IDs into Company A requests for:
- tenant-equivalent resources;
- domains;
- DNS records;
- senders;
- usage;
- deliverability;
- runs;
- recipients.

Expected:
no data leak, no mutation.

### Event trust

- forged SNS body;
- altered signed body;
- wrong TopicArn;
- unexpected region;
- HTTP SigningCertURL;
- non-AWS cert hostname;
- redirect;
- expired timestamp;
- duplicate MessageId;
- valid event carrying mismatched internal IDs.

### Sender spoofing

Attempt sender:
- under other customer domain;
- under parent/subdomain not explicitly ACTIVE;
- with malformed local part;
- IDN confusion case;
- after domain suspension;
- after sender disable.

### DNS security

- race two companies for same customer domain;
- verify then change DNS;
- delete then another company attempts claim;
- hosted reservation reuse;
- reserved/profane/platform impersonation slug.

### AWS IAM

Review effective role policies:
- provisioning role minimum;
- sending role minimum;
- no long-lived managed credentials;
- no customer access;
- no broad admin role;
- hosted DNS write constrained to intended zone.

### Secrets / logging

Search:
- AWS keys;
- SNS raw payload logging;
- recipient/body logging;
- tenant/provider internal names in customer API;
- credential serialization.

### Dependency review

- Maven dependency/CVE scan;
- npm dependency/CVE scan;
- AWS SDK compatibility;
- signature/certificate implementation review.

## Load / resilience targets

Use realistic staged volumes to validate:
- Redis limiter correctness;
- database contention;
- event duplicate handling;
- usage row locking;
- provisioning polling cadence;
- no unbounded in-memory recipient/event accumulation.

Exact throughput targets are set from production SES quota and deployment sizing before QA execution.

## Deliverability review

Validate:
- DKIM success on own domain;
- DKIM success on hosted domain;
- DMARC alignment;
- no erroneous SPF instruction;
- message From header correctness;
- bounce/complaint processing;
- unsubscribe behavior from existing Broadcast/compliance layer;
- account/tenant health dashboards.

## Exit criteria

QA/SEC produces:
- PASS findings;
- defects with severity;
- security findings;
- provider/deliverability findings;
- migration/recovery findings;
- performance findings;
- recommended Round-2 changes.

The Architecture Lead then:
1. classifies architecture vs implementation defects;
2. updates DECISIONS/ARCHITECTURE/API when required;
3. creates Round-2 tasks with exact ownership;
4. prevents workers from applying conflicting ad-hoc fixes.
