alter table managed_email_tenants
  add column provider_max_send_rate double precision,
  add column provider_max_24h_send double precision,
  add column provider_production_access_enabled boolean not null default false,
  add constraint uk_managed_email_tenants_id_company unique (id, company_id);

alter table managed_email_domains
  add constraint uk_managed_email_domains_id_company unique (id, company_id);

alter table managed_email_sender_bindings
  add constraint uk_managed_email_sender_bindings_sender_company unique (sender_id, company_id);

alter table managed_email_provider_events
  add constraint fk_managed_email_provider_events_tenant_company
    foreign key (tenant_id, company_id)
    references managed_email_tenants (id, company_id) on delete restrict,
  add constraint fk_managed_email_provider_events_domain_company
    foreign key (domain_id, company_id)
    references managed_email_domains (id, company_id) on delete restrict,
  add constraint fk_managed_email_provider_events_sender_company
    foreign key (sender_id, company_id)
    references managed_email_sender_bindings (sender_id, company_id) on delete restrict;

alter table managed_email_reputation_snapshots
  add constraint fk_managed_email_reputation_tenant_company
    foreign key (tenant_id, company_id)
    references managed_email_tenants (id, company_id) on delete restrict;

create index idx_managed_email_provider_events_tenant_company
  on managed_email_provider_events (tenant_id, company_id)
  where tenant_id is not null;

create index idx_managed_email_provider_events_domain_company
  on managed_email_provider_events (domain_id, company_id)
  where domain_id is not null;
