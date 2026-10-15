-- V111__managed_email_hosted_approval.sql
-- Support custom subdomain request approval workflow for Managed Email hosted domains

alter table managed_email_domains
  add column if not exists requested_subdomain varchar(63),
  add column if not exists rejection_reason varchar(500),
  add column if not exists reviewed_by_user_id bigint,
  add column if not exists reviewed_at timestamp(6) with time zone,
  add column if not exists admin_assigned_alternative boolean not null default false;

create index if not exists idx_managed_email_domains_status_pending
  on managed_email_domains (status)
  where status = 'PENDING_APPROVAL';

do $$
begin
  if not exists (
    select 1 from pg_constraint where conname = 'fk_managed_email_domains_reviewed_by'
  ) then
    alter table managed_email_domains
      add constraint fk_managed_email_domains_reviewed_by
      foreign key (reviewed_by_user_id) references users (id);
  end if;
end $$;
