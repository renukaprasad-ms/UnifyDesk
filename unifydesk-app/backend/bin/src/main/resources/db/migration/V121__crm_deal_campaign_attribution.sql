alter table crm_deals
  add column if not exists attribution_channel varchar(50) default 'DIRECT_SALES',
  add column if not exists broadcast_message_id bigint,
  add column if not exists ad_campaign_id bigint,
  add column if not exists lead_form_id bigint,
  add column if not exists utm_source varchar(100),
  add column if not exists utm_medium varchar(100),
  add column if not exists utm_campaign varchar(150);

do $$
begin
  if not exists (select 1 from pg_constraint where conname = 'fk_crm_deals_broadcast_message') then
    alter table crm_deals add constraint fk_crm_deals_broadcast_message
      foreign key (broadcast_message_id) references broadcast_messages(id) on delete set null;
  end if;

  if not exists (select 1 from pg_constraint where conname = 'fk_crm_deals_ad_campaign') then
    alter table crm_deals add constraint fk_crm_deals_ad_campaign
      foreign key (ad_campaign_id) references ad_campaigns(id) on delete set null;
  end if;

  if not exists (select 1 from pg_constraint where conname = 'fk_crm_deals_lead_form') then
    alter table crm_deals add constraint fk_crm_deals_lead_form
      foreign key (company_id, lead_form_id) references crm_lead_forms(company_id, id) on delete set null;
  end if;
end $$;

create index if not exists idx_crm_deals_attribution
  on crm_deals (company_id, attribution_channel);

create index if not exists idx_crm_deals_broadcast_attr
  on crm_deals (company_id, broadcast_message_id) where broadcast_message_id is not null;

create index if not exists idx_crm_deals_ad_attr
  on crm_deals (company_id, ad_campaign_id) where ad_campaign_id is not null;

create index if not exists idx_crm_deals_form_attr
  on crm_deals (company_id, lead_form_id) where lead_form_id is not null;
