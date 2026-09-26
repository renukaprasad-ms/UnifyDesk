# Managed Email Tasks

**Owner:** Lead / Architect  
**Status:** NOT FROZEN

The Lead must replace this file with concrete Round-1 tasks after completing `ARCHITECTURE.md`.

## Contract header

Before implementation starts, fill in:

- Architecture revision:
- Base `dev` commit SHA:
- API contract revision:
- Database migration owner:
- Shared-file owner list:
- Round:
- Frozen at:

## Backend A — Control Plane

Lead should assign concrete tasks covering only the control plane, for example:

- managed-email domain/subdomain persistence;
- SES tenant provisioning;
- tenant resource association;
- SES identity provisioning;
- DNS record generation and verification;
- scheduled verification refresh;
- managed sender activation/deactivation;
- platform subdomain allocation;
- authorization and idempotency;
- API tests.

### Exclusive files/packages

Lead must list exact paths this worker owns.

### Shared files this worker may change

Lead must list exact files or write "none".

### Acceptance criteria

Lead must provide testable conditions.

## Backend B — Delivery, Events & Metering

Lead should assign concrete tasks covering:

- managed SES transport;
- tenant/configuration-set aware sends;
- event destination integration;
- event ingestion and idempotent reconciliation;
- suppression/reputation enforcement;
- rate limits and safety suspension;
- managed usage metering;
- tests.

### Exclusive files/packages

Lead must list exact paths this worker owns.

### Shared files this worker may change

Lead must list exact files or write "none".

### Acceptance criteria

Lead must provide testable conditions.

## Frontend

Lead should assign:

- managed provider selection;
- domain-vs-subdomain onboarding;
- DNS instructions/status;
- verification refresh;
- sender identity setup;
- Broadcast sender selection/status;
- error/loading/accessibility/responsive states;
- frontend tests.

### Exclusive files/packages

Lead must list exact paths this worker owns.

### Shared files this worker may change

Lead must list exact files or write "none".

### Acceptance criteria

Lead must provide testable conditions.

## Security & QA entry gate

Security & QA does not start until Lead marks:

- [ ] Backend A integrated
- [ ] Backend B integrated
- [ ] Frontend integrated
- [ ] migrations apply cleanly
- [ ] backend build/tests pass
- [ ] frontend build/tests pass
- [ ] local/test environment flow is runnable
- [ ] architecture docs match implementation
