# Contacts & Audience Research

Research date: 2026-09-30

## What was reviewed

The design was compared against current public documentation and patterns from HubSpot, Mailchimp, Brevo, Klaviyo, Customer.io, Twilio Segment, Google Customer Match, Meta audience concepts, and Indian consent/telecom guidance.

Official references used during architecture work include:
- HubSpot CRM contacts and unique/custom properties: https://developers.hubspot.com/
- Mailchimp Audiences API: https://mailchimp.com/developer/marketing/docs/audiences-introduction/
- Klaviyo Profiles/subscriptions/suppressions: https://developers.klaviyo.com/
- Customer.io people and data-driven segments: https://docs.customer.io/
- Twilio Segment identity/audience documentation: https://segment.com/docs/
- Google Customer Match / Data Manager API: https://developers.google.com/google-ads/api/docs/remarketing/audience-segments/customer-match/get-started and https://developers.google.com/data-manager/api
- TRAI TCCCPR guidance: https://www.trai.gov.in/tcccpr and https://www.trai.gov.in/advice-to-senders
- India DPDP materials: https://www.meity.gov.in/

## Findings adopted

### Separate person/profile state from channel state

Modern systems increasingly distinguish a contact/profile from whether that profile can receive marketing on a particular channel. UnifyDesk therefore does not put a single subscribed boolean on contacts.

### Identity must not equal contact

Email-only uniqueness is a dead end for Inbox, SMS, WhatsApp and Ads. A Contact owns multiple normalized identities. This also supports external IDs later without making provider payloads part of the core schema.

### Static and dynamic audiences are different products

A static event-attendee/customer list needs explicit membership. A dynamic audience should be an expression evaluated against current data. UnifyDesk names these Lists and Segments respectively rather than overloading one table.

### Dynamic filtering should be represented as a structured expression

A versioned, bounded filter AST gives the UI a stable contract and lets the backend compile safe parameterized SQL. Raw SQL, arbitrary scripts and application-memory filtering are rejected.

### Consent and suppression are distinct

Consent records describe the communication preference/evidence. Suppression is a delivery prohibition that can override consent, for example complaint, hard bounce, provider block or manual suppression.

### Bulk data work must be asynchronous

Imports, exports, provider audience sync and large bulk changes are naturally job-oriented. UnifyDesk already has AsyncJob + realtime progress and should extend it.

### Ad activation belongs at an adapter boundary

Google Customer Match currently uses first-party identifiers, consent metadata, provider-specific normalization/hashing and asynchronous/bulk ingestion. Google also changed the preferred integration path for most new Customer Match integrations in 2026. Therefore hashes and provider audience IDs are not canonical Contact fields; future Ad Center adapters derive them at sync time.

### Legal facts are not the same as storage facts

TRAI rules and provider policies can change. The core must retain auditable source/timestamp/purpose evidence and suppression state, while policy adapters decide eligibility. This allows India-specific requirements without contaminating generic Contact entities with DLT/MSG91/Meta concepts.

## Deliberately not copied

- HubSpot-style full CRM deals/pipelines/activities
- CDP-scale event ingestion and identity graph automation
- provider-specific audience objects in Contacts
- arbitrary user SQL
- AI segmentation
- fuzzy automatic merging
- journey automation
- real-time behavioral event lake

These can be added later without changing the V1 Contact identity, consent or audience contracts.
