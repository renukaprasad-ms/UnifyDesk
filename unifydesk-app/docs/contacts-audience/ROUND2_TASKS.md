# Contacts & Audience Round-2 Remediation Tasks

Status: IMPLEMENTATION INTEGRATED — READY FOR FINAL QA

Round-1 QA disposition:

`NOT RELEASE-READY`

Frozen Round-2 remediation source base:

`f6a04c4596240d0c43bde6ef83c66619abbd9a02`

All B1/B2/F1/Lead Round-2 branches were created from that exact source base.

## Findings assigned

B1:
- CA-R1-010

B2:
- CA-R1-001
- CA-R1-002
- CA-R1-003
- CA-R1-006
- CA-R1-009
- service/runtime portion of CA-R1-008

F1:
- CA-R1-011
- CA-R1-013

Lead:
- CA-R1-004
- CA-R1-005
- CA-R1-007
- CA-R1-012
- database portion of CA-R1-008

## Integrated PRs

B1:
- PR #210
- worker head: `c38f71b5e476937484aa62909e6d5ac37ff5eb46`
- MERGED

B2:
- PR #212
- worker head: `32dc3648d33ea701600ee33bf332c0ace0d8b421`
- MERGED

F1:
- PR #211
- worker head: `78ec9046db3ac3a40487c2ec8966a53468d8dff8`
- MERGED

Lead:
- PR #213
- Lead head: `1417762df87dd51a36e705a8db291c9f70218f5b`
- migration: `V83__contacts_audience_round2_security.sql`
- MERGED

## Exact final Round-2 QA base

The ONE valid integrated implementation SHA for final QA is:

`75adcc7dd17105c2a62570c56a8e7c2509321398`

Final QA branch:

`review/contacts-audience-security-qa-r2`

Create it from EXACTLY:

`75adcc7dd17105c2a62570c56a8e7c2509321398`

Do NOT branch final QA from moving `dev`, from `f6a04c...`, or from an individual worker head.

## Round-2 migration

Lead owns:

`V83__contacts_audience_round2_security.sql`

V80/V81/V82 remain unchanged historical migrations.

## Engineering invariants that final QA must re-check

- tenant isolation
- destructive permission boundaries
- async permission revocation
- consent authority/provenance
- idempotency
- Contact/identity ownership
- bounded import transaction/checkpoint atomicity
- company privacy purge
- batch UPDATE_FIELD performance
- bounded DELETE transactions
- stable cursor signing
- frontend/backend contract parity
- legacy Broadcast compatibility
- List/Segment Broadcast snapshot semantics

## Execution evidence rule

Round-2 worker/Lead environments could not establish trustworthy CI execution because affected GitHub jobs contained zero workflow steps.

Final QA must distinguish:

- SOURCE/CONTRACT REVIEWED
- LOCAL/MOCK VERIFIED
- DB INTEGRATION VERIFIED
- EXECUTION BLOCKED

Never call a zero-step job PASS.

## Final QA

Prepared prompt:

`SECURITY_QA_R2_FINAL_CHAT_PROMPT.md`

Exact QA base:

`75adcc7dd17105c2a62570c56a8e7c2509321398`

Final report:

`SECURITY_QA_FEEDBACK_R2_FINAL.md`
