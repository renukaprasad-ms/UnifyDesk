# Segmentation Contract

## V1 expression

Segments store a JSON AST with version 1.

Node types:
- group: op AND | OR, children
- predicate: field descriptor + operator + typed value

Field kinds:
- BUILTIN_CONTACT
- IDENTITY
- TAG
- LIST
- CUSTOM_FIELD
- CHANNEL_PREFERENCE
- SUPPRESSION
- CREATED_AT
- LAST_ACTIVITY_AT

Representative operators:
- EQ, NE
- IN, NOT_IN
- PRESENT, ABSENT
- CONTAINS for bounded text
- GT, GTE, LT, LTE
- BEFORE, AFTER
- HAS_TAG, IN_LIST
- IS_OPTED_IN, IS_OPTED_OUT, IS_SUPPRESSED

## Safety limits

Round-1 defaults:
- maximum depth: 4
- maximum predicates: 50
- maximum values in one IN list: 100
- maximum serialized filter size: 32 KiB
- no regex
- no user SQL
- no arbitrary functions
- no subquery supplied by client

Limits are configuration, with hard server ceilings.

## Compiler

The compiler:
1. parses a versioned DTO, not arbitrary maps;
2. validates field exists and caller may filter it;
3. validates operator/type compatibility;
4. binds every value as a parameter;
5. injects company_id and active-contact predicate at the root;
6. emits SQL/EXISTS using indexed tables;
7. uses deterministic ordering by contact id for cursor iteration.

The frontend never sends column names or SQL fragments.

## Custom fields

Compiler resolves field ID + type from the company field definition, then emits the typed-value comparison. Archived fields cannot be added to new filters; existing segments using them become ACTION_REQUIRED until edited rather than silently changing meaning.

## Counts and preview

Preview returns a small keyset-paged sample plus an estimated/current count marker.

Exact count can be queued asynchronously for expensive segments. contact_segments.cached_count is advisory and includes count_computed_at/filter_hash. Any filter edit invalidates the cache.

## Snapshot evaluation

Audience snapshot streams matching contact IDs in keyset order. It does not materialize the whole result in Java memory.

For a Broadcast snapshot, the stream is additionally passed through channel identity selection + eligibility and inserted in batches.

## V1 exclusions

- behavior/event-window queries such as opened 3 emails in 7 days
- arbitrary campaign aggregate formulas
- geo-radius
- full-text fuzzy matching
- nested subsegments
- AI-generated segments
- real-time materialized membership

The AST is versioned so these can be introduced without accepting arbitrary SQL.
