# Remediation Plan

## Principle

Do not manufacture B1/B2/F1 work. Create implementation assignments only when evidence proves a production-source defect that belongs in this narrow round.

## Finding CMR1-AUDIT-001 — Direct mailbox providers are not canonical Broadcast providers

### Classification

**A. SOURCE DEFECT / REQUIREMENT-SCOPE MISMATCH** if this initiative truly requires canonical Broadcast execution via Gmail, Microsoft and SMTP.

Current source is explicit:

- historical enum values exist;
- `isBroadcastProvider()` excludes them;
- `supports()` therefore returns false;
- modern `BroadcastLaunchService` rejects them;
- `BroadcastSenderService.refresh()` says mailbox providers cannot be refreshed as Broadcast senders.

### Impact

The requested Provider-4 regression suite cannot be executed as written because there is no supported canonical launch path for those providers.

### Remediation decision

**Do not implement in this narrow verification branch.**

Supporting Gmail/Microsoft/SMTP as modern canonical Broadcast transports would require an explicit product/architecture decision around sender ownership, OAuth/mailbox quotas, bulk-send behavior, delivery semantics, retries, consent/suppression, and provider events. That is broader than a verification-only repair.

MAIN/L1 should choose one of two contracts before worker assignment:

1. **Provider-4 scope correction:** canonical direct Email regression means the modern supported customer-managed bulk providers: Amazon SES, SendGrid, Azure Email and MSG91 Email; or
2. **New feature scope:** explicitly add mailbox-backed Gmail/Microsoft/SMTP canonical Broadcast, with new architecture/contracts and a separate implementation round.

No B1/B2/F1 task is created from this audit without that decision.

## Environment/provider blockers

These do not justify source changes:

- live `messaging-env` secret values not visible to repository audit;
- real SES account/domain/sender/SNS state not executed;
- MSG91 DLT/header/template/provider assets not executed;
- Meta customer onboarding blocked by Meta;
- test WABA availability not proven.

Classify them as environment/provider blockers or NOT EXECUTED after checking the deployed runtime.

## Next execution sequence

1. Run exact-head CI for `850ce7ad2874d20070067d46d8604ddc2bdd2c99` or, if this documentation PR is merged first, freeze the resulting production SHA and run the same matrix there.
2. Inspect deployed backend environment variable **names/presence only**, never secret values.
3. Execute Contact import + consent + List/Segment + Preview/freeze tests.
4. Execute Managed Email end-to-end first.
5. Execute Managed SMS end-to-end second.
6. Execute Meta test-WABA outbound/status/inbound path if assets are available.
7. Resolve Provider-4 contract mismatch before attempting Gmail/Microsoft/SMTP work.
8. Record every API ID, DB transition and provider event in `EXECUTION-RESULTS.md`.
9. If a genuine narrow source defect appears, create exactly the worker assignment needed, integrate it, freeze the resulting production SHA and hand that exact SHA to independent SEC/QA.

## Independent SEC/QA handoff gate

Do not hand off a runtime PASS until:

- exact SHA is frozen;
- executable matrix results are committed;
- blocked items are explicitly named;
- provider PASS claims contain real provider IDs/events;
- no NOT EXECUTED item is mislabeled PASS;
- any remediation has been integrated and re-frozen.
