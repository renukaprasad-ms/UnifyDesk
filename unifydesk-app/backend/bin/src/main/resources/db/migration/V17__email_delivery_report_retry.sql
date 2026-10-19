alter table email_delivery_reports
  add column provider_correlation_id varchar(128),
  add column attempt_count integer not null default 0,
  add column next_attempt_at timestamp(6) with time zone,
  add column claimed_at timestamp(6) with time zone,
  add column last_error varchar(1000);

create index idx_email_delivery_reports_claim
  on email_delivery_reports (processed_at, next_attempt_at, claimed_at, id)
  where processed_at is null;

create index idx_email_delivery_reports_correlation
  on email_delivery_reports (provider_correlation_id)
  where provider_correlation_id is not null;
