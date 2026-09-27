# Notification QA / Security Plan

Status: prepared in Round 0; execution waits for L1 integrated frozen SHA.

## 1. Process

QA/SEC MUST NOT branch from moving `dev` or from any individual B1/B2/F1 branch.

L1 first:

1. reviews B1/B2/F1 independently against frozen contracts;
2. integrates them;
3. adds/adjusts lead-owned cross-module migration V72;
4. resolves migration-number collisions if needed;
5. runs backend/frontend checks;
6. publishes exact immutable integrated SHA;
7. updates `SECURITY_QA_CHAT_PROMPT.md` or hands QA the exact SHA without changing product contracts.

Then QA/SEC creates:

`review/notification-security-qa-r1`

from that exact integrated SHA.

QA/SEC reviews/tests; it does not broadly redesign the feature.

## 2. Architecture conformance

Verify:

- no worker imported another worker's branch-only classes;
- B2 reads V70 core tables through frozen SQL/JDBC contract;
- F1 matches API/event fields exactly;
- source event recording is transactionally coupled to company/inbox persistence;
- Kafka/realtime are not durability boundaries;
- no SMTP/push provider call under DB transaction/row lock;
- no arbitrary public notification creation API;
- legacy OTP path still works.

## 3. Migration tests

From a clean database:

- V70 alone on B1 branch;
- V71 alone on B2 branch from worker base;
- integrated migration sequence including V70/V71/V72;
- upgrade from frozen baseline schema;
- indexes/unique constraints exist;
- partial uniqueness works;
- constraints do not break existing company/user deletion lifecycle.

Check migration-number collision against integrated dev history.

## 4. Core functional cases

### Company created

- one source event;
- one in-app notification;
- unread +1;
- one logical welcome email intent;
- creator is recipient;
- company.businessEmail is not recipient;
- duplicate recorder call creates no additional logical effect;
- business transaction rollback creates none.

### Inbox activity

- inbound unread message records source event;
- outbound/read message does not;
- provider resync of same local message ID does not double increment;
- eligible active members only;
- member lacking email.read excluded;
- multiple messages same connection/user/5m bucket coalesce;
- aggregate count exact;
- new event reopens still-current read bucket per contract;
- no subject/body/snippet copied.

## 5. Read-state concurrency

Test:

- repeated mark read;
- repeated mark unread;
- concurrent read/read;
- concurrent read/unread;
- coalesced arrival racing read;
- mark-all-read racing new arrival;
- account + company counters;
- counter never negative;
- reconciliation finds zero drift after test.

## 6. Pagination

- stable order for identical timestamps via ID tie-break;
- no duplicate/missing item while paging a static dataset;
- cursor/filter mismatch rejected;
- invalid cursor 400;
- page max enforced;
- deep history query uses expected seek index.

## 7. Tenant/security matrix

User A/company A attempts:

- list company B;
- get B notification by ID;
- seen B ID;
- read B ID;
- read-all B;
- preference update B;
- delete B user's subscription ID.

Expected: no B data/mutation.

Membership/permission revoked after materialization:

- list hides sensitive company row;
- B2 suppresses unsent email/push;
- push-open cannot route into inaccessible resource.

Platform admin access must not automatically subscribe admin to customer notifications.

## 8. Push security

- endpoint/key never in GET responses/logs;
- DB stores ciphertext, hash only for lookup;
- missing encryption/VAPID secret fails closed;
- cross-user endpoint registration => 409;
- same-user registration idempotent;
- max subscriptions enforced;
- 404/410 revokes;
- 429 retry respects delay;
- 401/403 does not hot-loop;
- payload contains no token/email/message content;
- openPath same-origin only;
- service-worker click cannot open external/javascript URL.

## 9. Browser UX matrix

At least current stable Chromium + Firefox + Safari/macOS where available, plus iOS/iPadOS installed web-app flow where feasible:

- unsupported;
- permission default;
- granted;
- denied;
- permission prompt only after user gesture;
- push with app closed/background;
- push while focused => no duplicate OS notification;
- notification click existing tab;
- click no tab;
- multiple tabs;
- workspace switch;
- logout/account switch;
- subscription revoked externally/browser reset.

Badging/BroadcastChannel absence must not break core UI.

## 10. Email/digest

Welcome:

- multipart HTML/text;
- escaped malicious workspace name;
- exactly one logical intent;
- current verified user email resolved at send;
- provider transient retry;
- permanent failure terminal;
- ambiguous timeout => UNKNOWN/no blind retry.

Digest:

- default OFF;
- opt-in required;
- trigger at 10 contributions;
- trigger at two-hour max;
- 15m debounce;
- 4h cooldown;
- rolling optional-email cap;
- quiet-hour deferral;
- zero unread at send => suppress;
- permission/membership revoked => suppress;
- read/seen relevance suppression;
- no message content.

## 11. Resilience/fault injection

- Kafka unavailable after source commit;
- Kafka duplicate delivery;
- handler crash after first recipients;
- fanout lease expiry;
- Redis unavailable;
- WebSocket unavailable;
- B2 crash after claim before network call;
- B2 crash around definite pre-acceptance failure;
- SMTP timeout ambiguous after possible acceptance;
- push transient 5xx;
- database transient failure.

Assert no lost durable source event, no duplicate logical local effect, bounded retry.

## 12. Performance

Use realistic generated data and verify:

- unread count does not scan user_notifications;
- list uses seek index;
- due workers use partial due indexes;
- no N+1 for list;
- source transaction latency independent of company member count;
- fanout memory bounded by chunk;
- no provider call inside transaction;
- cleanup bounded.

Capture `EXPLAIN (ANALYZE, BUFFERS)` for list/count/due-worker representative queries.

## 13. Observability

Verify metrics exist and have low-cardinality labels.

Trigger representative failures and confirm:

- safe structured logs;
- correlation/event/intent IDs available;
- secrets/content absent;
- alertable metrics move.

## 14. Accessibility/UX

F1:

- bell has accessible label including unread state;
- keyboard opens/closes menu;
- Escape behavior;
- focus management for popover/dialog;
- notification item semantic button/link;
- unread indicator not color-only;
- `aria-live` used carefully, no noisy announcement storm;
- reduced-motion respected where animation exists;
- mobile responsive;
- loading/empty/error/offline/reconnect states;
- mandatory preference controls explain locked behavior.

## 15. Build/regression suite

Backend at minimum:

```bash
cd unifydesk-app/backend
./mvnw spotless:check
./mvnw test
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

If repository scripts/CI differ at integrated SHA, QA records exact commands/results rather than silently skipping them.

## 16. Severity

- Critical: cross-tenant data leak, push secret exposure, auth bypass, arbitrary external navigation/code execution.
- High: duplicate mandatory emails, lost source events, broken idempotency, unread counter corruption at concurrency, push cross-account leak, unbounded email storm.
- Medium: retry/recovery defect with safe fallback, incorrect quiet hour/digest suppression, pagination issue.
- Low: cosmetic/wording/minor metric issue.

## 17. Round-2 handoff

QA/SEC produces `SECURITY_QA_FEEDBACK.md` with:

- finding ID;
- severity;
- evidence/reproduction;
- affected frozen invariant;
- suggested ownership B1/B2/F1/L1;
- regression test required.

L1 then creates `ROUND2_TASKS.md` from the integrated/QA-reviewed exact SHA and divides remediation so workers remain independent. No worker silently fixes another owner's files.
