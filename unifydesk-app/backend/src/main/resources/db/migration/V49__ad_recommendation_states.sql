create table ad_recommendation_states (
  id bigint primary key,
  company_id bigint not null,
  ad_account_id bigint not null,
  provider varchar(24) not null,
  recommendation_key varchar(255) not null,
  fingerprint varchar(128) not null,
  status varchar(24) not null,
  snoozed_until timestamp(6) with time zone,
  actor_user_id bigint,
  last_seen_at timestamp(6) with time zone not null,
  resolved_at timestamp(6) with time zone,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_ad_recommendation_state_company
    foreign key (company_id) references companies (id) on delete cascade,
  constraint fk_ad_recommendation_state_account
    foreign key (ad_account_id) references ad_accounts (id) on delete cascade,
  constraint uk_ad_recommendation_state
    unique (ad_account_id, provider, recommendation_key)
);

create index idx_ad_recommendation_state_company_status
  on ad_recommendation_states (company_id, status);

create index idx_ad_recommendation_state_account_status
  on ad_recommendation_states (ad_account_id, status);
