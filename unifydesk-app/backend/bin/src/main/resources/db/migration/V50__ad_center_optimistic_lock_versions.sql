alter table ad_account_billing
  add column if not exists version bigint not null default 0;

alter table ad_account_health
  add column if not exists version bigint not null default 0;

alter table ad_recommendation_states
  add column if not exists version bigint not null default 0;
