# UnifyDesk Ad Center — Final Security / QA Feedback

Status: IN PROGRESS

Frozen application base SHA:

`88af16fe1cd6966730c26447119416104039e6bb`

Review branch:

`review/ad-center-security-qa-final`

Ad Center completion integration:

PR #193

## 1. Verification status

| Area | Status | Evidence |
| --- | --- | --- |
| Source/security audit | IN PROGRESS | Final QA branch created from frozen base |
| Backend full suite | NOT EXECUTED | Awaiting executable environment |
| Frontend tests/build/lint/format | NOT EXECUTED | Awaiting executable environment |
| Flyway clean/upgrade | NOT EXECUTED | Awaiting PostgreSQL test environment |
| Tenant/RBAC matrix | IN PROGRESS | Source shows company-scoped access + permission helpers; adversarial tests still required |
| OAuth state/replay | IN PROGRESS | Google and Meta use Redis-backed one-time state; executable replay tests required |
| Meta webhook signature | IN PROGRESS | HMAC signature verification present; adversarial tests required |
| Launch Saga/idempotency | IN PROGRESS | Idempotency key + provider deployment planning present; failure/restart tests required |
| PII/first-party audiences | IN PROGRESS | Google/Meta hashing flows exist; persistence/logging verification required |
| Real Google Ads | NOT ATTEMPTED | Requires connected Google Ads test account |
| Real Meta Ads | NOT ATTEMPTED | Requires connected Meta Ads test account |

## 2. Initial source-review observations

The frozen code contains the expected security primitives, but these observations are not PASS decisions until tested:

- Google OAuth state is stored in Redis and consumed atomically with get-and-delete semantics.
- Meta OAuth uses the same one-time server-side state pattern.
- Google callback flow re-checks the initiating user's ADS_CONNECT permission.
- Ad Center controllers use explicit read/create/modify/connect permission gates.
- provider/domain lookups commonly include company ID rather than trusting object ID alone.
- Meta Ads webhook endpoints validate `X-Hub-Signature-256` when configured.
- Google first-party Data Manager paths normalize/hash identifiers before provider submission.
- unified launch planning persists an idempotency key and provider deployment/step state.
- campaign creation paths are designed around PAUSED-first remote resources.
- G-11 portfolio/shared-budget backend validates compatible campaign types and aligned-pair constraints.

These are targets for adversarial verification, not final findings.

## 3. Findings

No finding is closed or opened yet. Use:

- `ADS-FINAL-NNN`
- severity
- state
- exact evidence
- impact
- recommended owner
- closure test

## 4. Application-code readiness

`NOT EXECUTED`

Reason: full backend/frontend/Flyway executable gate has not yet run on this frozen SHA.

## 5. Google provider readiness

`NOT ATTEMPTED`

Real-provider evidence is required.

## 6. Meta provider readiness

`NOT ATTEMPTED`

Real-provider evidence is required.

## 7. Overall production readiness

`NOT RELEASE-READY`

This is an initial status only. Development is complete, but final executable and real-provider verification are still pending.
