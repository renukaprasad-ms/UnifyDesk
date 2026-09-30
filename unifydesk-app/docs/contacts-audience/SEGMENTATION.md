# Dynamic Segmentation Contract

## 1. Goals

Provide expressive reusable audiences without:

- loading all contacts into application memory;
- accepting raw SQL;
- coupling segments to provider APIs;
- creating a materialized-membership table that becomes stale truth.

PostgreSQL remains authoritative.

## 2. AST

Root node is either GROUP or PREDICATE.

### GROUP

```json
{
  "type":"GROUP",
  "operator":"AND",
  "children":[]
}
```

Operators:
- AND
- OR

NOT is represented as a validated group flag only if implementation keeps SQL generation safe; otherwise defer NOT in Round 1.

### PREDICATE

```json
{
  "type":"PREDICATE",
  "field":"countryCode",
  "operator":"EQ",
  "value":"IN"
}
```

No client-provided table/column/operator SQL is accepted.

## 3. Round-1 fields

Standard:

- firstName
- lastName
- displayName
- organizationName
- jobTitle
- countryCode
- timezone
- languageCode
- source
- ownerUserId
- status
- createdAt
- updatedAt
- lastActivityAt

Identity:

- email.exists
- phone.exists
- email.validationState
- phone.validationState

Audience:

- tags
- lists

Preference:

- email.marketingConsent
- sms.marketingConsent
- whatsapp.marketingConsent

Suppression:

- email.suppressed
- sms.suppressed
- whatsapp.suppressed

Custom:

- `custom:<fieldKey>`

Round 1 does not expose arbitrary Inbox/Broadcast/Ads JSON payload fields.

## 4. Operators

String:
- EQ
- NEQ
- CONTAINS
- STARTS_WITH
- IS_EMPTY
- IS_NOT_EMPTY
- IN

Number/date/datetime:
- EQ
- NEQ
- GT
- GTE
- LT
- LTE
- BETWEEN
- IS_EMPTY
- IS_NOT_EMPTY

Boolean:
- EQ

Single-select:
- EQ
- IN

Multi-select:
- CONTAINS_ANY
- CONTAINS_ALL
- IS_EMPTY
- IS_NOT_EMPTY

Tag/list:
- CONTAINS_ANY
- CONTAINS_ALL
- NOT_CONTAINS_ANY where efficiently implementable

Consent:
- EQ one enum value

Suppression:
- EQ boolean

## 5. Complexity limits

Hard safety ceilings:

- max depth: 5
- max total predicates: 50
- max children/group: 20
- max IN/list values/predicate: 100
- max custom-field predicates: 20
- max request JSON: bounded by normal API request limit

Reject before query generation when exceeded.

## 6. SQL compiler

Compiler phases:

1. JSON schema validation.
2. Resolve field descriptor from closed registry.
3. Resolve custom-field definition/type.
4. Validate operator allowed for type.
5. Normalize/validate value.
6. Allocate bind parameters.
7. Produce SQL fragment from server-owned template.
8. Always inject:
   - `c.company_id = :companyId`
   - default `c.status = 'ACTIVE'` unless status predicate is explicitly allowed by endpoint.
9. Keyset member ordering by `c.id`.

No string concatenation of user-supplied identifiers.

## 7. SQL shape guidance

Tags/lists use `exists` semi-joins rather than duplicate-producing joins.

Consent/suppression use indexed `exists/not exists`.

Identity existence uses indexed semi-joins.

Custom JSON predicates use PostgreSQL JSONB operators with parameterized field keys/values through safe functions/templates.

## 8. Counts

A segment's cached count is a hint keyed to `definition_version`.

Rules:

- definition update increments version and invalidates cache;
- a synchronous preview may count when bounded/fast;
- expensive count becomes async;
- stale count is returned with `countFresh=false`;
- stale count is never presented as a frozen send count.

Final audience snapshot count is authoritative for that snapshot.

## 9. Member pagination

Member enumeration:

```sql
where c.company_id = ?
  and c.status = 'ACTIVE'
  and c.id > :afterId
  and <compiled predicate>
order by c.id
limit :limit
```

Cursor may include sort direction/version metadata, signed or opaque encoded by server.

If a segment definition changes while a UI member page is being browsed, the API may require restart using a version mismatch response rather than mixing definitions.

## 10. Index strategy

Base:

- contacts `(company_id,status,id)`
- identities exact lookup + contact/type
- list memberships `(company_id,list_id,contact_id)`
- tag memberships `(company_id,tag_id,contact_id)`
- preferences `(company_id,identity_id,channel,purpose)`
- suppressions `(company_id,identity_id,channel,active)`

Add selective indexes for standard high-value fields after query plans prove benefit.

Avoid "index every possible custom field".

## 11. Behavioral predicates

Deferred in Round 1:

- opened campaign X;
- clicked in last N days;
- received provider event Y;
- Inbox message contains text;
- arbitrary ad engagement.

Round 1 supports `lastActivityAt`.

A later activity-rollup design can add stable derived fields without changing the segment AST framing.

## 12. Security tests

QA must verify:

- SQL injection strings in field/value;
- deep nesting;
- huge IN lists;
- invalid custom field key;
- foreign tag/list IDs;
- foreign owner IDs;
- archived definitions;
- cross-company plan differences;
- expensive OR fanout;
- null/empty semantics;
- timezone/date boundaries;
- cursor tampering;
- stale version pagination.
