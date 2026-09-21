# Email Provider Strategy Architecture

The email channel uses a Strategy Pattern combined with capability-oriented interfaces.

The goal is to keep application behavior provider-neutral while leaving provider-specific protocol
details inside the Gmail, Microsoft, and IMAP/SMTP adapters.

## Design principles

The implementation follows:

- Strategy Pattern for runtime provider dispatch.
- SOLID, especially Interface Segregation, Open/Closed, and Dependency Inversion.
- Single Responsibility for OAuth connection services, provider adapters, queue services, and
  persistence services.
- YAGNI: abstractions are introduced only where providers share real application semantics.
- Fail-fast registration: duplicate provider strategies fail application startup.
- Bounded provider APIs: quotas, token refresh, batching, cursors, retries, and webhook protocol
  parsing stay inside their provider package.

## Runtime flow

For common email operations:

```text
Controller
   |
   v
EmailChannelOperationsService
   |
   +-- validate tenant + EMAIL channel + lifecycle state
   |
   v
ChannelProviderRegistry
   |
   +-- GmailProvider
   +-- MicrosoftProvider
   +-- ImapSmtpProvider
```

The controller does not branch on provider type.

## Capability contracts

`ChannelProvider` is intentionally small and contains only provider identity and health.

Providers opt into additional capabilities:

- `EmailSendProvider`
- `EmailSyncProvider`
- `EmailReadStateProvider`
- `DisconnectableChannelProvider`

A provider is never required to implement an operation it cannot support.

This avoids placeholder methods such as `throw new UnsupportedOperationException()` and avoids
empty webhook implementations.

## Provider-neutral commands and results

The application layer uses:

- `EmailSendCommand`
- `EmailSendResult`
- `EmailSyncRequest`
- `EmailSyncQueuedResult`
- `EmailReadStateMessage`

Provider HTTP DTOs remain at the API edge so existing REST contracts do not change. They adapt to
and from the provider-neutral model.

## Why connect is not one generic method

Gmail and Microsoft connect through OAuth authorization-code flows. IMAP/SMTP connects by testing
mail server credentials and discovering mailbox state.

Those are different protocols and lifecycle state machines. Forcing both into a single
`connect(Object request)` contract would lose type safety and move provider-specific branching into
the abstraction.

Therefore:

- common post-connection behavior uses Strategy interfaces;
- provider-specific connection establishment stays in the provider connection service;
- once a connection exists, common operations use the provider registry.

## Why raw webhooks are not one generic parser

Gmail Pub/Sub and Microsoft Graph change notifications do not carry the same semantics:

- Gmail Pub/Sub notifies that mailbox history advanced;
- Microsoft Graph notifications identify a subscription and trigger delta synchronization;
- IMAP/SMTP currently has no webhook transport.

Both webhook controllers already converge on provider synchronization after authenticating and
validating their provider envelope. A generic raw-webhook interface would add no useful domain
abstraction today.

The rule is: normalize after provider authentication, not before it.

## Complexity

Let:

- `P` = number of configured providers;
- `N` = number of messages in a read-state operation.

Startup:

- provider registry construction: O(P) time and O(P) space.

Request dispatch:

- provider selection: O(1) enum-map lookup;
- capability check: O(1);
- no request-time provider scanning.

Read-state:

- Gmail: O(N) deduplication, then bounded batches of 250;
- Microsoft: O(N) deduplication, then Graph batches of 20;
- IMAP/SMTP: O(N) classification by folder, then bulk flag updates.

Temporary memory is O(N) for provider IDs. There is no O(N²) provider dispatch or message lookup.

Send:

- one channel-connection database lookup occurs in `EmailChannelOperationsService`;
- provider send services receive the already validated connection and do not repeat the lookup;
- provider API calls dominate runtime latency.

## Dependency direction

The channel module must not depend on inbox persistence entities.

```text
inbox
  -> EmailReadStateMessage
  -> EmailReadStateProvider
  -> provider adapter
```

This preserves the modular-monolith boundary and prevents provider code from importing
`InboxMessage`.

## Adding a new email provider

For a new provider:

1. Create the provider-specific API/client and credential logic.
2. Implement `ChannelProvider`.
3. Implement only the capability interfaces the provider supports.
4. Register the implementation as a Spring bean.
5. Keep provider quotas, token handling, retries, cursors, and protocol DTOs in its package.
6. Do not add a provider `switch` to controllers or common orchestration services.
7. Add capability and integration tests.

If a future operation is shared by multiple providers, add a focused capability interface for that
operation. Do not expand `ChannelProvider` into a god interface.
