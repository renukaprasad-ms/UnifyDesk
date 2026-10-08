-- Durable, retryable, provider-specific poll queue; no outbound SMS retries.
create table managed_sms_metareach_dlr_jobs (
 id bigint primary key,
 company_id bigint not null,
 run_id bigint not null,
 recipient_id bigint not null,
 correlation_token varchar(52) not null unique references managed_sms_correlations(correlation_token),
 message_id varchar(255) not null,
 mobile varchar(15) not null,
 state varchar(16) not null,
 poll_count integer not null default 0,
 next_poll_at timestamptz not null,
 expires_at timestamptz not null,
 lease_until timestamptz,
 created_at timestamptz not null,
 constraint fk_metareach_poll_recipient foreign key (company_id,run_id,recipient_id)
 references broadcast_recipients(company_id,run_id,id),
 constraint ck_metareach_poll_state check(state in ('PENDING','CLAIMED','TERMINAL','EXPIRED'))
);
create index idx_metareach_poll_due on managed_sms_metareach_dlr_jobs(next_poll_at,id)
 where state='PENDING';
