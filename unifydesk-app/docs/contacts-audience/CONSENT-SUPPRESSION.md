# Consent, Preferences, Suppression & Eligibility

## Principle

Contact existence never implies permission to send marketing.

A single global subscribed flag is prohibited.

## Preference model

Current preference is scoped by:
- company
- contact
- optional identity
- channel
- purpose

V1 purpose is MARKETING.

States:
- UNKNOWN
- OPTED_IN
- OPTED_OUT

Every mutation writes an immutable preference event with source, timestamp, actor, reason/source reference and idempotency key.

Examples of sources:
- USER_UI
- CONTACT_ACTION
- IMPORT
- API
- INBOX
- MANAGED_EMAIL_EVENT
- MANAGED_SMS_EVENT
- META_WHATSAPP
- SYSTEM_MIGRATION

Importing a contact or adding it to a List does not silently create OPTED_IN consent unless the import request explicitly maps verifiable consent evidence and policy permits it.

## Suppression

Suppression is a harder delivery prohibition and is separate from preference.

Representative reason codes:
- MANUAL
- HARD_BOUNCE
- COMPLAINT
- PROVIDER_SUPPRESSED
- DND
- INVALID_DESTINATION
- ABUSE_POLICY
- LEGAL_POLICY

Suppressed means ineligible even if preference is OPTED_IN.

A provider event cannot lift an unrelated suppression reason. Lifting must address the same suppression source/reason or follow an explicit administrative reconciliation rule.

## Shared eligibility contract

Internal service:

evaluate(companyId, contactId, identityId, channel, purpose, context)
-> decision ELIGIBLE | INELIGIBLE
-> stable reason codes
-> evaluatedAt / policyVersion

Bulk form streams decisions for snapshot workers.

Minimum checks:
- company-scoped active Contact
- active selected identity owned by Contact/company
- channel-compatible normalized identity
- preference/purpose policy
- active suppression
- provider/channel policy adapter
- optional company sending policy

This service is the only approved Contacts-level gate for Broadcast audience sends. Managed Email/SMS/WhatsApp may keep their provider/runtime guards, but those guards do not replace recipient eligibility.

## Channel events

Managed Email:
- unsubscribe -> preference OPTED_OUT
- hard bounce/complaint -> suppression
- delivery/open/click do not imply consent

Managed SMS:
- opt-out/DND/provider suppression -> preference/suppression as mapped by the generic adapter
- DLT/header/template/provider details remain in Managed SMS

WhatsApp:
- opt-in state/source/timestamp can populate preference
- Meta identifiers/provider DTOs remain outside core entities

## India-first policy separation

The raw schema records evidence. A policy adapter evaluates current legal/provider rules. This is necessary because TRAI/DPDP/provider requirements can change independently of Contact storage.

For commercial SMS in India, existing Managed SMS remains responsible for DLT operational requirements. Contacts supplies recipient preference/evidence and generic suppression; it does not duplicate PE/Header/Template registration.

## Deletion protection

Privacy purge removes plaintext identities where required, but an active opt-out/complaint may be retained as a versioned keyed HMAC fingerprint under a dedicated suppression key. A re-import with the same normalized identity reattaches the suppression without revealing the original value from the tombstone.

Key material is not stored in the database. Key version is stored for rotation/reconciliation.
