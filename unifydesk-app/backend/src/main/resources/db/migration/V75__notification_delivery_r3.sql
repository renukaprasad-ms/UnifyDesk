-- Notification System Round-3 delivery remediation.
-- Persist provider-authoritative Web Push retry timing across worker crashes.

alter table notification_delivery_attempts
  add column provider_retry_after_at timestamptz null;

alter table notification_delivery_attempts
  add constraint ck_notification_delivery_attempt_retry_after_completed
  check (provider_retry_after_at is null or provider_call_state = 'COMPLETED');
