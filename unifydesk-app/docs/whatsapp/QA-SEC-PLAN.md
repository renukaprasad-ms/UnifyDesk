# Meta WhatsApp QA / Security Plan

Status: FROZEN PLAN — DO NOT START UNTIL L1 PUBLISHES INTEGRATED QA SHA

QA/SEC branch:
`review/whatsapp-security-qa-r1`

Base:
TBD integrated SHA after B1+B2+F1 integration.

Output:
`unifydesk-app/docs/whatsapp/SECURITY_QA_FEEDBACK.md`

## 1. Build and migration

Backend:
- spotless:check
- full test
- clean test/package
- clean Flyway V1 -> latest
- representative upgrade from current pre-WhatsApp schema through V76-V78
- duplicate/constraint checks

Frontend:
- npm ci
- format check
- lint
- tests
- build

No failed/runner-skipped suite may be reported as PASS.

## 2. Connection control plane

Test:
- signup session only on explicit Connect click
- TTL expiry
- state company/user binding
- GETDEL replay
- two parallel completes
- cancellation then new session
- invalid/tampered phone/account ID
- multiple WABAs/phones
- same provider phone claimed concurrently by two companies
- token exchange failure
- missing permission
- registration failure
- subscription failure
- success then local-response loss/retry
- DB persisted then worker retry
- reconnect same asset
- reconnect attempts different asset
- generic disconnect and Meta-specific disconnect
- purge cancellation on reconnect

Expected:
- convergence without duplicate connection/sender
- no foreign ownership transfer
- no secret leakage

## 3. Health/revocation

- valid active connection
- expired token
- revoked permission
- provider 429/5xx
- phone inaccessible
- app subscription lost
- quality/registration action required
- manual refresh rate limit
- Connections page causes no provider N+1 calls

## 4. Webhook authenticity

- valid signature
- missing signature
- wrong signature
- malformed hex
- wrong app secret
- body modified after signing
- body over max
- invalid JSON with valid signature
- deep JSON
- duplicate callback
- callback with many bounded effects

Confirm unsigned payload is not durably stored.

## 5. Tenant isolation

- webhook phone maps to correct company
- unknown phone
- duplicate active phone mapping triggers fail-safe
- foreign conversation ID
- foreign message ID
- foreign media ID
- foreign template ID
- user with whatsapp.read but no email.read
- user with email.read but no whatsapp.read
- mixed Inbox list without connectionId exposes only permitted channels

## 6. Inbound

- text
- reply/context
- image
- document
- audio/video/contacts/location/reaction unsupported placeholder
- duplicate message
- out-of-order callback
- malformed message
- huge text bounded
- unread count
- existing notification post-processor
- realtime snapshot/reconnect

Exactly one visible Inbox message for duplicate provider ID.

## 7. Outbound

- text inside service window
- reply
- template outside service window
- free-form blocked outside
- image
- PDF
- invalid MIME
- inactive connection
- action-required connection
- reauth-required connection
- duplicate clientRequestId
- provider 400 definite failure
- provider 429
- provider 5xx before known submit
- network timeout after submit -> UNKNOWN/no blind replay

## 8. Status

- accepted
- sent
- delivered
- read
- failed
- duplicate status
- read then late sent
- delivered then older failed
- unknown provider message ID
- wrong-company/provider message collision

Final state monotonic.

## 9. Templates

- sync approved/rejected/paused states
- cache freshness
- wrong component variable count
- malicious large variables
- foreign template
- non-approved send
- provider template policy error

## 10. Media security

- outbound company ownership
- inbound authenticated metadata lookup
- oversized media
- content-type mismatch
- storage outage
- Meta URL expiry/retry
- no public permanent URL
- no arbitrary URL SSRF path

## 11. Frontend

- v4 launch config
- no sessionInfoVersion 3
- SDK script failure
- popup CANCEL
- ERROR
- FB login callback without code
- FINISH without IDs
- timeout
- exactly-once listener cleanup
- fresh session every retry
- completion loading
- ACTIVE/DEGRADED/ACTION_REQUIRED/REAUTH states
- reconnect
- disconnect confirm
- template-required composer
- template picker variables
- image/PDF attachment
- responsive
- keyboard/accessibility
- no raw Graph error/secrets

## 12. Real Meta provider tests

Mocks are not sufficient for:
- v4 onboarding
- real token fields/expiry
- register/subscribed_apps
- inbound webhook
- text/template/media send
- status delivery/read
- permission revocation
- account-model identifiers
- coexistence if enabled
- throughput/quality fields

Report these separately as PROVIDER VERIFIED / NOT VERIFIED.

## 13. Performance/fault

- duplicate burst
- webhook max body latency
- worker concurrent claim
- DB outage during webhook -> non-200 durable failure
- Kafka/realtime outage after Inbox commit
- Redis outage signup
- provider rate limit
- template cache zero Graph calls on render

## Findings format

Each finding:
- ID: WA-R1-NNN
- severity: Critical/High/Medium/Low
- title
- affected invariant
- exact reproduction
- expected
- actual
- code/evidence
- recommended owner: B1/B2/F1/L1
- required regression test
- status

QA gives no implementation merge. L1 creates targeted Round-2 assignments.
