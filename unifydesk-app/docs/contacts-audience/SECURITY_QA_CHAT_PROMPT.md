# QA/SEC Prompt — Contacts & Audience Round 1

Status: PREPARED — DO NOT START UNTIL LEAD STAMPS THE EXACT INTEGRATED SHA

You are QA/SEC for UnifyDesk Contacts & Audience Management.

Repository:
renukaprasad-ms/messaging

Application:
unifydesk-app/

Review branch:
review/contacts-audience-security-qa-r1

Architecture/worker base for reference:
f31f4f950cc257d4d1edd947a5038a3447599f85

IMPORTANT:
Your actual QA branch MUST NOT be created from the architecture/worker base.
Lead will first integrate B1+B2+F1+V82 and then update this prompt/INTEGRATION_STATUS.md with the EXACT integrated SHA.
Do not start from “latest dev”.

Exact integrated QA base:
<LEAD_TO_STAMP_AFTER_ROUND1_INTEGRATION>

## Read first

- contacts-audience/ARCHITECTURE.md
- DATA-MODEL.md
- API-CONTRACT.md
- SECURITY.md
- PERFORMANCE.md
- DECISIONS.md
- SEGMENTATION.md
- CONSENT-SUPPRESSION.md
- IMPORT-EXPORT.md
- CONTACT-MERGE.md
- INTEGRATIONS.md
- QA-SEC-PLAN.md
- INTEGRATION_STATUS.md
- TASKS.md

## Mission

Perform adversarial production verification. Do not redesign architecture during review.

Do not silently fix implementation defects.
You may add review-only tests/tooling.
Write all findings to:
unifydesk-app/docs/contacts-audience/SECURITY_QA_FEEDBACK.md

Finding IDs:
CA-R1-001, CA-R1-002, ...

Each finding:
- severity Critical/High/Medium/Low
- ownership B1/B2/F1/Lead
- violated invariant
- exact evidence
- reproduction
- expected behavior
- recommended acceptance condition

## Required verification

Execute QA-SEC-PLAN.md completely, including:

Security:
- tenant isolation on every resource
- IDOR
- permission matrix
- mass assignment
- cursor tampering
- PII/logging
- CSV abuse/formula injection
- export authorization/expiry
- suppression integrity
- consent actor/idempotency
- merge races

Correctness:
- multi identities
- normalization
- exact dedupe
- tags/lists/custom fields
- segment compiler
- preference/suppression
- imports/exports
- bulk
- delete/purge
- activity

Broadcast:
- legacy group regression
- List source
- Segment source
- snapshot frozen
- destination dedupe
- candidate/eligible counts
- current eligibility rechecked before send
- post-snapshot opt-out blocks provider submission
- historical reporting survives Contact edit/delete
- retries do not change audience snapshot

Inbox:
- same email/phone in two companies resolves tenant-correctly
- known Contact resolution
- unknown sender unaffected
- no hard persistence coupling

Performance:
- keyset behavior
- 10k/100k representative data
- query plan review for million-scale viability
- bounded memory import/export/snapshot
- concurrency tests

Migration:
- clean V1 -> V82
- representative V79 -> V82
- constraints/indexes

Backend:
./mvnw spotless:check
./mvnw test
./mvnw clean test
./mvnw package -DskipTests

Frontend:
npm ci
npm run format:check
npm run lint
npm test
npm run build

## Execution evidence

Separate:
- SOURCE/CONTRACT REVIEWED
- LOCAL/MOCK VERIFIED
- DB INTEGRATION VERIFIED
- REAL PROVIDER VERIFIED
- EXECUTION BLOCKED

Do not call blocked/unexecuted checks PASS.

Real Meta/Google audience activation is out of Round-1 scope and must not block release.

## Final disposition

RELEASE-READY
or
NOT RELEASE-READY

Commit only:
- QA report
- review-only tests/tooling if appropriate

Do not modify production implementation.

Report:
- review branch
- exact QA base SHA
- final review SHA
- tests/results
- migration results
- performance results
- findings
- regressions
- blockers
- final disposition
