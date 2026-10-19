alter table managed_email_tenants
  add column provider_sent_last_24h double precision,
  add column provider_account_sending_enabled boolean not null default false;

alter table broadcast_senders
  add constraint uk_broadcast_senders_id_company unique (id, company_id);

alter table broadcast_runs
  add constraint uk_broadcast_runs_id_company unique (id, company_id);

alter table broadcast_recipients
  add constraint uk_broadcast_recipients_id_run_company unique (id, run_id, company_id);

alter table managed_email_domains
  add constraint fk_me_domains_tenant_company
    foreign key (tenant_id, company_id)
    references managed_email_tenants (id, company_id)
    on delete restrict
    not valid;

alter table managed_email_dns_records
  add constraint fk_me_dns_domain_company
    foreign key (domain_id, company_id)
    references managed_email_domains (id, company_id)
    on delete cascade
    not valid;

alter table managed_email_sender_bindings
  add constraint fk_me_sender_binding_sender_company
    foreign key (sender_id, company_id)
    references broadcast_senders (id, company_id)
    on delete restrict
    not valid,
  add constraint fk_me_sender_binding_domain_company
    foreign key (domain_id, company_id)
    references managed_email_domains (id, company_id)
    on delete restrict
    not valid,
  add constraint fk_me_sender_binding_tenant_company
    foreign key (tenant_id, company_id)
    references managed_email_tenants (id, company_id)
    on delete restrict
    not valid;

alter table managed_email_usage_events
  add constraint fk_me_usage_run_company
    foreign key (run_id, company_id)
    references broadcast_runs (id, company_id)
    on delete restrict
    not valid,
  add constraint fk_me_usage_recipient_run_company
    foreign key (recipient_id, run_id, company_id)
    references broadcast_recipients (id, run_id, company_id)
    on delete restrict
    not valid;

create table managed_email_admin_actions (
  id bigint primary key,
  company_id bigint not null,
  actor_user_id bigint not null,
  action varchar(32) not null,
  reason varchar(500) not null,
  created_at timestamp(6) with time zone not null,
  constraint fk_me_admin_actions_company
    foreign key (company_id) references companies (id) on delete restrict,
  constraint fk_me_admin_actions_actor
    foreign key (actor_user_id) references users (id) on delete restrict
);

create index idx_me_admin_actions_company_created
  on managed_email_admin_actions (company_id, created_at desc);
