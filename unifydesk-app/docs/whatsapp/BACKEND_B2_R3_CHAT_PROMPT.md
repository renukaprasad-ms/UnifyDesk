# B2 Prompt — Meta WhatsApp Round 3

You are B2 for UnifyDesk Meta WhatsApp targeted Round-3 remediation.

Your complete task is defined in:

`unifydesk-app/docs/whatsapp/ROUND3_TASKS.md`

Also read:
- `unifydesk-app/docs/whatsapp/SECURITY_QA_FEEDBACK_R2.md`
- `unifydesk-app/docs/whatsapp/ROUND2_CONTRACT.md`
- `unifydesk-app/docs/whatsapp/PERFORMANCE.md`
- `unifydesk-app/docs/whatsapp/MESSAGE-STATE.md`

Your EXACT frozen Round-3 base SHA is:

`6963caf049822a0133095ae2ae481848a257ad80`

Create:

`fix/whatsapp-runtime-r3`

from EXACTLY that SHA.

Do NOT branch from a later moving `dev`.

Your ONLY residual finding is:

`WA-R1-003`

The distributed per-phone limiter is implemented, but MEDIA currently consumes its permit before the potentially slow provider media upload.

Fix the actual message-submission ordering.

Required MEDIA order:

`uploadMedia -> requirePermit -> sendMedia`

The permit must be acquired immediately before the Meta `/messages` submission, not before media upload.

TEXT and TEMPLATE must keep:

`requirePermit -> sendText/sendTemplate`

If the MEDIA upload succeeds but the limiter then rejects or Redis fails:
- do not call `sendMedia`;
- do not leave the logical message PENDING;
- do not mark it UNKNOWN;
- persist a definite FAILED outcome with the appropriate safe limiter code;
- publish normal status realtime;
- do not auto-retry;
- duplicate same `clientRequestId` returns the existing FAILED result and performs zero second upload/send.

Keep the QA test:

`MetaWhatsAppRound2RateLimitQaTests.mediaPermitIsAcquiredAfterUploadImmediatelyBeforeMessageSubmission`

Make it pass because production ordering is fixed. Do not weaken/remove the QA test.

Add the focused tests listed in `ROUND3_TASKS.md`, including delayed media upload and post-upload limiter failure/idempotency.

Do not work on B1, F1, Ads, Broadcast, or unrelated WhatsApp code.

No migration is expected.

Run the required backend checks and Redis integration test where possible.

Commit everything and report:
- branch
- final commit SHA
- exact files changed
- exact WA-R1-003 fix
- TEXT/TEMPLATE/MEDIA ordering
- limiter failure durable-state behavior
- duplicate clientRequestId behavior
- tests/checks/results
- blocker/deviation

Do not merge.

Start the work now.
