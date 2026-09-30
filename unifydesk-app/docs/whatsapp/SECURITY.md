# WhatsApp Security Model

Status: FROZEN FOR ROUND 1

## Security invariants

WA-SEC-01 Tenant ownership  
A company cannot connect, read, send from, reconcile or disconnect another company's Meta account/phone/conversation/template/media.

WA-SEC-02 Untrusted provider IDs  
Browser-supplied WABA/account/phone IDs are hints. Backend validates them against the exchanged Meta token before persistence.

WA-SEC-03 Signup state  
State is >=256 bits entropy, TTL <=10m, company-bound, user-bound, one-time GETDEL, never logged in full after use.

WA-SEC-04 Secrets  
Access tokens, app secret, OAuth code, registration PIN and Authorization headers never appear in frontend responses, application logs, Kafka, webhook audit or plaintext DB.

WA-SEC-05 Credential encryption  
ProviderCredentialService/CredentialCipher is mandatory for access token and PIN.

WA-SEC-06 Webhook authenticity  
POST webhook body is trusted only after exact-byte X-Hub-Signature-256 HMAC verification with current app secret.

WA-SEC-07 Webhook storage abuse  
Unsigned or oversized webhook bodies are not durably persisted. Accepted signed payload storage is bounded and retained briefly.

WA-SEC-08 Replay/idempotency  
Duplicate webhook delivery cannot create duplicate Inbox messages, status regressions, media copies or notifications.

WA-SEC-09 Trusted tenant resolution  
Webhook phone/account identifiers are resolved through local provider mappings. No company ID carried by provider payload is trusted as authorization.

WA-SEC-10 Send authorization  
Every outbound request verifies current user permission, company ownership, active connection, conversation scope and sender ownership.

WA-SEC-11 Policy authority  
Backend, not UI, enforces customer-service/template rules and approved-template state.

WA-SEC-12 Media  
Outbound media must be ACTIVE company media. Inbound media download is authenticated, size/time bounded, MIME validated and stored privately.

WA-SEC-13 Reconnect  
Reauthorization for an existing connection may not silently bind a different phone/account to the company without explicit safe workflow and ownership validation.

WA-SEC-14 Disconnect  
Disconnect removes UnifyDesk credential/use capability but does not delete customer-owned Meta business assets.

WA-SEC-15 Safe provider diagnostics  
Only bounded safe Graph diagnostic fields are logged/stored. Raw bodies are redacted or discarded.

## Threats and controls

### Cross-company asset claim
Attack: user edits phoneNumberId/WABA ID in completion request.
Control:
- token grant discovery;
- provider account/phone lookup;
- local active phone uniqueness;
- company-scoped existing-connection check;
- 404/conflict without foreign-company detail.

### State replay / parallel tabs
Attack: reuse old code/state pair or complete from another tab/user.
Control:
- Redis GETDEL before code exchange;
- company/user binding;
- TTL;
- a consumed state never becomes valid again.
A user who cancels starts a fresh session.

### Token theft through observability
Control:
- never stringify StoredMetaToken;
- RestClient interceptors/logging must redact Authorization/query access_token;
- Graph exception sanitizes provider response;
- no frontend token.

### Forged webhook
Control:
- max Content-Length/request-body cap;
- exact raw bytes HMAC;
- constant-time compare;
- parse only after verify.
If reverse proxy decompresses/transforms bodies, QA must verify signature uses the servlet-visible exact provider bytes.

### Duplicate/out-of-order status
Control:
- unique event key;
- runtime unique provider message;
- monotonic status transition.
READ cannot become SENT due to late delivery.

### Webhook unknown phone
Control:
- no tenant creation;
- no Inbox mutation;
- bounded ignored/dead event with metric.

### Provider timeout during outbound send
Control:
- clientRequestId reserves local logical send;
- timeout after possible submission -> UNKNOWN;
- no automatic network replay.
Ops reconciliation/manual user action resolves it.

### Malicious template variables
Control:
- server maps variables against provider template component count/type;
- length limits;
- no arbitrary provider JSON pass-through from browser.

### Media SSRF
Control:
- Meta media URL comes from authenticated Meta media metadata call, not browser;
- validate HTTPS host/provider constraints where practical;
- do not accept arbitrary inbound URL from webhook as download target;
- byte/time limits;
- private storage.

### JSON complexity
Control:
- request body byte limit;
- Jackson depth/constraints if available;
- event extraction loops have bounded entry/change/message/status counts per callback;
- oversized callbacks fail before durable payload retention.

## Provider exception contract

Internal exception fields:
- operation
- httpStatus
- metaType
- metaCode
- metaSubcode
- fbtraceId
- safeCategory
- retryable
- ambiguousOutcome (send only)

Public mapping never includes raw provider message by default.

## Required QA abuse tests

- complete with foreign company state;
- state replay after successful consume;
- two parallel completes using same state;
- tampered phone ID not granted by token;
- same phone connected by two companies concurrently;
- expired/revoked token;
- log scan for token/code/PIN;
- invalid/malformed/uppercase signature variations;
- body over hard limit;
- deeply nested signed JSON;
- duplicate message webhook;
- duplicate status webhook;
- status reordering;
- unknown phone/account;
- outbound foreign conversation;
- outbound foreign media/template;
- free-form outside customer-service window;
- provider send timeout after request dispatch;
- inbound media oversized/wrong MIME;
- disconnect/reconnect race.

## Defaults

Suggested configurable defaults, to be finalized in code/config:
- signup TTL: 10 minutes
- webhook max body: 1 MiB unless real provider payload testing requires a higher bounded value
- webhook processed retention: 7 days
- dead event retention: 30 days
- template cache max age for send: 15 minutes
- health cache target: 15 minutes
- provider connect timeout: 3 seconds
- provider read timeout: 10 seconds

Provider media byte limits must be the minimum of Meta's current documented limit and local MediaValidationProperties.
