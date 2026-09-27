# Managed SMS Architecture Decisions

Status: FROZEN for Round 1

## D1 — Extend Broadcast; do not create an SMS campaign engine
Managed SMS adds compliance and managed-provider policy around the existing Broadcast run/outbox/Kafka/batch/recipient/retry/statistics path.

## D2 — Customer remains the Principal Entity
For the normal B2B model, each UnifyDesk customer registers its own business on DLT and owns its PE ID, Headers and Content Templates. UnifyDesk does not impersonate customer brands under a single UnifyDesk PE.

## D3 — Managed provider topology is child-per-company where available
Preferred provider isolation is a MSG91 child/client account per company under a UnifyDesk master/subaccount. A flat shared account is permitted only after provider confirmation and still requires strict local tenant binding.

## D4 — PE–TM chain is a send gate
DLT_PROFILE.ACTIVE alone is insufficient. The configured chain must be verified ACTIVE and not stale/suspended.

## D5 — Managed SMS is exact-template-only
A MESSAGING_MANAGED + MSG91_SMS sender cannot send scratch/raw arbitrary SMS text. A Broadcast draft must reference an ACTIVE managed SMS template. The final body is rendered from the frozen approved template version and declared variables only.

## D6 — Template versions are immutable after activation
Editing content creates a new version/binding. Historical broadcasts retain the exact approved body fingerprint, DLT Template ID, Header binding and ruleset snapshot.

## D7 — Header is a managed BroadcastSender
Customer-visible selection continues through BroadcastSender. For managed SMS, fromAddress stores the approved base Header/Sender ID, while managed_sms_sender_bindings links it to the tenant/profile/header record.

## D8 — One managed connection per company
A BroadcastSenderConnection with provider=MSG91_SMS, managementMode=MESSAGING_MANAGED represents the customer's managed SMS capability. It has no customer credential. Provider account/client references live in managed tables and are never returned.

## D9 — Provider secrets are not stored on customer-visible managed connection
The managed BroadcastSenderConnection.encryptedCredentials remains null. Platform/master and child-account secrets come from a managed provider credential service/secret store. Customer APIs never accept or return AuthKeys.

## D10 — Provider route is internal
Customer selects regulatory message purpose/category through the approved template; customer never enters a numeric MSG91 route in Managed SMS. Provider routing parameters are determined by the binding/provider adapter.

## D11 — Use stable SQL contracts between B1 and B2
B1 owns compliance/control tables and JPA. B2 reads the frozen B1 table contract through a B2-owned JdbcTemplate/runtime repository. B2 does not import B1 Java entities, so both branches compile independently from the same base.

## D12 — B2 is sole writer of Broadcast delivery hot files
B2 owns changes to BroadcastDeliveryService, BroadcastLaunchService/BroadcastMessageService only where necessary, BroadcastSmsCommand and managed MSG91 runtime dispatch. B1 may not edit these files.

## D13 — B1 is sole writer of sender/control-plane hot files
B1 owns creation/lifecycle of the managed BroadcastSenderConnection/BroadcastSender and any narrowly required repository/controller wiring. B2 consumes existing rows/SQL and does not create managed compliance objects.

## D14 — Managed compose uses a separate managed-SMS contract
F1 loads managed SMS templates from /api/companies/{companyId}/managed-sms/templates. For a managed sender, the Broadcast create/update request must include managedSmsTemplateId plus variables and must not submit bodyText as authoritative content.

## D15 — Snapshot managed template binding per run
At launch B2 creates managed_sms_run_bindings keyed by run_id, storing company, template version, DLT Template ID, Header, provider binding/Flow, body fingerprint and compliance ruleset. Delivery never resolves “latest” template for an existing run.

## D16 — Provider acceptance != delivery
Provider HTTP/API acceptance sets recipient ACCEPTED_BY_PROVIDER and commits billable usage once. DLR later projects DELIVERED/FAILED/REJECTED without rewriting the fact that the provider accepted the submission.

## D17 — UNKNOWN is not blindly retried
Once an external request may have reached MSG91, an ambiguous network outcome becomes UNKNOWN. Automated retry requires a provider/idempotency guarantee or later evidence proving no acceptance.

## D18 — Billing boundary is provider acceptance
Default managed-SMS billable unit is provider-accepted SMS segments. Downstream delivery failure does not automatically reverse billing because provider cost may already have been incurred. A future provider credit/refund event may create an explicit adjustment, never mutate the original usage event.

## D19 — Reserve before send; commit/release after outcome
B2 reserves tenant/provider/plan capacity before provider submission. Definite pre-acceptance failure releases. Acceptance commits exactly once. UNKNOWN keeps reservation until reconciliation/expiry policy resolves it.

## D20 — DLR handling is idempotent and trust-gated
Raw callbacks enter a provider-event inbox first. Only trusted, validated, non-duplicate events can mutate recipient delivery projection or usage reconciliation.

## D21 — CRQID is opaque
CRQID contains a compact random/alphanumeric correlation token only. The mapping table owns company/run/recipient/attempt IDs. No phone number or business data is encoded into the callback token.

## D22 — Customer UI hides MSG91
Customer surfaces say “UnifyDesk Managed SMS.” MSG91 appears only in internal admin/ops diagnostics or where provider support specifically requires a provider reference.

## D23 — Compliance status is explicit
Customer states: ACTION_REQUIRED, WAITING_DLT_APPROVAL, WAITING_UNIFYDESK_REVIEW, PROVIDER_SYNCING, READY, REJECTED, SUSPENDED. Internal states may be richer but map deterministically to these values.

## D24 — Archive, do not reuse
PE/Header/Template/provider-binding identifiers referenced by historical runs are soft-retired/versioned. Recreating the same visible Header or DLT Template ID never rebinds an old run.

## D25 — Third Amendment rules are effective-date versioned
The policy engine stores a compliance ruleset identifier/effective date. Newly notified 2026 provisions are not treated as already effective on 2026-09-27.

## D26 — Promotional support is gated by approved opt-out/template semantics
UnifyDesk never appends an opt-out footer to an already approved template. Promotional templates become READY only when the upstream approved template and current rules satisfy the required opt-out/preference model.

## D27 — CTA variables are schema constrained
A runtime URL/callback/email variable must have the correct approved tag/type and match stored CTA policy. Arbitrary URLs in a generic text variable are rejected locally.

## D28 — Production blocker is external, not code
GA is blocked until MSG91/access-provider confirmation covers UnifyDesk's exact TM role/chain, subaccount/multi-PE arrangement, webhook trust mechanism, throughput and credit model.
