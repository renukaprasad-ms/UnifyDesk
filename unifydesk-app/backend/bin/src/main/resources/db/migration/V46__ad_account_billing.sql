create table ad_account_billing (
  id bigint primary key,
  company_id bigint not null,
  ad_account_id bigint not null,
  provider varchar(24) not null,
  currency varchar(12),
  balance_minor bigint,
  amount_spent_minor bigint,
  spend_cap_minor bigint,
  min_campaign_group_spend_cap_minor bigint,
  min_daily_budget_minor bigint,
  is_prepay_account boolean,
  is_tax_id_required boolean,
  funding_source_id varchar(255),
  funding_source_type integer,
  funding_source_display varchar(500),
  funding_source_details_json text,
  expired_funding_source_details_json text,
  extended_credit_invoice_group_json text,
  tax_id_status integer,
  tax_id_type varchar(120),
  disable_reason integer,
  business_country_code varchar(8),
  provider_payload_json text,
  last_sync_at timestamp(6) with time zone not null,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_ad_account_billing_company
    foreign key (company_id) references companies (id) on delete cascade,
  constraint fk_ad_account_billing_account
    foreign key (ad_account_id) references ad_accounts (id) on delete cascade,
  constraint uk_ad_account_billing
    unique (ad_account_id, provider)
);

create index idx_ad_account_billing_company
  on ad_account_billing (company_id, provider);
