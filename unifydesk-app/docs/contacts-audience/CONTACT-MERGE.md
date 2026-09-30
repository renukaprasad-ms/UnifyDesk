# Contact Merge Contract

## V1 merge is explicit

UnifyDesk never fuzzy-auto-merges two existing Contacts.

Exact identity matching can attach incoming data to one existing Contact. If incoming identities resolve to different Contacts, the operation fails with IDENTITY_CONFLICT and requires an explicit merge.

## Merge request

Required:
- sourceContactId
- targetContactId
- version/If-Match for both records or equivalent optimistic guard
- field conflict policy

Optional explicit per-field choices may override the default.

## Transaction

1. authorize CONTACT_MODIFY for company;
2. load source and target by company_id;
3. lock both rows in ascending ID order;
4. require both ACTIVE and different IDs;
5. recheck versions;
6. validate no identity ownership contradiction;
7. move non-conflicting identities;
8. move tags/list memberships with ON CONFLICT convergence;
9. move custom field values using explicit conflict policy;
10. reconcile current channel preference conservatively;
11. preserve all active suppressions;
12. mark source MERGED -> target;
13. append merge audit/activity;
14. commit.

No provider/network call occurs inside merge.

## Conflict defaults

- target non-null core property wins unless request explicitly chooses source
- identities are unioned when non-conflicting
- tag/list membership is unioned
- custom field target wins unless explicit source choice
- OPTED_OUT wins over OPTED_IN/UNKNOWN for the same channel/identity unless a later explicit consent event is separately recorded
- active suppression always survives
- owner assignment target wins

## History

Do not rewrite historical Broadcast recipients, provider messages, audit events or Inbox history to pretend they originally belonged to the target.

APIs resolving a merged source Contact ID may return a stable MERGED response with targetContactId; mutation of a merged source is rejected.

## Concurrency tests

Required:
- source/target reversed concurrent merges do not deadlock
- duplicate merge replay converges
- contact updated after stale merge read produces conflict
- cross-company source/target is not discoverable
- identity moved concurrently cannot become double-owned
