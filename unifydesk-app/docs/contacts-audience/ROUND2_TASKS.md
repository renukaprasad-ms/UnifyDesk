# Contacts & Audience Round-2 Remediation Tasks

Status: FROZEN
Round-1 QA disposition: NOT RELEASE-READY
Frozen Round-2 implementation base: `f6a04c4596240d0c43bde6ef83c66619abbd9a02`
Round-1 QA report source: `SECURITY_QA_FEEDBACK.md`
Round-1 QA report branch head merged via PR #208: `0d06d91becbdf61f5de8ab4ee6b9b604267dd26c`

All Round-2 implementation branches MUST start from exactly:

`f6a04c4596240d0c43bde6ef83c66619abbd9a02`

Do not branch from moving `dev`.

Workers may read the latest Round-2 docs and QA report from `dev`, but production code changes must start from the frozen implementation SHA above.

## Findings

Round-1 QA found:

- 0 Critical
- 5 High
- 7 Medium
- 1 Low

Every High finding is release-blocking.

## Round-2 branches

B1:
`fix/contacts-core-r2`

B2:
`fix/contacts-audience-processing-r2`

F1:
`fix/contacts-audience-ui-r2`

Lead:
`fix/contacts-audience-integration-r2`

Final QA/SEC after Lead integration:
`review/contacts-audience-security-qa-r2`

## Migration ownership

Round-2 workers B1/B2/F1 do not create a migration.

Lead owns the only Round-2 migration if required:

`V83__contacts_audience_round2_security.sql`

Do not modify already-reviewed V80/V81/V82 migrations to hide a Round-2 fix. Use a forward migration.

## B1 ownership

Finding:
- `CA-R1-010` — Cursor HMAC key silently becomes process-local random state.

B1 only fixes stable cursor-signing configuration and focused tests.

## B2 ownership

Findings:
- `CA-R1-001` — bulk DELETE permission bypass
- `CA-R1-002` — async Contact jobs do not revalidate current authorization
- `CA-R1-003` — import can mutate consent without `contact.consent.modify`
- `CA-R1-006` — bulk idempotency key not bound to request payload
- `CA-R1-009` — user-facing preference source trust class can be spoofed

Shared service-side portion of:
- `CA-R1-008` — validate an identity belongs to the specified Contact/company and matches the requested channel before preference/suppression mutation.

QA originally assigned CA-R1-008 to Lead because the invariant spans B1/B2. Round-2 deliberately splits the fix:
- B2 owns runtime/service validation.
- Lead owns the database invariant in V83.

This avoids conflicting changes while satisfying the full QA acceptance condition.

## F1 ownership

Findings:
- `CA-R1-011` — default Contacts export sends backend-rejected identity fields
- `CA-R1-013` — UI exposes unsupported DELETED status filter

F1 must align to the existing frozen backend contract rather than widening backend export scope in this remediation round.

## Lead ownership

Findings:
- `CA-R1-004` — import mutation/checkpoint crash window
- `CA-R1-005` — company purge unsafe with merged-Contact self-FK
- `CA-R1-007` — large bulk field/delete mutation has per-Contact writes/long transaction
- database portion of `CA-R1-008` — Contact/identity relational invariant
- `CA-R1-012` — contradictory QA freeze metadata

Lead also integrates B1/B2/F1 Round-2 PRs and freezes the final R2 QA SHA.

## Independence / shared-file rule

B1, B2, F1 and Lead work in parallel from the same frozen base.

Do not cherry-pick another worker branch.

If a branch discovers a needed change in another owner's file:
1. do not take over that file silently;
2. complete owned work;
3. report an integration request.

Round-2 scope is remediation only. No new CRM features, provider audience sync, XLSX, journeys, lead scoring, or unrelated UI redesign.

## Integration order

Lead reviews every PR independently before merging.

Preferred merge order:

1. B1
2. B2
3. F1
4. Lead
5. integrated compile/test/migration verification
6. freeze exact Round-2 QA SHA
7. stamp `SECURITY_QA_R2_FINAL_CHAT_PROMPT.md`
8. start QA/SEC Round-2

## Engineering standard

All fixes must preserve:
- tenant isolation
- optimistic concurrency
- idempotency
- bounded memory
- bounded transaction duration
- keyset pagination
- JS-safe public IDs
- provider-neutral Contacts contracts
- readable production-grade code
- existing legacy Broadcast compatibility

Do not replace a security defect with a performance defect.

## Round-2 acceptance

A finding is resolved only when its precise QA acceptance condition is met and tests are added.

Do not mark a finding fixed merely because a code path looks safer.

Required evidence must distinguish:
- SOURCE/CONTRACT REVIEWED
- LOCAL/MOCK VERIFIED
- DB INTEGRATION VERIFIED
- EXECUTION BLOCKED

Zero-step GitHub jobs are not PASS.
