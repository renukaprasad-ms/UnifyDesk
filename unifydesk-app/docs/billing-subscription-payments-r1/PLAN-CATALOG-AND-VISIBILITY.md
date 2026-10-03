# Plan Catalog and Visibility

## Existing version model

Existing `Plan(code, version)` rows are the canonical plan-version model.

Do not add a duplicate PlanVersion table.

Interpretation:

- `code` identifies the plan family;
- each `Plan` row is a commercial version;
- DRAFT may be edited;
- PUBLISHED is frozen;
- RETIRED remains available for history but not new customer selection.

R1 remediation must make PUBLISHED entitlements immutable as well as prices/rate cards.

## Visibility enum

Frozen R1 values:

- `PUBLIC`
- `PRIVATE`
- `INTERNAL`

### PUBLIC

Visible to eligible companies through the normal plan catalog.

### PRIVATE

Visible/selectable only when an active `PlanCompanyAssignment` exists for the requesting company.

Backend selection must re-check the assignment even when a plan ID is submitted directly.

A guessed PRIVATE plan ID must fail closed.

### INTERNAL

Never returned from customer-facing discovery.

May be used by authorized platform operations for staff testing, support, migration or internal commercial handling.

## PlanCompanyAssignment

Canonical relation:

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
```

Assignment targets an immutable plan version.

One PRIVATE plan version may be assigned to multiple companies.

## Discovery predicate

Customer-visible plan catalog:

```
PUBLISHED PUBLIC
UNION
PUBLISHED PRIVATE with active assignment for companyId
```

Never include INTERNAL plans.

## Assignment removal

Removing/revoking a PRIVATE assignment:

- prevents future discovery/selection;
- does not silently cancel or destroy an already-valid active subscription;
- requires a separate explicit subscription lifecycle operation to alter an active subscriber.

## Historical safety

Once PUBLISHED, material commercial terms are immutable:

- platform price;
- entitlements;
- rate cards;
- visibility semantics affecting eligibility.

Changes use a new plan version.
