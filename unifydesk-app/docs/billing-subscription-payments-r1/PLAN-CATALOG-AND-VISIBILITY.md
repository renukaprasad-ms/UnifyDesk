# Plan Catalog and Visibility

## Plan version model

Do **not** add a separate `PlanVersion` table in R1. Existing `Plan` already has unique `(code, version)`, and `PlanPrice`, `PlanEntitlement`, and `RateCard` are plan-row scoped.

Canonical interpretation:

- `Plan.code` = plan family identifier;
- each `Plan` row = immutable commercial version;
- DRAFT may be edited;
- PUBLISHED is frozen;
- RETIRED remains queryable for historical subscriptions but is not newly selectable.

Required remediation: published entitlements must become immutable exactly like prices/rate cards.

## Visibility

Add a visibility field:

- `PUBLIC` — discoverable to eligible companies.
- `PRIVATE` — discoverable/selectable only with active assignment.
- `INTERNAL` — recommended reserved scope for development/migration/support/admin-only subscriptions.

INTERNAL is justified because current bootstrap/admin flows need a non-customer catalog scope and using PRIVATE assignments for staff/migration plans would conflate commercial contracts with operational plans.

Customer-facing discovery must always exclude INTERNAL.

## PlanCompanyAssignment

Recommended relation:

```
plan_company_assignments
- id
- plan_id
- company_id
- assigned_at
- assigned_by_user_id
- effective_from
- effective_to
- revoked_at
- revoked_by_user_id
- reason
unique active(plan_id, company_id)
```

Assignment is to the immutable plan version (`plan_id`), not a raw company id column on `Plan`.

Benefits:

- one private plan version can be assigned to multiple companies;
- eligibility has an audit trail;
- guessed IDs fail backend authorization;
- a new plan version is not accidentally exposed to prior assignees unless explicitly assigned/cloned.

## Discovery contract

Company-visible catalog =

```
PUBLISHED PUBLIC plans
UNION
PUBLISHED PRIVATE plans with active PlanCompanyAssignment(companyId)
```

Never return INTERNAL plans and never return another company's PRIVATE plan.

The same eligibility predicate must be rechecked during subscription creation; frontend hiding is insufficient.

## Assignment removal

Removing eligibility prevents new selection. It must not silently invalidate an already-active subscription. Existing subscription remains valid until an explicit plan-change/cancellation policy acts.

## Historical stability

A published plan version must not be edited in any financially or entitlement-material way. Create a new version and schedule/apply a subscription change instead.

Legacy mutable display metadata should also be treated cautiously: invoice line descriptions and all financial numbers must be snapshotted at finalization.
