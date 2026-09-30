# Contacts & Audience Research

Research date: 2026-09-30  
Repository baseline inspected: `dev @ 88af16fe1cd6966730c26447119416104039e6bb`

This document records architecture-relevant research. It is not a claim that UnifyDesk should clone any provider.

## 1. Existing UnifyDesk infrastructure

### Auth / tenant authorization

`CompanyAuthorizationService` already provides active-workspace checks and string-key IAM permission enforcement.

`CompanyPermissionKeys` is the canonical permission catalog. Contacts must extend it rather than introduce a parallel ACL subsystem.

### Billing / entitlements

`EntitlementService` provides generic boolean/integer/decimal plan values and limit assertions.

The system entitlement catalog already contains:

- `CONTACT_LIMIT`
- `SEGMENT_LIMIT`
- `STORAGE_GB`

Round 1 reuses these. Additional limits are added only where they protect an expensive resource, for example custom-field definitions, lists or per-job import/export rows.

### Storage

`StorageUsageType` already contains:

- `CONTACT_DATA`
- `EXPORT`

`StorageQuotaService` provides tenant locking, idempotent reserve/consume/release semantics.

The existing Media subsystem currently validates image/PDF media only. CSV contact files therefore should not be forced through the user-facing Media abstraction in Round 1. B2 should reuse the underlying private storage provider + storage-accounting primitives through a Contacts-owned file adapter, without creating a new quota system.

### Async jobs / realtime

`AsyncJobService` already provides:

- company/user/resource scoped jobs;
- queued/running/completed/failed states;
- progress;
- stale worker reclamation;
- realtime `async.job.progress` hints.

Contacts bulk work extends `AsyncJobType` and uses the existing service.

### Broadcast

Current Broadcast has channel-specific `broadcast_groups` and destination rows. A send snapshots group members into immutable `broadcast_recipients`.

Broadcast also has a separate `broadcast_suppressions` table, currently used for email hard-bounce suppression.

Decision: Contacts becomes the canonical reusable contact/audience and eligibility layer. Existing Broadcast groups remain backward compatible during Round 1. Integration is via an adapter/snapshot seam, not an immediate destructive migration of old Broadcast data.

### Inbox

`InboxParticipant` currently stores address + display name per conversation.

Decision: Inbox does not get a hard FK to Contacts. A resolver maps normalized Inbox participant identities to a contact in the same company. Unknown senders are not automatically converted into contacts in Round 1.

### Ad Center

`ad_audiences` is provider/account-specific and stores remote audience IDs, provider subtype, delivery/operation status, rule/lookalike metadata and customer-file source.

Decision: `ad_audiences` remains a provider projection. It must never become the canonical Contacts list/segment table.

### Lifecycle

Company/user deletion uses bounded purge jobs. Contacts must plug company purge into the existing lifecycle flow; per-contact deletion gets its own domain soft-delete/restore/purge semantics.

## 2. Market/product research

### HubSpot

Useful patterns:

- CRM contacts are records with standard/custom properties.
- APIs support batch operations and upsert by email or configured unique identifiers.
- Imports map file columns to CRM properties and distinguish create/update/upsert behavior.
- Modern HubSpot APIs are moving to date-based versions.

Architecture takeaways:

- stable internal contact ID;
- explicit unique identifier rules;
- typed property definitions;
- bulk operations must be first-class and error-reportable;
- do not bind UnifyDesk to provider-specific email uniqueness.

Sources:
- https://developers.hubspot.com/changelog/september-2024-rollup
- https://developers.hubspot.com/move-data
- https://developers.hubspot.com/changelog/new-and-improved-apis-fall-spotlight-2025

### Mailchimp

Useful patterns:

- contact marketing status is distinct from merely having a stored contact;
- statuses include subscribed, unsubscribed, non-subscribed, cleaned and pending;
- bounced/cleaned is semantically different from user unsubscribe;
- segments can filter contacts by contact/marketing attributes.

Architecture takeaways:

- do not infer sendability from existence/list membership;
- distinguish opt-out from technical suppression/bounce;
- expose contact/audience count separately from sendable estimated audience.

Sources:
- https://mailchimp.com/help/about-your-contacts/
- https://mailchimp.com/developer/marketing/guides/create-your-first-audience/
- https://mailchimp.com/developer/marketing/api/

### Brevo

Useful patterns:

- a contact may have multiple identifiers (email, SMS, external ID);
- contact creation can include list membership and typed attributes;
- identifier conflicts can be rejected or explicitly merged;
- channel blacklist state is separate from profile fields.

Architecture takeaways:

- multiple identity model;
- deterministic conflict handling;
- explicit merge operation;
- channel eligibility stored separately from profile attributes.

Source:
- https://developers.brevo.com/reference/create-contact

### Klaviyo

Useful patterns:

- lists are static while segments are condition-driven/dynamic;
- adding a profile to a list is distinct from subscribing the profile;
- consent status and suppression are separate concepts;
- campaign estimated audience can be lower than list/segment count after consent/suppression rules.

Architecture takeaways:

- static list and dynamic segment are separate domain objects;
- membership never implies consent;
- central audience resolution returns both raw match count and eligible recipient count.

Sources:
- https://help.klaviyo.com/hc/en-us/articles/115005061447
- https://developers.klaviyo.com/en/v2025-07-15/docs/collect_email_and_sms_consent_via_api
- https://developers.klaviyo.com/en/v2025-07-15/reference/add_profiles_to_list
- https://help.klaviyo.com/hc/en-us/articles/51721301082011

### Customer.io

Useful patterns:

- a person has identifiers, attributes and events;
- segments are groups derived from attributes/behaviors and can be campaign audiences;
- subscription preferences can be channel/topic specific;
- activity is useful on the profile, but raw event history should not be copied blindly.

Architecture takeaways:

- keep profile/identity separate from activity;
- implement a bounded segment predicate language;
- leave topic/preference-center expansion for a later round while making the consent schema extensible.

Sources:
- https://docs.customer.io/messaging/people/manage/overview/
- https://docs.customer.io/get-started/add-people/
- https://docs.customer.io/get-started/segments-and-people-data/
- https://docs.customer.io/messaging/channels/subscriptions/manage-subscription-preferences/

### Twilio Segment / Unify

Useful patterns:

- one unified profile can have multiple external identifiers;
- matching is deterministic; profile merge protection exists to prevent incorrect merges;
- identity rules include blocked values, identifier limits and priorities;
- unified profiles feed downstream audiences/activation.

Architecture takeaways:

- Round 1 matching remains deterministic;
- no fuzzy/name-based auto-merge;
- multiple identities are bounded;
- future CDP/event identity resolution can extend this model rather than replace it.

Sources:
- https://www.twilio.com/docs/segment/unify
- https://www.twilio.com/docs/segment/unify/identity-resolution/identity-resolution-settings
- https://www.twilio.com/docs/segment/unify/faqs

### Google Customer Match

Current platform note:

As of 2026-04-01, most new Customer Match API integrations cannot start with the legacy `OfflineUserDataJobService`/`UserDataService`; Google directs new integrations to the Data Manager API.

Architecture takeaways:

- Contacts owns first-party identity/consent state, not Google API mechanics;
- future activation uses an Ad Center adapter with versioned provider implementation;
- provider upload identifiers are normalized/hash-transformed only at the activation boundary;
- never store a provider-upload hash as the canonical identity.

Sources:
- https://developers.google.com/google-ads/api/docs/remarketing/audience-segments/customer-match/get-started
- https://developers.google.com/google-ads/api/docs/remarketing/audience-segments/customer-match/manage

### Meta customer-list Custom Audiences

Meta's customer-list terms state that advertisers need necessary rights/permissions/lawful basis and must honor opt-outs, including removing people after they opt out. Meta describes locally hashed customer-list data such as email/phone for matching.

Architecture takeaways:

- activation is future Ad Center work;
- only centrally eligible contacts may be exported to the provider adapter;
- opt-out/suppression changes must be able to generate provider removals in a future sync;
- no provider-specific audience fields are added to the core Contact schema.

Source:
- https://www.facebook.com/legal/terms/customaudience/update

Official Meta developer pages were not reliably retrievable during this research run, so no unstable Graph endpoint/version is frozen in Contacts Round 1.

## 3. India / compliance context

TRAI's sender guidance states that bulk senders using telecom resources are subject to TCCCPR requirements including Principal Entity registration, header, content template, transmission identifiers and consent-template/acquisition requirements where applicable.

TRAI describes consent as voluntary permission tied to a specific purpose/product/service and emphasizes robust/verifiable records.

The TCCCPR Third Amendment was released 2026-09-18, so Contacts must store evidence and auditable preference changes without pretending that one static application rule captures all provider/jurisdiction requirements.

MeitY published the Digital Personal Data Protection Rules, 2025 and an enforcement timeline on 2025-11-14.

Architecture decision:

- Contacts stores consent source, state, purpose, timestamp and provenance.
- Provider/channel modules continue enforcing provider-specific registration/template rules.
- UnifyDesk exposes policy inputs and audit records; it does not claim to replace customer legal review.

Sources:
- https://www.trai.gov.in/advice-to-senders
- https://www.trai.gov.in/manage-your-consent
- https://www.trai.gov.in/release-publication/regulations
- https://www.meity.gov.in/documents/act-and-policies/digital-personal-data-protection-rules-2025-gDOxUjMtQWa

## 4. Research conclusions for V1

Adopt:

- PostgreSQL canonical profile/identity model;
- deterministic matching;
- static lists + predicate-driven dynamic segments;
- typed custom-field definitions + bounded JSONB values;
- separate consent and suppression;
- immutable consent audit;
- CSV streaming imports with explicit duplicate strategy;
- async bulk jobs;
- immutable audience snapshots at send preparation;
- adapter boundaries for Inbox/Broadcast/Ads.

Do not adopt in V1:

- Elasticsearch;
- probabilistic identity resolution;
- full event-CDP ingestion;
- XLSX parser;
- provider-specific ad audience schema in Contacts;
- one giant EAV contact-property table;
- live segment evaluation inside delivery loops.
