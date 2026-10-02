# F1 — Core Messaging Runtime R1 public Email unsubscribe page

You are **F1** for one narrow UnifyDesk Core Messaging Runtime R1 remediation.

Repository:

`renukaprasad-ms/UnifyDesk`

Your exact frozen production/remediation base is:

`f2760fe0a11ad571f984636a65304a64b77459ac`

Create:

`fix/core-messaging-email-unsubscribe-ui-r1`

from **exactly** that SHA.

Do NOT branch from moving `dev`.

Do NOT branch from PR #251 head.

Do NOT rebase.

Do NOT merge your own PR.

PR target:

`dev`

## Finding owned

Frontend portion of:

`CMR1-RUNTIME-001A — Recipient-facing Email marketing unsubscribe action missing`

Backend B2 owns token generation/validation, canonical preference mutation, idempotency, event ordering, Broadcast compliance metadata and provider transports.

You own only the minimal public confirmation/result UI.

## Read before changing code

Read the source-review evidence from PR #251 / branch:

`review/core-messaging-runtime-r1-execution`

especially:

- `unifydesk-app/docs/core-messaging-runtime-r1/EXECUTION-RESULTS.md`
- `unifydesk-app/docs/core-messaging-runtime-r1/REMEDIATION-PLAN.md`
- `unifydesk-app/docs/core-messaging-runtime-r1/E2E-VERIFICATION-MATRIX.md`
- `unifydesk-app/docs/core-messaging-runtime-r1/LIVE-EXECUTION.md`

Inspect current:

- `unifydesk-app/frontend/src/routes/AppRoutes.tsx`
- `unifydesk-app/frontend/src/App.tsx`
- auth/bootstrap behavior;
- API client/error conventions;
- responsive/public auth pages and tests.

## Frozen public contract

Implement UI against this narrow semantic contract from B2:

- recipient arrives with a single opaque/protected `token`;
- no workspace authentication is required;
- frontend must not parse/decode the token;
- frontend must not send company/contact/identity/channel/purpose IDs;
- unsubscribe mutation is a public POST owned by backend;
- backend returns generic safe success-equivalent output for valid/replayed actions;
- invalid/tampered/expired actions must not reveal unrelated Contact existence.

Coordinate endpoint naming with B2's PR contract but do not copy backend authorization logic into frontend.

## Required route

Add one public route outside `ProtectedRoute`.

Recommended product route:

`/unsubscribe`

The page may read the opaque token from query/path according to the frozen B2 contract.

It must remain accessible for both logged-out and logged-in visitors.

Do not redirect the recipient to login.

## UX

Keep the page intentionally small.

Before mutation:

- explain that the recipient can unsubscribe from marketing Email;
- one explicit confirmation action is acceptable unless B2's visible link contract intentionally performs a safe direct POST through a dedicated redirect flow.

After successful/replayed unsubscribe show success-equivalent text such as:

`You have been unsubscribed from marketing emails.`

A company display name may be shown only if B2 explicitly returns it as safe public metadata.

Do not display:

- company ID;
- Contact ID;
- identity ID;
- channel/purpose internal values;
- token contents;
- backend exception detail;
- provider details.

For invalid/expired/tampered action, use generic safe wording and do not leak whether a Contact exists.

Do not expose stack traces/API bodies.

## Security behavior

The frontend treats the token as opaque.

Never:

- decode it;
- persist it to localStorage/sessionStorage;
- send it to analytics;
- log it;
- include it in unrelated requests.

Avoid putting the token into global Redux state.

The only allowed use is the recipient-action flow.

## One-click distinction

Standards `List-Unsubscribe-Post` one-click processing is backend/provider transport work.

Do not make the browser page responsible for RFC one-click POST semantics.

The visible link/page is the human-facing confirmation/result surface.

## Tests

Add component/route tests proving:

1. `/unsubscribe` renders without authentication;
2. logged-out recipient is not redirected to `/login`;
3. valid token POST success shows success-equivalent result;
4. backend replay/success-equivalent response remains successful;
5. invalid/expired/tampered safe error does not expose identifiers;
6. no token is written to browser storage;
7. route works while user is already authenticated;
8. page is usable on mobile widths;
9. other auth/protected routes retain existing behavior.

Do not weaken current auth tests.

## Verification

Run the actual frontend scripts from `package.json`.

At minimum, as defined by the repository:

```bash
cd unifydesk-app/frontend
npm test
npm run build
npm run lint
npm run format:check
```

If exact script names differ, use the repository-defined names and report them.

## PR handoff

Open a PR to `dev`.

Do not merge it.

Return to L1 with:

- branch;
- PR;
- exact head SHA;
- route/API files changed;
- exact public UX;
- tests executed/results;
- any integration dependency on B2's frozen endpoint contract.

Do not modify Contacts/Broadcast backend source.

Do not add unrelated UI redesign.
