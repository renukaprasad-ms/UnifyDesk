# Prompt — QA/SEC Contacts & Audience Round 1

STATUS: BLOCKED UNTIL L1 PUBLISHES INTEGRATED SHA

You are QA/SEC for UnifyDesk Contacts & Audience Management.

Repository:
`renukaprasad-ms/messaging`

Application:
`unifydesk-app/`

Docs:
`unifydesk-app/docs/contacts-audience/`

Your review branch:
`review/contacts-audience-security-qa-r1`

Your EXACT integrated Round-1 base SHA:
`__INTEGRATED_QA_BASE_SHA__`

Do not start until L1 replaces that token with an exact immutable integrated SHA.

When activated:

- create the review branch from exactly that SHA;
- do NOT branch from moving dev;
- do NOT implement product fixes;
- do NOT merge.

## Read completely

- README.md
- RESEARCH.md
- ARCHITECTURE.md
- DECISIONS.md
- DATA-MODEL.md
- API-CONTRACT.md
- EVENT-CONTRACT.md
- IMPORT-EXPORT.md
- SEGMENTATION.md
- CONSENT-SUPPRESSION.md
- CONTACT-MERGE.md
- INTEGRATIONS.md
- SECURITY.md
- PERFORMANCE.md
- QA-SEC-PLAN.md
- INTEGRATION_STATUS.md

## Mission

Execute the complete integrated QA + Security review in QA-SEC-PLAN.md against actual merged implementation.

This is not another architecture exercise.

Verify the implementation for:

- tenant isolation and authorization;
- contact CRUD/identity correctness;
- normalization;
- duplicate/merge races;
- custom fields;
- tags/lists;
- dynamic segmentation;
- consent integrity;
- suppression;
- eligibility;
- import/export security and restart safety;
- bulk operations;
- immutable audience snapshots;
- async/realtime failure recovery;
- storage accounting;
- lifecycle/purge;
- Inbox resolver;
- Broadcast backward compatibility and Contacts audience integration if enabled;
- migrations;
- frontend permissions/error/loading/accessibility/mobile;
- performance/index assumptions.

## Required automated checks

Backend:

```bash
cd unifydesk-app/backend
./mvnw spotless:check
./mvnw test
./mvnw clean test
./mvnw package -DskipTests
```

Frontend:

```bash
cd unifydesk-app/frontend
npm ci
npm run format:check
npm run lint
npm test
npm run build
```

## Migration verification

Run:

- clean database through Contacts integration migration V96;
- representative upgrade from pre-Contacts schema through V90-V96.

Record actual result.

## Large-data verification

Execute where environment permits:

- 10k contacts;
- 100k import/export/segment/snapshot;
- 1M query-plan/load scenario.

If 1M is not actually run, mark NOT EXECUTED. Do not infer pass.

Record environment and measured evidence.

## Security verification

Explicitly test:

- cross-company resource IDs;
- identity enumeration;
- export exfiltration;
- signed URL expiry;
- permission revocation before artifact access;
- CSV injection;
- upload parser abuse;
- custom field mass assignment;
- segment SQL injection/DoS;
- cursor tampering;
- consent actor/source forgery;
- opt-out reimport;
- suppression release;
- merge races;
- contact-limit race;
- PII leakage in logs/events/realtime.

## Integration verification

### Broadcast

If Contacts audience source is enabled:

- legacy Broadcast groups must still work;
- final recipient snapshot timing must match the frozen contract;
- opted-out/suppressed contacts must be excluded;
- delivery/retry/reporting behavior must remain correct.

If L1 deliberately defers enabling Contacts -> Broadcast, record that adapter as NOT RELEASED rather than failing unrelated Contacts core.

### Inbox

Verify same-company resolver only, no auto-create and no implicit opt-in.

### Ads

Verify Contacts Round 1 did not accidentally couple core schema/UI to provider-specific Google/Meta audience IDs.

No live Ads provider test is required unless L1 later explicitly integrates activation.

## Findings output

Write:

`unifydesk-app/docs/contacts-audience/SECURITY_QA_FEEDBACK.md`

Each finding:

- ID: `CA-R1-NNN`
- severity: Critical / High / Medium / Low
- title
- affected invariant/contract
- exact reproduction
- expected
- actual
- code/test evidence
- recommended owner: B1 / B2 / F1 / L1
- required regression test
- status

Overall disposition:

- RELEASE-READY
or
- NOT RELEASE-READY

Do not fix implementation findings yourself.

## Final report

Report:

- review branch
- exact integrated base SHA
- final review commit SHA
- checks/results
- migration results
- large-data/performance results
- findings by severity
- release disposition
- blockers/external prerequisites

Do not merge.
