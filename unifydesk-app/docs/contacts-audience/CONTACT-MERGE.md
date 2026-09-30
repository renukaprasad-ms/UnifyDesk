# Contact Duplicate, Merge and Lifecycle Contract

## 1. Duplicate philosophy

Round 1 only makes deterministic identity decisions.

Exact normalized EMAIL/PHONE overlap is evidence that records refer to the same destination, but import logic still refuses a merge if different identities point to different existing contacts.

Similarity in:

- name;
- organization;
- city/country;
- job title;

is never sufficient for automatic merge.

## 2. Create conflict

A new contact cannot claim an active normalized identity already owned by another active contact in the same company.

API returns 409 with safe conflict metadata:

- identity type;
- masked value where appropriate;
- conflicting contact ID only if caller has contacts.read and the same company.

Never leak foreign-company data.

## 3. Merge preconditions

Source and target:

- same company;
- distinct;
- not purged;
- target ACTIVE/ARCHIVED as allowed;
- source ACTIVE/ARCHIVED;
- expected versions match.

Lock source/target and relevant identities in stable ID order to avoid deadlock.

## 4. Merge field policy

Round-1 explicit policy:

`TARGET_WINS_FILL_NULLS`

For standard scalar fields:

- target non-empty value wins;
- target empty may be filled from source.

Notes:

- do not concatenate arbitrary long notes automatically;
- source note may be recorded in merge summary or copied only if target note empty.

Custom fields:

- target key value wins;
- source fills missing keys;
- type validation still applies.

## 5. Identity move

For each source identity:

- if target already has same normalized identity/type, consolidate references;
- otherwise move ownership to target;
- conflict with a third contact aborts merge.

Primary identity:

- existing target primary wins;
- if target has none, source primary may become primary.

## 6. Tags/lists

Union memberships.

Unique constraints make replay idempotent.

Counts must not double-increment for duplicate membership.

## 7. Consent/suppression

Audit events remain attached to identity ID.

When duplicate identical identities are consolidated, L1/B2 must preserve audit provenance and compute the safest current eligibility.

Safety rule:

- any effective OPTED_OUT outranks OPTED_IN for the same channel/purpose unless a later explicit opt-in event clearly supersedes it;
- active blocking suppression remains active.

Merge itself never produces an opt-in.

## 8. Activity

Source contact activity references may be reassigned to target where safe.

Merge audit remains immutable and records source/target.

Cross-module historical objects such as Broadcast recipients are not rewritten.

## 9. Source tombstone

After successful merge:

- source status MERGED;
- merged_into_contact_id = target;
- normal list/search hides source;
- GET may return merge redirect metadata to authorized callers;
- source cannot be restored independently as an ordinary contact.

## 10. Delete

DELETE produces a tombstone:

- status DELETED;
- deleted_at;
- purge_after.

Default list/search/segments exclude deleted.

New audience snapshots exclude deleted.

Historical snapshots remain unchanged.

## 11. Restore

Restore succeeds only if:

- within retention;
- not MERGED;
- identities do not now conflict with another active contact;
- plan limit permits restored contact.

If conflict exists return CONTACT_RESTORE_CONFLICT and require manual merge/resolution.

## 12. Purge

Purge is asynchronous/bounded.

It removes or anonymizes ordinary profile PII and raw identity values.

Historical campaign delivery rows survive independently.

Consent/suppression safety may retain a one-way fingerprint under a privacy/legal policy to prevent an unsubscribe from being bypassed through delete/re-import.

The exact retention duration must be configurable and product/legal reviewed.

## 13. Company deletion

Contacts company purge integrates with existing company lifecycle and deletes company-scoped Contacts domain rows in bounded batches before the company reaches PURGED.

Avoid relying only on giant ON DELETE CASCADE transactions for million-contact tenants.

## 14. Concurrency tests

QA must test:

- two contacts concurrently claim same email;
- add identity races;
- primary toggles;
- merge A->B concurrent with B->A;
- merge while import upserts source;
- merge while list/tag bulk operation runs;
- restore after identity reused;
- delete while audience snapshot prepares;
- repeated merge request/idempotency.
