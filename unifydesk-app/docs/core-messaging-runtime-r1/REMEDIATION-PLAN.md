# Remediation Plan

## Principle

Do not manufacture B1/B2/F1 work. Create implementation assignments only when evidence proves a production-source defect that belongs in this narrow round.

## MAIN decision — Provider-4 scope resolved

MAIN selected the scope-correction option.

Provider-4 canonical direct Email regression means the modern supported customer-managed bulk Email providers:

1. Amazon SES
2. SendGrid
3. Azure Communication Services Email
4. MSG91 Email

Gmail, Microsoft, IMAP/SMTP and SMTP mailbox providers are outside Provider-4 verification for this round. No production remediation is required merely to make those historical mailbox enum values launchable as modern Broadcast senders.

The prior `CMR1-AUDIT-001` requirement/scope mismatch is therefore **RESOLVED BY SCOPE DECISION**, not by source modification.

No B1/B2/F1 implementation assignment is created for mailbox-backed Broadcast.

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
7. Regression-verify Provider-4 against Amazon SES, SendGrid, Azure Email and MSG91 Email after the Managed Email/SES path.
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
