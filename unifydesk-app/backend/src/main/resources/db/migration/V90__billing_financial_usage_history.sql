-- UnifyDesk Billing R1 B2: immutable Managed-Service usage ownership and pricing history.

alter table usage_events
  add column service_ownership varchar(32),
  add column billing_sku varchar(64),
  add column subscription_id_snapshot bigint,
  add column billing_period_id bigint,
  add column provider_reference varchar(255);

-- Existing canonical MANAGED_SMS rows were produced only by the MESSAGING_MANAGED runtime.
update usage_events
set service_ownership = case
  when source_type = 'MANAGED_SMS' then 'MANAGED'
  else 'CUSTOMER_OWNED'
end
where service_ownership is null;

update usage_events
set billing_sku = 'MANAGED_SMS_SEGMENT',
    unit = 'SEGMENT'
where service_ownership = 'MANAGED'
  and source_type = 'MANAGED_SMS'
  and billing_sku is null;

alter table usage_events alter column service_ownership set not null;

alter table usage_events
  add constraint ck_usage_events_service_ownership
    check (service_ownership in ('MANAGED', 'CUSTOMER_OWNED')),
  add constraint ck_usage_events_managed_sku
    check (
      (service_ownership = 'CUSTOMER_OWNED' and billing_sku is null)
      or (service_ownership = 'MANAGED' and billing_sku is not null)
    ),
  add constraint fk_usage_events_billing_period
    foreign key (billing_period_id) references billing_periods(id);

create index idx_usage_events_financial_period
  on usage_events (company_id, service_ownership, billing_period_id, occurred_at);

alter table company_rate_overrides
  add column supersedes_override_id bigint,
  add column superseded_at timestamp(6) with time zone,
  add column retention_until timestamp(6) with time zone
    default (now() + interval '8 years'),
  add column legal_hold boolean not null default false;

update company_rate_overrides
set retention_until = coalesce(retention_until, created_at + interval '8 years');

alter table company_rate_overrides alter column retention_until set not null;

alter table company_rate_overrides
  add constraint fk_company_rate_overrides_supersedes
    foreign key (supersedes_override_id) references company_rate_overrides(id);

create index idx_company_rate_overrides_history
  on company_rate_overrides (company_id, effective_from desc, superseded_at);

create table usage_invoice_allocations (
  usage_event_id bigint primary key,
  invoice_id bigint not null,
  billing_period_id bigint not null,
  allocated_at timestamp(6) with time zone not null,
  retention_until timestamp(6) with time zone not null default (now() + interval '8 years'),
  legal_hold boolean not null default false,
  constraint fk_usage_invoice_allocations_usage
    foreign key (usage_event_id) references usage_events(id),
  constraint fk_usage_invoice_allocations_invoice
    foreign key (invoice_id) references invoices(id),
  constraint fk_usage_invoice_allocations_period
    foreign key (billing_period_id) references billing_periods(id)
);

create index idx_usage_invoice_allocations_invoice
  on usage_invoice_allocations (invoice_id);
