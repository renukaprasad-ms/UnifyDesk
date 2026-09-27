# Notification System Security Architecture

Status: frozen Round-1 security requirements  
Threat-model date: 2026-09-27

## 1. Assets and trust boundaries

Sensitive assets:

- user notification history/read state;
- company-scoped metadata/deep links;
- user email destinations;
- Web Push endpoints and p256dh/auth secrets;
- VAPID private key;
- preference/quiet-hour settings;
- delivery operational metadata.

Untrusted:

- browser-supplied company IDs;
- notification/subscription IDs;
- cursor strings;
- browser PushSubscription payload before validation;
- service-worker message payload before route validation;
- product/module strings unless created by the internal registry;
- broker duplicates/order;
- provider HTTP/SMTP error text.

Trusted only after validation:

- current authenticated principal;
- company authorization result;
- server registry event definition;
- rows loaded with user/company ownership predicates;
- decrypted push subscription after current ownership check.

## 2. Tenant isolation invariant

Personal API flow:

```text
authenticated current user
 -> optional requested company authorization
 -> notification query constrained by user_id
 -> company row constrained by requested company scope
 -> required permission recheck
 -> response/mutation
```

Never:

- accept a target `userId` from personal API;
- fetch notification by global ID and then expose data before ownership check;
- use frontend-selected workspace as proof of access;
- allow a company A caller to mark company B notification read;
- deliver an old company notification after membership/permission revocation.

Cross-company substitution tests are release blockers.

## 3. Account vs company scope

Account events may contain only account-safe content.

Company event visibility requires:

- current active membership or platform behavior explicitly covered by existing company authorization;
- required permission when stored.

Platform admins must not accidentally receive every customer's notification just because they can administratively inspect a workspace. Round-1 product-recipient fanout is membership based; platform-admin elevated access is not a notification subscription.

## 4. Event injection prevention

There is no public arbitrary-notification creation API.

Only internal code can call the recorder with a registry-known event type.

Registry validates:

- exact event type/version;
- recipient selector allowed for that type;
- subject ownership;
- variable names;
- variable types/length;
- group-key construction;
- action route.

Business modules do not supply rendered HTML, arbitrary titles or arbitrary URLs.

## 5. XSS and content safety

In-app:

- title/body are plain text;
- React renders them as text;
- no `dangerouslySetInnerHTML` for notification content.

Email:

- all variable values escaped;
- HTML structure is code-owned;
- text alternative always produced.

Push:

- OS title/body is generic, bounded plain text;
- no HTML.

Deep links:

- relative path beginning with one `/`;
- reject `//`, backslash, URL scheme, host, control characters;
- route builder is registry-owned.

## 6. Push subscription capability secrecy

A Push endpoint is a capability URL and p256dh/auth are secret key material.

Server storage:

- SHA-256 exact endpoint for lookup/unique ownership;
- encrypt endpoint, p256dh and auth independently or as one authenticated encrypted structure with existing AES-256-GCM `CredentialCipher`;
- VAPID private key only in deployment secret/config, never database/API/log;
- public VAPID key may be returned.

Logging:

- never raw endpoint;
- never p256dh/auth;
- at most subscription ID + endpoint hash prefix for diagnostics.

API list never returns encrypted/raw values.

## 7. Shared-browser/account switching

Threat: user A logs out of a shared browser; user B logs in while the same PushSubscription endpoint remains registered.

Rules:

- endpoint hash cannot be silently rebound across users;
- B's registration receives 409;
- F1 unsubscribes the local browser subscription and creates a new subscription for B;
- logout performs best-effort backend revoke + browser unsubscribe before local auth state is cleared;
- push send rechecks current subscription owner.

This prevents cross-account OS notifications on shared machines.

## 8. Web Push CSRF/auth

Subscription create/delete are authenticated state-changing endpoints and use normal CSRF protection.

Do not add them to SecurityConfig's CSRF ignore list.

The browser push service callback is outbound-only in Round 1; no anonymous web-push webhook is introduced.

## 9. Service worker security

- service worker file is served only by the UnifyDesk origin and scoped intentionally;
- only same-origin open paths are honored;
- notificationclick must not eval/execute payload data;
- service-worker messages are treated as hints; page still fetches authorized notification from API;
- no access/refresh token is stored in push payload or service worker persistence;
- if a notification ID is inaccessible, page fails closed.

## 10. Email privacy

Destination:

- resolved from current user server-side;
- never accepted from event payload for product notifications.

Content:

- inbox digests contain aggregate counts only;
- no inbound subject/body/snippet/sender address in Round 1 notifications;
- security email subject must avoid disclosing sensitive action details on lock screens/mail previews where practical.

URLs:

- generated from configured trusted frontend base URL;
- no event-supplied host.

## 11. Preference abuse/mandatory mail

A client cannot make a non-mandatory type mandatory or vice versa.

Registry is authoritative.

Attempts to disable a mandatory security/account email are rejected/ignored safely and effective GET contract reports `locked=true`.

“Mandatory” does not mean bypass:

- user/account validity checks;
- destination validity;
- anti-abuse rate controls for source event creation;
- provider suppression/legal constraints.

## 12. Notification flood/DoS controls

Controls:

- deterministic event keys;
- closed registry;
- input size limits;
- source-specific coalescing;
- bounded fanout batches;
- per-user optional email rolling cap;
- max active push subscriptions;
- bounded list page size;
- bounded seen batch size;
- worker claim limits;
- delivery retry limits.

Metrics/alerts detect sudden source-type volume anomalies.

A source flood should primarily increase durable internal rows; it must not multiply into unlimited email.

## 13. Cursor security

Cursor:

- base64url opaque versioned JSON is acceptable only if it contains non-sensitive seek values and strict parsing;
- preferably sign/HMAC if future cursor includes filters/authorization-sensitive claims;
- client cannot change company/user scope through cursor; server binds query to request/auth regardless.

Malformed cursor => 400 without parser stack trace.

## 14. Concurrency/security race

Membership or permission can change after event materialization.

Therefore:

- list/get rechecks current visibility;
- B2 rechecks before external delivery;
- read mutation constrains current user/company visibility;
- push click fetches again before navigation.

A stale notification row never grants access to its target resource.

## 15. Database ownership predicates

Repository/JDBC methods must prefer predicates such as:

```text
where n.id=:notificationId
  and n.user_id=:currentUserId
  and (
      n.company_id is null
      or n.company_id=:authorizedCompanyId
  )
```

Do not use `findById` followed by an application-level user comparison for externally addressed notification resources.

Push subscription mutation similarly uses `id + user_id`.

## 16. SQL/JSON safety

- parameterized JDBC only;
- no dynamic SQL fragments from notification type/group/user input;
- JSON payload parsed into typed/validated registry variables;
- reject unknown fields where security relevant;
- do not deserialize arbitrary polymorphic Java types from event JSON.

## 17. Kafka security/replay

Existing Kafka authentication/security configuration is reused.

Kafka payload is not authorization authority. B1 reloads event from DB and revalidates state.

Duplicate/replayed records cannot duplicate effect because of:

- event recipient unique key;
- delivery intent unique key.

Kafka DLT/log payload handling must not expose event variable PII.

## 18. Redis/realtime security

Realtime WebSocket already authenticates principal.

Notification realtime send target is always derived from materialized `user_id`, never a client-supplied socket/user target.

Realtime payload contains IDs/counts only, not sensitive bodies.

Redis pub/sub loss or spoof-resistant network configuration remains deployment responsibility; durable API authorization is still authoritative.

## 19. Encryption/configuration secrets

VAPID private key and encryption master key:

- deployment secret only;
- never committed to `.env.example` as a real value;
- fail closed for push registration/sending when missing;
- do not auto-generate a new VAPID key at every startup, because subscriptions are tied to the application-server key.

Public key may be configured/derived and returned to F1.

## 20. Email header injection

Subject/from/display values must not contain CR/LF injection.

Use `MimeMessageHelper` APIs, normalized address objects and fixed platform from address.

Workspace names are content, not raw headers; strip/reject control characters where inserted into subject and bound length.

## 21. Data deletion/retention

Notification tables contain derived user/company data.

L1 integration must align FKs/purge logic with existing deletion lifecycle.

At minimum:

- deleted user cannot continue receiving email/push;
- all active push subscriptions are revoked/purged on user deletion;
- deleted/purged company stops company delivery;
- retained operational rows must not contain raw push secrets beyond retention need.

## 22. Logging/observability privacy

Structured fields allowed:

- notificationType;
- eventId;
- intentId;
- notificationId;
- companyId/userId where existing internal logging policy permits;
- channel;
- outcome/errorCode;
- latency;
- attempt number.

Never log:

- email body;
- inbox subject/body/snippet;
- Push endpoint/key;
- VAPID private key;
- auth cookie/token;
- full SMTP/provider raw response.

## 23. Security tests required

- cross-company list/get/read/seen/read-all substitutions;
- foreign push subscription delete;
- same endpoint cross-account conflict;
- arbitrary event type rejection;
- bad action path rejection;
- XSS-looking workspace name remains escaped in HTML and plain text in UI;
- invalid timezone/cursor/base64 push key;
- permission revoked before B2 send => suppressed;
- membership revoked before list => hidden;
- CSRF required on all mutations;
- push API secrets never returned;
- logs/errors do not contain subscription endpoint/keys;
- source duplicate cannot duplicate in-app row/count/email intent;
- concurrency read/unread counter cannot go negative/double decrement.
