# Contacts & Audience Integration Status

## Round-1 lineage

Frozen Round-1 worker base:

`f31f4f950cc257d4d1edd947a5038a3447599f85`

Round-1 implementation:
- B1 PR #202 — final reviewed head `4c86c66607721c85b5e3f75d4b27bddae96dc421`
- B2 PR #201 — final head `3c37f504fb5d256154f6c9f045567e514695a37a`
- F1 PR #200 — final head `e9b4d7b7fdae1efd23b3ed53962a2882b12b9787`
- Lead PR #203 — initial integration
- Lead PR #206 — final Round-1 integration corrections
- Round-1 migration head: `V82__contacts_audience_integration.sql`

Exact Round-1 QA production-code base:

`f6a04c4596240d0c43bde6ef83c66619abbd9a02`

Round-1 QA:
- branch: `review/contacts-audience-security-qa-r1`
- report: `SECURITY_QA_FEEDBACK.md`
- QA report merged to dev via PR #208
- disposition: `NOT RELEASE-READY`
- findings: 0 Critical / 5 High / 7 Medium / 1 Low

The earlier `be6de61...` SHA is historical only and is not a valid remediation or QA base.

## Round-2 remediation source base

All Round-2 remediation branches were created from exactly:

`f6a04c4596240d0c43bde6ef83c66619abbd9a02`

### B1

- branch: `fix/contacts-core-r2`
- PR: #210
- final worker head: `c38f71b5e476937484aa62909e6d5ac37ff5eb46`
- merge SHA: `2e6b0c407320ebf66c4538d630ab0c79178b0153`
- finding: CA-R1-010
- status: MERGED

### B2

- branch: `fix/contacts-audience-processing-r2`
- PR: #212
- final worker head: `32dc3648d33ea701600ee33bf332c0ace0d8b421`
- merge SHA: `4f3d3747f99f38e9f79d9ad46a874bb29222afd7`
- findings: CA-R1-001, CA-R1-002, CA-R1-003, CA-R1-006, CA-R1-009
- service/runtime portion: CA-R1-008
- status: MERGED

### F1

- branch: `fix/contacts-audience-ui-r2`
- PR: #211
- final worker head: `78ec9046db3ac3a40487c2ec8966a53468d8dff8`
- merge SHA: `cc75e32069041fc48500ad1a2b120c7b5dde4657`
- findings: CA-R1-011, CA-R1-013
- status: MERGED

### Lead

- branch: `fix/contacts-audience-integration-r2`
- PR: #213
- final Lead head: `1417762df87dd51a36e705a8db291c9f70218f5b`
- integrated merge SHA: `75adcc7dd17105c2a62570c56a8e7c2509321398`
- findings: CA-R1-004, CA-R1-005, CA-R1-007, CA-R1-012
- database portion: CA-R1-008
- migration: `V83__contacts_audience_round2_security.sql`
- status: MERGED

Lead integration also connects B2's trusted import-consent provenance through `setFromImport(...)`.

## Exact Round-2 final QA base

The ONE valid Round-2 final QA implementation SHA is:

`75adcc7dd17105c2a62570c56a8e7c2509321398`

Round-2 final QA branch:

`review/contacts-audience-security-qa-r2`

Create that branch from EXACTLY:

`75adcc7dd17105c2a62570c56a8e7c2509321398`

Do NOT create Round-2 QA from:
- moving `dev`
- the Round-1 base `f6a04c...`
- any individual B1/B2/F1/Lead head
- any earlier historical integration SHA

Final QA prompt:

`SECURITY_QA_R2_FINAL_CHAT_PROMPT.md`

Final QA report path:

`SECURITY_QA_FEEDBACK_R2_FINAL.md`

## Round-2 implementation highlights

- stable shared Contact cursor HMAC configuration; no process-local random fallback
- operation-specific bulk authorization
- current authorization rechecked at async claim/reclaim
- consent mappings require `contact.consent.modify`
- bulk idempotency key bound to canonical request
- public preference provenance cannot spoof trusted source classes
- runtime Contact/identity/channel validation for preference/suppression
- import mutations + failure outcomes + cursor checkpoint committed in one bounded DB transaction
- merged Contact references cleared safely during bounded company purge
- custom-field bulk UPDATE_FIELD uses batch mutation
- destructive bulk DELETE uses independent bounded transactions
- V83 enforces current Contact/identity ownership integrity
- frontend default export/status filters match frozen backend contract

## Execution state at final freeze

GitHub Actions runs observed for the Round-2 Lead head still completed affected jobs with zero workflow steps.

Therefore the following are NOT verified by CI and must be executed by final QA on a functioning environment when possible:

- Maven spotless/test/package
- npm install/format/lint/test/build
- clean -> V83 migration
- representative V79 -> V83 migration
- DB-backed crash/concurrency tests
- 500/2000 bulk query/transaction tests
- 10k/100k representative performance and EXPLAIN checks

Zero-step jobs are neither PASS nor proof of product-code failure.

## Freeze consistency verification

Active Round-2 final QA handoff/status documents all use:

`75adcc7dd17105c2a62570c56a8e7c2509321398`

This SHA is authoritative until Round-2 QA completes.
