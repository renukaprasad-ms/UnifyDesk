# UnifyDesk Core Messaging Journey R1

## Purpose

Core Messaging Journey R1 is an integration/product-flow initiative for the customer journey:

`Workspace -> Contacts/Audience -> Broadcast -> Channel -> Delivery -> Inbox`

It does not rebuild Contacts, Audience, Broadcast, Managed Email, Managed SMS, WhatsApp, Inbox, Notifications, or provider integrations that already exist. It closes only the gaps required to make those domains act as one coherent SaaS product.

## Audit baseline

- Repository: `renukaprasad-ms/UnifyDesk`
- Audited branch: `dev`
- Exact audited dev SHA: `1a39af74156e21ae5d4d7e7f4e0c32ddc0851daf`
- Audit date: 2026-10-01
- GitHub Actions run at audited SHA: `36874477918`
- CI disposition: EXECUTION BLOCKED. All five jobs ended with no runner and `steps=[]`; this is neither a test PASS nor an application-code FAIL.

## Scope

R1 owns integration correctness for:

- workspace-scoped messaging state;
- canonical Contact/List/Segment audience reuse;
- recipient preview and eligibility explanation;
- execution snapshots and send-time eligibility revalidation;
- Broadcast channel/sender readiness;
- Email/SMS/WhatsApp Broadcast execution where required for the primary product journey;
- delivery/accounting truthfulness;
- Inbox-to-canonical-Contact identity association;
- required notification events;
- idempotency, transaction, retry, crash-recovery and 2k-10k recipient behavior;
- tenant-safe frontend async/realtime behavior.

## Explicitly out of scope

- Ads Center feature expansion;
- advanced journey/automation builders;
- A/B experimentation;
- analytics warehouse work;
- production HA/cloud expansion;
- arbitrary new messaging providers;
- sophisticated segmentation features not needed by the primary journey.

## Verification vocabulary

Every capability is classified independently:

- **SOURCE VERIFIED** — current repository source implements the behavior.
- **AUTOMATED VERIFIED** — tests actually executed and verified it.
- **LOCAL/INTEGRATION VERIFIED** — exercised in a suitable executable environment.
- **PROVIDER VERIFIED** — exercised against the real external provider.
- **BLOCKED** — runner, credentials, approval or environment prevents the verification level.

Source review must never be promoted to provider verification. A GitHub Actions job with no runner/steps is never a PASS.

## Lifecycle

`Main -> L1 -> B1/B2/F1 -> L1 integration -> SEC/QA -> targeted remediation -> SEC/QA`

The architecture/contracts and Round-1 tasks are frozen by L1 before implementation workers start. All workers use one exact base SHA and do not rebase onto moving `dev`.
