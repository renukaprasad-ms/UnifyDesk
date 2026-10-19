-- Notification System Round-2 B2 delivery remediation.
-- Adds an explicit provider-call crash boundary and retention indexes.
-- Existing incomplete Round-1 attempts are conservatively treated as IN_FLIGHT
-- because their external-effect status cannot be reconstructed safely.

alter table notification_delivery_attempts
  add column provider_call_state varchar(24);

alter table notification_delivery_attempts
  add column provider_call_started_at timestamptz null;

update notification_delivery_attempts
set provider_call_state =
    case
      when completed_at is null then 'IN_FLIGHT'
      else 'COMPLETED'
    end,
    provider_call_started_at =
    case
      when completed_at is null then started_at
      else provider_call_started_at
    end
where provider_call_state is null;

alter table notification_delivery_attempts
  alter column provider_call_state set not null;

alter table notification_delivery_attempts
  alter column provider_call_state set default 'COMPLETED';

alter table notification_delivery_attempts
  add constraint ck_notification_delivery_attempt_provider_call_state
  check (provider_call_state in ('PREPARED','IN_FLIGHT','COMPLETED'));

alter table notification_delivery_attempts
  add constraint ck_notification_delivery_attempt_provider_call_timestamps
  check (
    (provider_call_state = 'PREPARED'
      and provider_call_started_at is null
      and completed_at is null)
    or
    (provider_call_state = 'IN_FLIGHT'
      and provider_call_started_at is not null
      and completed_at is null)
    or
    (provider_call_state = 'COMPLETED'
      and completed_at is not null)
  );

create index idx_notification_delivery_attempts_recovery
  on notification_delivery_attempts(provider_call_state, started_at, id)
  where completed_at is null;

create index idx_notification_delivery_attempts_retention
  on notification_delivery_attempts(created_at, id);

create index idx_notification_delivery_intents_retention
  on notification_delivery_intents(updated_at, id)
  where state in ('SENT','SUPPRESSED','FAILED_PERMANENT','UNKNOWN','DEAD');

create index idx_notification_push_attempts_retention
  on notification_push_attempts(updated_at, id)
  where state in ('ACCEPTED','FAILED_PERMANENT','DEAD','UNKNOWN');

create index idx_notification_digest_buckets_retention
  on notification_digest_buckets(updated_at, id)
  where state in ('SENT','SUPPRESSED','DEAD','UNKNOWN');

create index idx_notification_push_subscriptions_retention
  on notification_push_subscriptions(updated_at, id)
  where status in ('REVOKED','EXPIRED','INVALID');
