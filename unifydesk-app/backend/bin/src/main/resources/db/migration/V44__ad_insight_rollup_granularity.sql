alter table ad_insights_daily
  add column granularity varchar(16) not null default 'DAILY',
  add column period_end date;

update ad_insights_daily
set period_end = insight_date
where period_end is null;

alter table ad_insights_daily
  alter column period_end set not null;

alter table ad_insights_daily
  drop constraint uk_ad_insights_daily;

alter table ad_insights_daily
  add constraint uk_ad_insights_period
    unique (
      ad_account_id,
      provider,
      insight_level,
      granularity,
      entity_ref,
      insight_date,
      period_end
    );

create index idx_ad_insights_granularity_period
  on ad_insights_daily (
    ad_account_id,
    insight_level,
    granularity,
    insight_date,
    period_end
  );
