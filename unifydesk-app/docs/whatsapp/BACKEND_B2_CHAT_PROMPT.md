# Prompt — B2 Backend Messaging / Webhook / Delivery

You are B2 for UnifyDesk Meta WhatsApp.

Repository:
`renukaprasad-ms/messaging`

Application:
`unifydesk-app/`

Docs:
`unifydesk-app/docs/whatsapp/`

Your branch:
`feature/whatsapp-runtime-r1`

Your EXACT frozen base SHA:
`cd5961362ddba0eda45d0ee1a881e93bab2f214c`

Create your branch from exactly that SHA. Do not branch from moving dev. Do not merge.

## Read first

- README.md
- ARCHITECTURE.md
- DATA-MODEL.md
- API-CONTRACT.md
- EVENT-CONTRACT.md
- WEBHOOK-CONTRACT.md
- MESSAGE-STATE.md
- TEMPLATE-POLICY.md
- SECURITY.md
- PERFORMANCE.md
- TASKS.md

Inspect:
- existing Meta webhook controller/tests
- Inbox entities/controllers/query/repositories
- Gmail/Microsoft Inbox persistence patterns
- ProviderCredentialService
- Kafka/realtime
- InboxMessagePostProcessor
- Media subsystem/storage providers
- notification inbox post-processor

## Mission

Implement the runtime plane:
- secure webhook intake
- durable event queue
- inbound Inbox persistence
- status reconciliation
- outbound text/reply/template/image/PDF
- template sync/cache
- customer-service policy
- provider-aware Inbox authorization/query
- media integration
- realtime/notification hooks

## Exact ownership

Existing files you own:
- backend/src/main/java/com/messaging/channel/meta/MetaWhatsAppWebhookController.java
- backend/src/test/java/com/messaging/channel/meta/MetaWhatsAppWebhookControllerTests.java
- backend/src/main/java/com/messaging/inbox/controller/InboxController.java
- backend/src/main/java/com/messaging/inbox/service/InboxQueryService.java
- Inbox repositories needed for safe provider-aware queries
- Inbox DTOs only for backward-compatible augmentation if needed
- backend/src/main/java/com/messaging/media/service/MediaService.java
- backend/src/main/java/com/messaging/media/provider/StorageProvider.java
- S3StorageProvider.java and SupabaseStorageProvider.java only if required for trusted server-side inbound media ingestion
- focused tests for owned changes

New code:
- backend/src/main/java/com/messaging/channel/meta/runtime/**
- backend/src/test/java/com/messaging/channel/meta/runtime/**

Migration:
`V77__meta_whatsapp_runtime.sql`

## Frozen B1 seam

Your branch does NOT contain V76 or B1 Java classes.

At runtime after integration you may query the V76 SQL columns documented in TASKS/DATA-MODEL through a B2-owned JdbcTemplate projection.

Do not import B1 entities/services/repositories.

Read access token through existing ProviderCredentialService OAUTH_TOKEN_SET and parse backward-compatible fields already in frozen base:
- accessToken
- wabaId
- phoneNumberId
- graphApiVersion

Mock your B1 SQL projection in B2 tests.

## Required implementation

### Webhook
- hard body cap before durable storage
- exact-byte X-Hub-Signature-256 validation
- signed JSON parse
- one logical durable event row per effect
- duplicate event insert is 200/no-op
- return 200 only after durable DB commit
- async claim/retry/dead processing
- no unsigned raw payload audit storage

### Inbound
- trusted phone -> local connection mapping
- text -> canonical Inbox
- reply/context mapping
- image/document -> existing private Media
- unsupported type placeholder
- duplicate external message no-op
- unread count
- message post-processors exactly once
- realtime recoverable notification

### Inbox adaptation
Current Inbox is email-only. Fix it safely:
- resolve connection/channel
- EMAIL keeps EMAIL permissions/folder semantics
- WHATSAPP uses WHATSAPP_READ/MODIFY
- mixed list only returns channels user can read
- foreign IDs remain hidden
- WhatsApp internal provider folder key = WHATSAPP, not a user-facing EmailFolder concept

### Outbound
- frozen send endpoint
- whatsapp.send authorization
- clientRequestId reservation
- TEXT/reply
- TEMPLATE
- MEDIA: image/PDF
- definite failure vs UNKNOWN ambiguity
- providerMessageId persistence
- no blind auto-retry

### Policy
- customer service session from trusted inbound
- free-form outside window -> WHATSAPP_TEMPLATE_REQUIRED
- template list/sync cache
- APPROVED + freshness + variable validation

### Status
- sent/delivered/read/failed
- monotonic/provider timestamp
- duplicate/out-of-order safe

### Media
- no arbitrary URLs
- company-owned outbound Media
- authenticated provider media retrieval
- size/MIME/time bounds
- private storage
- if server-side storage write needs interface changes, keep them minimal and fully test both S3/Supabase adapters

## Config

Do not modify application.yaml/.env.example because B1 owns them.

You may create B2-owned @ConfigurationProperties with safe defaults under:
`app.channel.meta-whatsapp.runtime`

Production config glue can be resolved by L1 at integration.

## Required tests

At minimum:
- valid/invalid signature
- signed oversized/malformed payload
- unsigned payload not stored
- duplicate webhook
- unknown phone
- cross-company mapping mismatch
- text inbound exactly once
- reply missing parent tolerated
- image/PDF media validation
- unsupported placeholder
- whatsapp vs email permission isolation
- mixed Inbox filtering
- duplicate clientRequestId
- free-form inside/outside window
- foreign conversation/template/media
- approved/stale/rejected template
- provider 400/429/5xx
- ambiguous timeout -> UNKNOWN
- status duplicates/out-of-order
- notification postprocessor once
- realtime failure does not lose durable Inbox

Run:
```bash
cd unifydesk-app/backend
./mvnw spotless:check
./mvnw test
./mvnw package -DskipTests
```

## Forbidden

Do not modify:
- B1-owned MetaGraphClient/Connection/Persistence/Properties/Provider/SignupState
- ChannelConnectionRepository/SenderIdentityRepository
- application.yaml/.env.example
- V76
- frontend
- Broadcast
- Ads
- frozen docs

## Final report

Report:
- branch
- final SHA
- files changed
- V77
- webhook/inbound/outbound/template/media implementation
- Inbox compatibility changes
- tests/checks
- B1 SQL seam assumptions
- integration requests/deviations/blocker

Do not merge.
