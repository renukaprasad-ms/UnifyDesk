# Contacts & Audience QA / Security Plan

## 1. Activation

QA starts only after L1 integrates B1, B2 and F1 and publishes an exact integrated SHA.

Review branch:

`review/contacts-audience-security-qa-r1`

QA does not implement fixes.

## 2. Architecture invariants

Verify:

- contact ID is canonical;
- identities are multi-valued and company-scoped;
- exact identity uniqueness works;
- no fuzzy auto-merge;
- list membership != consent;
- consent != suppression;
- segment definition is source of truth;
- audience snapshot is immutable;
- existing Broadcast behavior remains compatible;
- no Ad Center provider implementation was accidentally added.

## 3. Authorization / IDOR matrix

For every resource:

- contact
- identity
- tag
- list
- list member
- segment
- custom field
- consent preference/event
- suppression
- import
- export
- bulk job
- audience snapshot internal seam

Test:

- owner/admin/member/custom role;
- permission present/absent;
- foreign company ID;
- foreign child ID under local parent route;
- platform-admin workspace access;
- deleted/suspended company behavior;
- enumeration-safe 404/403 semantics.

## 4. Contact CRUD

Test:

- create with email only;
- phone only;
- multiple identities;
- no identity;
- invalid email;
- invalid phone;
- national phone with/without region;
- duplicate exact email;
- duplicate exact phone;
- same identity in another company;
- primary uniqueness;
- owner inactive/foreign;
- optimistic-lock conflict;
- archive/restore;
- soft delete/restore;
- purge eligibility.

## 5. Normalization

Email:

- case;
- whitespace;
- Unicode normalization;
- plus tags preserved;
- dots preserved;
- malformed IDN/domain;
- long address.

Phone:

- E.164;
- national with default country;
- ambiguous;
- invalid;
- extension handling;
- leading zeros;
- same phone formatted differently.

## 6. Merge

- deterministic source/target;
- union tags/lists;
- target wins;
- fill nulls;
- identity consolidation;
- third-contact conflict;
- same-contact merge;
- already merged source;
- concurrent reverse merge;
- merge vs import;
- merge vs list/tag bulk;
- consent safety;
- suppression safety;
- historical Broadcast data unchanged.

## 7. Custom fields

- all supported types;
- wrong types;
- options;
- archived field;
- unknown field;
- 64KiB contact JSON cap;
- definition entitlement;
- duplicate normalized key;
- malicious key/value;
- segment predicates against custom fields.

## 8. Lists/tags

- normalized duplicate names;
- cross-company member injection;
- duplicate membership idempotency;
- member counts under concurrent add/remove;
- archive behavior;
- contact deletion/merge behavior.

## 9. Segments

Validate AST:

- valid AND/OR;
- field/operator matrix;
- null/empty;
- custom fields;
- tags/lists;
- consent;
- suppression;
- date/timezone;
- max depth;
- max clauses;
- max IN;
- invalid/foreign refs;
- SQL injection payloads;
- cursor tampering;
- definition version change while paging.

Performance:

- explain/index plan;
- 10k;
- 100k;
- 1M where feasible;
- expensive count -> stale/async, not request-thread exhaustion.

## 10. Consent/preferences

- UNKNOWN default;
- explicit opt-in;
- opt-out;
- opt-out then later valid opt-in;
- repeated idempotency;
- import consent evidence;
- stale import cannot clear newer opt-out;
- actor/source spoofing blocked;
- list add does not opt in;
- delete/reimport preserves policy;
- cross-company same email independent.

## 11. Suppression

- hard bounce;
- complaint;
- invalid destination;
- manual admin;
- provider replay;
- release rules;
- active suppression overrides opt-in;
- multiple reasons;
- merge;
- identity replacement;
- legacy Broadcast suppression bridge.

## 12. Eligibility

For EMAIL/SMS/WHATSAPP marketing:

- active eligible;
- archived/deleted;
- missing identity;
- invalid identity;
- unknown consent;
- opted out;
- suppressed;
- multiple identities primary selection;
- exclusion reason counts.

Provider-specific rules must not be incorrectly represented as generic eligibility.

## 13. Import abuse/correctness

- valid CSV;
- UTF-8 BOM;
- malformed quoting;
- empty;
- oversize;
- huge row;
- >200 columns;
- duplicate headers;
- duplicate rows;
- create only;
- skip matched;
- upsert matched;
- conflict to two contacts;
- custom field mapping;
- consent mapping;
- target list;
- idempotency key replay;
- crash at chunk checkpoint;
- stale worker reclaim;
- cancel;
- storage quota;
- contact limit;
- permission revoked;
- PII in logs;
- error report.

## 14. Export

- list;
- segment;
- filtered all;
- explicit IDs;
- custom fields;
- formula injection;
- large stream;
- permission revoked before access;
- signed URL expiry;
- foreign export;
- artifact cleanup;
- storage accounting/release;
- no audit evidence leakage.

## 15. Bulk operations

- add/remove tag/list;
- update field;
- archive;
- delete;
- export;
- explicit <=cap;
- segment/list selection;
- stale version;
- idempotency;
- cancellation;
- crash/reclaim;
- cross-company selection;
- permission mapping.

## 16. Audience snapshots

- immediate preparation;
- scheduled preparation timing;
- immutable after READY;
- segment changes mid-snapshot;
- consent changes before preparation reflected;
- changes after preparation do not mutate active snapshot;
- dedupe destinations;
- primary identity selection;
- exclusion counts;
- retry idempotency;
- deleted contact during preparation;
- million-row bounded-memory behavior.

## 17. Inbox integration

- known sender resolves;
- unknown sender;
- same email different company;
- archived/deleted contact visibility;
- no auto-create;
- no implicit consent;
- foreign conversation cannot resolve foreign contact.

## 18. Broadcast regression

Before enabling Contacts audiences:

- legacy groups still list/create/import/send;
- current raw-destination broadcasts unchanged;
- hard-bounce behavior preserved;
- contact list source;
- contact segment source;
- snapshot counts;
- final broadcast recipient rows;
- scheduled audience timing;
- duplicate destination;
- suppressed/opted-out excluded;
- delivery retries/reporting unchanged.

## 19. Storage / async / realtime

- CONTACT_DATA accounting;
- EXPORT accounting;
- reserve/release replay;
- async progress;
- Redis failure;
- websocket failure;
- worker multi-pod claim;
- stale reclaim;
- storage outage.

## 20. Lifecycle

- contact soft delete/restore;
- purge;
- company delete with 10k+ contacts;
- bounded purge transactions;
- no FK deadlock;
- no orphan B2 rows;
- historical broadcast reporting remains valid.

## 21. Migrations

Test:

### Clean
Run Flyway from empty DB through V96.

### Upgrade
Representative current production-like schema through:
- V79;
- B1 V90-V92;
- B2 V93-V95;
- L1 V96.

Verify indexes/constraints/FKs and rollback-by-fix-forward assumptions.

## 22. Automated checks

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

## 23. Performance recording

Report actual:
- environment;
- dataset;
- p50/p95 where measured;
- rows/sec import/export;
- segment query plan;
- memory observation;
- DB CPU/locks if available.

Never claim a 1M test passed if it was not executed.

## 24. Findings format

Write:

`unifydesk-app/docs/contacts-audience/SECURITY_QA_FEEDBACK.md`

Each:

- ID: CA-R1-NNN
- severity: Critical / High / Medium / Low
- title
- invariant
- exact reproduction
- expected
- actual
- evidence
- owner: B1 / B2 / F1 / L1
- regression test
- status

Overall:
- RELEASE-READY
- NOT RELEASE-READY
