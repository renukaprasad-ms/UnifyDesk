-- Billing R1 FINAL-003: one authoritative scheduled downgrade per subscription boundary.
-- Preserve commercial history. Existing duplicate authoritative rows fail deployment for explicit reconciliation.

do $$
begin
  if exists (
    select 1
      from subscription_changes
     where change_type = 'DOWNGRADE'
       and status = 'SCHEDULED'
     group by subscription_id, effective_at
    having count(*) > 1
  ) then
    raise exception using
      errcode = '23505',
      message = 'Cannot enforce authoritative scheduled downgrade invariant: duplicate SCHEDULED DOWNGRADE rows exist for a subscription/effective boundary';
  end if;
end
$$;

create unique index if not exists uk_subscription_changes_scheduled_downgrade_boundary
  on subscription_changes (subscription_id, effective_at)
  where change_type = 'DOWNGRADE'
    and status = 'SCHEDULED';
