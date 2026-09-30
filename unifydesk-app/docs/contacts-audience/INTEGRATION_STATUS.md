# Contacts & Audience Integration Status

Last updated: 2026-09-30

## Round 0

- [x] current dev inspected
- [x] existing Managed Email/SMS/Notification/WhatsApp workflow inspected
- [x] existing Billing/Entitlements inspected
- [x] existing Storage accounting inspected
- [x] existing Async Jobs/realtime inspected
- [x] existing Broadcast groups/snapshot/suppression inspected
- [x] existing Inbox participant model inspected
- [x] existing Ad Center audience projection inspected
- [x] current Flyway range inspected
- [x] public product/API research
- [x] V1 scope decided
- [x] contact identity model decided
- [x] custom field model decided
- [x] lists/tags/segments decided
- [x] consent/suppression/eligibility decided
- [x] import/export decided
- [x] audience snapshot timing decided
- [x] deletion/merge semantics decided
- [x] worker boundaries decided
- [x] migration allocation decided
- [ ] architecture docs merged to dev
- [ ] Round-1 frozen SHA published

## Round 1 workers

B1:
- branch: feature/contacts-core-r1
- base: pending architecture merge
- status: NOT STARTED

B2:
- branch: feature/contacts-audience-runtime-r1
- base: pending architecture merge
- status: NOT STARTED

F1:
- branch: feature/contacts-audience-ui-r1
- base: pending architecture merge
- status: NOT STARTED

QA:
- branch: review/contacts-audience-security-qa-r1
- base: starts only after integration
- status: BLOCKED BY DESIGN

## Existing module integration

### Inbox
Design complete; implementation glue owned by L1 after worker integration.

### Broadcast
Legacy behavior preserved. Contacts audience snapshot adapter planned; L1 decides final Round-1 enablement only after integrated regression passes.

### Managed Email
Central suppression bridge contract defined; integration glue after B2.

### Managed SMS
Central phone/consent seam defined; provider-specific DLT remains isolated.

### WhatsApp
Central phone/preference seam defined; provider runtime isolated.

### Ad Center
No implementation changes in Contacts Round 1.

### Billing
CONTACT_LIMIT and SEGMENT_LIMIT already exist.

### Storage
CONTACT_DATA and EXPORT usage types already exist.

### Async Jobs
Service exists; B2 extends job types.

## Release gate

Round 1 cannot be called complete until:

- B1/B2/F1 integrated;
- migrations clean/upgrade tested;
- backend checks pass;
- frontend checks pass;
- QA/SEC completes;
- no release-blocking finding remains.
