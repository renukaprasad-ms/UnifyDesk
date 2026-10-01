# Integration Plan

## Precondition

Main reviews the L1 audit/contracts/tasks first.

Before releasing workers:
1. merge the L1 planning PR to `dev`;
2. sync latest `dev`;
3. record that exact merge-tip SHA as the **Core Messaging Journey R1 frozen worker base**;
4. update the three worker prompt files with that one SHA if needed;
5. every worker branches from exactly that SHA.

No worker rebases onto later moving `dev`.

## Planned branches

- B1: `feature/core-messaging-broadcast-r1`
- B2: `feature/core-messaging-identity-events-r1`
- F1: `feature/core-messaging-ui-r1`

PR target: `dev`.
Workers open PRs and stop. They do not merge.

## Independence assumptions

B1 owns Broadcast/Audience/channel execution contracts.
B2 owns provider-feedback/Inbox-Contact/notification integration.
F1 consumes the frozen API contracts in `CANONICAL-CONTRACTS.md` and does not require B1/B2 code to exist on its branch to compile; API client/types can be implemented against the frozen contract.

B1 and B2 must avoid editing the same high-conflict Broadcast services. If B2 needs a terminal Broadcast hook, use a small extension/event boundary rather than rewriting B1 execution orchestration.

## L1 review gates

For every PR L1 checks:
- scope only;
- frozen contract compliance;
- no duplicated Contact/audience domain;
- company authorization;
- idempotency/transactions;
- bounded performance;
- provider truthfulness;
- tests are not weakened;
- source verification vs executed verification is stated correctly.

## Proposed integration order

1. **B1** — establishes canonical Broadcast execution/readiness and WhatsApp Broadcast backend.
2. Run backend compile/focused tests if runner available.
3. **B2** — provider feedback/Inbox Contact/notification integration.
4. Run backend compile + event/Inbox/Contacts regressions.
5. **F1** — customer journey against frozen contracts.
6. Run frontend format/lint/test/build plus backend/frontend integration smoke.
7. L1 resolves integration-only conflicts without changing frozen semantics.

If F1 is truly independent and conflict-free, its merge can precede B2, but final integrated validation must include all three.

## Required integrated regressions

- workspace A -> B -> A stale async/realtime;
- Contact import/list/segment;
- audience preview/freeze;
- Email Managed/direct send semantics;
- Managed SMS;
- WhatsApp template Broadcast;
- provider event dedupe/out-of-order;
- Inbox Contact association;
- Contact activity;
- notification completion/failure;
- cancellation/retry/recovery;
- 2k/5k/10k representative execution.

## Freeze QA SHA

After all production-code PRs and L1 integration corrections are merged:
1. ensure `dev` contains no unrelated later changes in the candidate window, or branch an integration candidate before those changes;
2. record exact integrated production-code SHA;
3. call it `Core Messaging Journey R1 QA base`;
4. create SEC/QA branch from exactly that SHA;
5. SEC/QA does not review a moving `dev`.

QA evidence/docs may merge later, but the reviewed production SHA remains immutable in the report.
