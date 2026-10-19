create table if not exists ad_optimization_policies (
    id bigint primary key,
    company_id bigint not null,
    policy_type varchar(32) not null,
    name varchar(255) not null,
    enabled boolean not null default true,
    mode varchar(32) not null,
    provider varchar(24),
    ad_account_id bigint,
    campaign_id bigint,
    config_json text not null,
    status varchar(32) not null default 'ACTIVE',
    last_evaluated_at timestamptz,
    last_triggered_at timestamptz,
    last_result_json text,
    created_by_user_id bigint not null,
    version bigint not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_ad_optimization_policies_company
    on ad_optimization_policies(company_id, policy_type, enabled);

create index if not exists idx_ad_optimization_policies_scope
    on ad_optimization_policies(company_id, ad_account_id, campaign_id);
