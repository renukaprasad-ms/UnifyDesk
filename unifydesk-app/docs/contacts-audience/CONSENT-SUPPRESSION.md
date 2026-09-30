# Consent, Preferences, Suppression and Eligibility

## 1. Principle

"Contact exists" is not equivalent to "message may be sent".

"List member" is not equivalent to "subscribed".

"Opted in" is not equivalent to "deliverable".

These are separate states.

## 2. Channels

Round 1:

- EMAIL
- SMS
- WHATSAPP

Phone identity is shared by SMS/WhatsApp but preferences/suppressions are channel-specific.

## 3. Purpose

Round 1 freezes one purpose:

- MARKETING

Schema/API includes purpose so future transactional/service/topic preferences do not require replacing the model.

## 4. Preference states

- UNKNOWN
- OPTED_IN
- OPTED_OUT

Unknown is first-class.

A CSV row containing an email/phone does not automatically produce OPTED_IN.

## 5. Preference sources

Closed enum/code registry, examples:

- MANUAL
- IMPORT
- API
- FORM
- USER_REQUEST
- INBOX
- UNSUBSCRIBE_LINK
- PROVIDER
- MIGRATION
- SYSTEM

Import/API requests cannot impersonate SYSTEM/PROVIDER internal sources.

## 6. Evidence

A consent event may contain bounded evidence metadata, for example:

- capture form ID;
- purpose text/version;
- IP hash/tokenized reference if policy permits;
- imported consent column/source reference;
- provider callback ID;
- external form submission ID.

Do not dump full request bodies.

## 7. Transitions

Public/admin service permits explicit state transitions and always writes:

1. immutable event;
2. current preference projection;

in one transaction.

Repeated same idempotency event has no duplicate effect.

An OPTED_OUT -> OPTED_IN transition is allowed only through an explicit new positive action/source; workers must not auto-resubscribe because a contact was re-imported.

## 8. Suppressions

Suppression reasons:

- HARD_BOUNCE
- COMPLAINT
- INVALID_DESTINATION
- USER_REQUEST
- ADMIN
- PROVIDER
- LEGAL
- OTHER

Sources are audited.

Some suppressions are releasable; provider/permanent reasons may require channel-specific policy.

Core Contacts does not silently release a suppression because consent changed.

## 9. Effective eligibility

Input:

- company
- contact
- channel
- purpose
- optional requested identity

Evaluation order is deterministic and returns one primary reason plus optional details.

Suggested order:

1. contact exists in company
2. contact status ACTIVE
3. select active identity required for channel
4. identity validation state acceptable
5. active blocking suppression?
6. purpose preference state
7. return eligible/common exclusion

For MARKETING:

```text
eligible =
  contact ACTIVE
  AND valid required identity
  AND preference == OPTED_IN
  AND no active blocking suppression
```

Provider modules can further reject.

## 10. Bounce/complaint bridge

Managed Email / Broadcast provider feedback that proves a hard bounce or complaint should call an internal idempotent suppression adapter.

Round-1 integration must not delete existing `broadcast_suppressions` before compatibility is proven.

During transition:

- provider signal -> central contact suppression when identity resolves;
- legacy Broadcast hard-bounce table remains usable;
- Contacts audience snapshots check central suppression;
- L1 documents any dual-write/reconciliation needed before Contacts audiences are enabled for Broadcast.

No AWS SES-specific field appears in contact DTOs.

## 11. SMS

Contacts tracks:

- normalized E.164 identity;
- marketing opt-in/out;
- SMS suppression.

MSG91/DLT:

- PE ID;
- sender/header;
- content templates;
- route/provider policy;

remain Managed SMS/channel concerns.

TRAI compliance evidence may reference consent source/purpose, but generic Contacts does not decide whether a particular campaign satisfies all TCCCPR obligations.

## 12. WhatsApp

Contacts tracks WhatsApp preference/suppression against a PHONE identity.

Meta-specific:

- WABA;
- phone number ID;
- template category/state;
- service window;
- provider quality;

remain WhatsApp module concerns.

## 13. Unknown senders

Inbound Inbox receipt does not imply marketing opt-in.

If future "create contact from Inbox" UX is added, initial MARKETING state remains UNKNOWN unless an explicit consent source is supplied.

## 14. Deleted contacts

Delete does not erase consent/suppression audit immediately if retention/legal safety requires it.

Normal UI/export hides deleted profile PII.

A purge may retain a non-reversible destination fingerprint solely to prevent silent reactivation of a known opt-out/suppression, subject to final privacy policy.

## 15. Auditing

Consent/suppression history is integrity-sensitive.

Requirements:

- append-only through domain service;
- no generic PATCH/DELETE;
- actor derived server-side;
- timestamps distinguish occurred vs recorded;
- idempotency keys for provider/import replay;
- log redaction;
- evidence size cap;
- tenant scope on every lookup.

## 16. QA invariants

QA must prove:

- list membership cannot send without required opt-in;
- re-import cannot clear opt-out;
- merge keeps safest effective eligibility;
- identity replacement does not transfer unrelated consent blindly;
- provider replay is idempotent;
- hard bounce overrides opt-in;
- cross-company same email has independent preference/suppression;
- foreign identity ID cannot be used to write consent;
- opted-out deleted/re-imported behavior follows tombstone policy.
