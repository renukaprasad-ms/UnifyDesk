-- Managed SMS Round-2 runtime security hardening.
-- Prove existing tuple consistency before adding composite ownership constraints.

alter table managed_sms_provider_events
  add column provider_event_at timestamp(6) with time zone;

alter table managed_sms_provider_snapshots
  add column observation_source varchar(80);

do $$
begin
  if exists (
    select 1
      from managed_sms_correlations c
      left join broadcast_recipients r
        on r.company_id = c.company_id
       and r.run_id = c.run_id
       and r.id = c.recipient_id
     where r.id is null
  ) then
    raise exception 'V66 managed_sms_correlations contains inconsistent company/run/recipient tuples';
  end if;

  if exists (
    select 1
      from managed_sms_usage_reservations u
      left join broadcast_recipients r
        on r.company_id = u.company_id
       and r.run_id = u.run_id
       and r.id = u.recipient_id
     where r.id is null
  ) then
    raise exception 'V66 managed_sms_usage_reservations contains inconsistent company/run/recipient tuples';
  end if;

  if exists (
    select 1
      from managed_sms_recipient_delivery d
      left join broadcast_recipients r
        on r.company_id = d.company_id
       and r.id = d.recipient_id
     where r.id is null
  ) then
    raise exception 'V66 managed_sms_recipient_delivery contains inconsistent company/recipient tuples';
  end if;
end $$;

create unique index if not exists uk_broadcast_recipients_company_run_id
  on broadcast_recipients(company_id, run_id, id);

create unique index if not exists uk_broadcast_recipients_company_id
  on broadcast_recipients(company_id, id);

alter table managed_sms_correlations
  add constraint fk_managed_sms_correlation_recipient_tuple
  foreign key (company_id, run_id, recipient_id)
  references broadcast_recipients(company_id, run_id, id);

alter table managed_sms_usage_reservations
  add constraint fk_managed_sms_usage_recipient_tuple
  foreign key (company_id, run_id, recipient_id)
  references broadcast_recipients(company_id, run_id, id);

alter table managed_sms_recipient_delivery
  add constraint fk_managed_sms_delivery_recipient_tuple
  foreign key (company_id, recipient_id)
  references broadcast_recipients(company_id, id);

create index if not exists idx_managed_sms_provider_events_provider_event_at
  on managed_sms_provider_events(provider_event_at desc)
  where provider_event_at is not null;
