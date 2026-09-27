-- Notification System Round-2 core remediation.
-- Adds permission-scoped unread counter buckets and cleanup/retention indexes.

CREATE TABLE notification_unread_permission_counters (
    user_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    permission_key VARCHAR(160) NOT NULL,
    unread_count BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_notification_unread_permission_counters
      PRIMARY KEY (user_id, company_id, permission_key),
    CONSTRAINT ck_notification_permission_counter_nonnegative
      CHECK (unread_count >= 0)
);

CREATE INDEX idx_notification_permission_counters_company
  ON notification_unread_permission_counters(company_id, user_id, permission_key);

-- Split existing company counters into:
--   notification_unread_counters => unread rows with no required permission
--   notification_unread_permission_counters => unread rows per required permission.
INSERT INTO notification_unread_permission_counters(
  user_id, company_id, permission_key, unread_count, updated_at)
SELECT
  n.user_id,
  n.company_id,
  n.required_permission_key,
  count(*),
  now()
FROM user_notifications n
WHERE n.company_id IS NOT NULL
  AND n.required_permission_key IS NOT NULL
  AND n.read_at IS NULL
  AND (n.expires_at IS NULL OR n.expires_at > now())
GROUP BY n.user_id, n.company_id, n.required_permission_key
ON CONFLICT (user_id, company_id, permission_key)
DO UPDATE SET
  unread_count = EXCLUDED.unread_count,
  updated_at = EXCLUDED.updated_at;

UPDATE notification_unread_counters c
SET unread_count = COALESCE(
      (
        SELECT count(*)
        FROM user_notifications n
        WHERE n.user_id = c.user_id
          AND n.company_id = c.company_id
          AND n.required_permission_key IS NULL
          AND n.read_at IS NULL
          AND (n.expires_at IS NULL OR n.expires_at > now())
      ),
      0
    ),
    updated_at = now()
WHERE c.scope_kind = 'COMPANY';

CREATE INDEX idx_user_notifications_company_permission_unread
  ON user_notifications(user_id, company_id, required_permission_key)
  WHERE company_id IS NOT NULL
    AND required_permission_key IS NOT NULL
    AND read_at IS NULL;

CREATE INDEX idx_user_notifications_retention
  ON user_notifications(last_event_at, id);

CREATE INDEX idx_user_notifications_company_cleanup
  ON user_notifications(company_id, id)
  WHERE company_id IS NOT NULL;

CREATE INDEX idx_notification_events_recipient_user
  ON notification_events(recipient_user_id, id)
  WHERE recipient_user_id IS NOT NULL;

CREATE INDEX idx_notification_events_retention_materialized
  ON notification_events(materialized_at, id)
  WHERE materialized_at IS NOT NULL;

CREATE INDEX idx_notification_events_retention_dead
  ON notification_events(updated_at, id)
  WHERE publish_state = 'DEAD';

CREATE INDEX idx_notification_fanout_company
  ON notification_fanout_jobs(company_id, id);

CREATE INDEX idx_notification_event_recipients_company
  ON notification_event_recipients(company_id, id)
  WHERE company_id IS NOT NULL;

CREATE INDEX idx_notification_unread_counters_company_cleanup
  ON notification_unread_counters(company_id, user_id)
  WHERE company_id IS NOT NULL;

CREATE INDEX idx_notification_user_settings_company_cleanup
  ON notification_user_settings(company_id, user_id)
  WHERE company_id IS NOT NULL;

CREATE INDEX idx_notification_preference_company_cleanup
  ON notification_preference_overrides(company_id, user_id)
  WHERE company_id IS NOT NULL;
