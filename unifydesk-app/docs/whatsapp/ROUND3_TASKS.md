# Meta WhatsApp Round-3 Targeted Remediation

Status: READY  
Exact frozen worker base SHA: `6963caf049822a0133095ae2ae481848a257ad80`

Source:
- `SECURITY_QA_FEEDBACK_R2.md`

## Scope

Round 3 fixes exactly one residual finding:

`WA-R1-003 — MEDIA message submission permit timing`

Round-2 QA passed WA-R1-001, WA-R1-002, WA-R1-004, WA-R1-005, WA-R1-006, WA-R1-007 and WA-R1-008 at integrated source/contract level.

Do not reopen those findings unless the targeted change causes a regression.

## Owner

B2 only.

Branch:
`fix/whatsapp-runtime-r3`

Base:
`6963caf049822a0133095ae2ae481848a257ad80`

No B1 or F1 worker is required.

## Required behavior

The distributed per-phone limiter must guard the actual Meta message submission.

Required provider-operation order:

TEXT:
`reserve -> permit -> sendText`

TEMPLATE:
`reserve -> permit -> sendTemplate`

MEDIA:
`reserve -> read local media -> uploadMedia -> persist/reuse provider-media link -> permit -> sendMedia`

For MEDIA, the permit MUST NOT be consumed before the potentially slow media read/upload.

The permit must be acquired immediately before `sendMedia`, after upload preparation completes.

## Local limiter failure after MEDIA upload

If the limiter rejects/fails closed after provider media upload:
- do not call `sendMedia`;
- classify this as a definite pre-message-submission local failure, not UNKNOWN;
- transition the reserved runtime message to a durable FAILED state with a stable safe code:
  - `WHATSAPP_RATE_LIMITED` for local rate rejection;
  - `WHATSAPP_RATE_LIMIT_UNAVAILABLE` for Redis/limiter infrastructure failure;
- generic Inbox message becomes FAILED consistently;
- publish the normal status-change realtime event;
- do not automatically retry the message;
- a duplicate `clientRequestId` returns the existing durable FAILED logical result and does not upload/send again.

An already uploaded provider-media object may be left for provider-side expiry/cleanup; do not risk message duplication by automatic replay.

## Preserve Round-2 behavior

Do not regress:
- phone-scoped Redis key;
- multi-instance shared budget;
- provider 429 Retry-After cooldown;
- numeric/RFC-1123 Retry-After parsing;
- fail-closed Redis semantics;
- stale PENDING -> UNKNOWN recovery;
- outbound idempotency;
- detailed whatsappStatus;
- media inbound reservation idempotency.

## Required tests

Keep and satisfy:
`MetaWhatsAppRound2RateLimitQaTests.mediaPermitIsAcquiredAfterUploadImmediatelyBeforeMessageSubmission`

Add/extend focused tests:
1. TEXT order remains permit -> sendText.
2. TEMPLATE order remains permit -> sendTemplate.
3. MEDIA order is uploadMedia -> permit -> sendMedia.
4. delayed media upload longer than outboundRateWindow still requires a fresh permit immediately before sendMedia.
5. MEDIA local rate rejection after successful upload causes zero sendMedia calls.
6. MEDIA Redis failure after successful upload causes zero sendMedia calls.
7. limiter failure produces durable FAILED, not PENDING/UNKNOWN.
8. duplicate same clientRequestId after that failure returns existing FAILED and causes zero second upload/send.
9. provider 429 after sendMedia still imposes shared cooldown.
10. existing Redis integration test remains valid.

## Ownership

B2 may modify:
- `MetaWhatsAppMessageService.java`
- small B2 runtime helper(s) if needed
- focused B2 tests

Do not modify:
- B1 control plane
- webhook normalization/media inbound path unless strictly necessary
- frontend
- migrations unless absolutely required (none expected)
- Round-2 QA tests except to add non-weakened coverage
- Ads
- Broadcast

No new migration is expected. If you believe one is required, stop and report it as an integration request instead of adding it.

## Checks

Run:
```bash
cd unifydesk-app/backend
./mvnw spotless:check
./mvnw test
./mvnw clean test
./mvnw package -DskipTests
```

Run the Redis integration test where infrastructure permits.

## Final report

Report:
- branch
- final SHA
- files changed
- exact WA-R1-003 fix
- provider-operation ordering for TEXT/TEMPLATE/MEDIA
- limiter failure state behavior
- duplicate clientRequestId behavior
- tests/checks/results
- Redis result
- blocker/deviation

Do not merge.
