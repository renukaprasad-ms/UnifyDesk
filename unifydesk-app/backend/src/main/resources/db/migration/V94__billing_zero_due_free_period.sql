-- Billing R1 QA final correction: FREE platform periods are open-ended.
-- Paid MONTHLY/YEARLY periods remain strict half-open [start,end) ranges.

alter table billing_periods
  alter column end_at drop not null;

alter table billing_periods
  drop constraint if exists ck_billing_period_range;

alter table billing_periods
  add constraint ck_billing_period_range
  check (
    (cadence = 'FREE' and end_at is null)
    or
    (cadence <> 'FREE' and end_at is not null and end_at > start_at)
  );

create unique index if not exists uk_billing_period_open_boundary
  on billing_periods(company_id, kind, start_at)
  where end_at is null;
