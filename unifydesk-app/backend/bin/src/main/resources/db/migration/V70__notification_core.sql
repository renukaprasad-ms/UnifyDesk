CREATE TABLE notification_events (
    id BIGINT PRIMARY KEY,
    event_key VARCHAR(255) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    schema_version INTEGER NOT NULL,
    company_id BIGINT,
    actor_user_id BIGINT,
    recipient_mode VARCHAR(32) NOT NULL,
    recipient_user_id BIGINT,
    required_permission_key VARCHAR(160),
    subject_type VARCHAR(64) NOT NULL,
    subject_id VARCHAR(160) NOT NULL,
    priority VARCHAR(16) NOT NULL,
    group_key VARCHAR(180),
    variables_json JSONB NOT NULL,
    correlation_id VARCHAR(80) NOT NULL,
    causation_id VARCHAR(80),
    occurred_at TIMESTAMPTZ NOT NULL,
    publish_state VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    publish_attempts INTEGER NOT NULL DEFAULT 0,
    next_publish_at TIMESTAMPTZ NOT NULL,
    claim_token VARCHAR(80),
    claim_expires_at TIMESTAMPTZ,
    published_at TIMESTAMPTZ,
    materialized_at TIMESTAMPTZ,
    last_error_code VARCHAR(80),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_notification_events_recipient
      CHECK (
        (recipient_mode = 'USER' AND recipient_user_id IS NOT NULL)
        OR
        (recipient_mode = 'COMPANY_PERMISSION'
          AND company_id IS NOT NULL
          AND required_permission_key IS NOT NULL)
      ),
    CONSTRAINT ck_notification_events_priority
      CHECK (priority IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW')),
    CONSTRAINT ck_notification_events_publish_state
      CHECK (publish_state IN ('PENDING', 'CLAIMED', 'PUBLISHED', 'DEAD')),
    CONSTRAINT ck_notification_events_publish_attempts CHECK (publish_attempts >= 0)
);

CREATE UNIQUE INDEX uk_notification_events_event_key
  ON notification_events(event_key);

CREATE INDEX idx_notification_events_publish_due
  ON notification_events(next_publish_at, id)
  WHERE publish_state IN ('PENDING', 'CLAIMED');

CREATE INDEX idx_notification_events_materialization_recovery
  ON notification_events(published_at, id)
  WHERE publish_state = 'PUBLISHED' AND materialized_at IS NULL;

CREATE INDEX idx_notification_events_company_created
  ON notification_events(company_id, created_at DESC, id DESC);


CREATE TABLE notification_fanout_jobs (
    id BIGINT PRIMARY KEY,
    event_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    state VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    cursor_user_id BIGINT,
    batch_size INTEGER NOT NULL DEFAULT 500,
    processed_count BIGINT NOT NULL DEFAULT 0,
    suppressed_count BIGINT NOT NULL DEFAULT 0,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    claim_token VARCHAR(80),
    claim_expires_at TIMESTAMPTZ,
    last_error_code VARCHAR(80),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    CONSTRAINT uk_notification_fanout_event UNIQUE (event_id),
    CONSTRAINT ck_notification_fanout_state
      CHECK (state IN ('PENDING', 'CLAIMED', 'RUNNING', 'COMPLETED', 'DEAD')),
    CONSTRAINT ck_notification_fanout_batch_size CHECK (batch_size BETWEEN 50 AND 1000),
    CONSTRAINT ck_notification_fanout_counts
      CHECK (processed_count >= 0 AND suppressed_count >= 0 AND attempts >= 0)
);

CREATE INDEX idx_notification_fanout_due
  ON notification_fanout_jobs(next_attempt_at, id)
  WHERE state IN ('PENDING', 'RUNNING', 'CLAIMED');


CREATE TABLE notification_event_recipients (
    id BIGINT PRIMARY KEY,
    notification_event_id BIGINT NOT NULL,
    company_id BIGINT,
    user_id BIGINT NOT NULL,
    notification_id BIGINT,
    status VARCHAR(24) NOT NULL,
    suppression_reason VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_notification_event_recipient UNIQUE (notification_event_id, user_id),
    CONSTRAINT ck_notification_event_recipient_status
      CHECK (status IN ('MATERIALIZED', 'SUPPRESSED_INELIGIBLE', 'SUPPRESSED_POLICY'))
);

CREATE INDEX idx_notification_event_recipients_user
  ON notification_event_recipients(user_id, created_at DESC);

CREATE INDEX idx_notification_event_recipients_event
  ON notification_event_recipients(notification_event_id);


CREATE TABLE user_notifications (
    id BIGINT PRIMARY KEY,
    company_id BIGINT,
    user_id BIGINT NOT NULL,
    notification_type VARCHAR(100) NOT NULL,
    category VARCHAR(64) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    required_permission_key VARCHAR(160),
    title VARCHAR(240) NOT NULL,
    body VARCHAR(1000) NOT NULL,
    aggregate_count INTEGER NOT NULL DEFAULT 1,
    group_key VARCHAR(180),
    bucket_start TIMESTAMPTZ,
    action_kind VARCHAR(32),
    action_path VARCHAR(512),
    first_event_at TIMESTAMPTZ NOT NULL,
    last_event_at TIMESTAMPTZ NOT NULL,
    seen_at TIMESTAMPTZ,
    read_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_user_notifications_aggregate_count
      CHECK (aggregate_count BETWEEN 1 AND 2147483647),
    CONSTRAINT ck_user_notifications_action_kind
      CHECK (action_kind IS NULL OR action_kind = 'ROUTE'),
    CONSTRAINT ck_user_notifications_action_path
      CHECK (
        action_path IS NULL
        OR (
          left(action_path, 1) = '/'
          AND left(action_path, 2) <> '//'
          AND position(E'\\' in action_path) = 0
        )
      )
);

CREATE UNIQUE INDEX uk_user_notifications_company_bucket
  ON user_notifications(company_id, user_id, notification_type, group_key, bucket_start)
  WHERE company_id IS NOT NULL
    AND group_key IS NOT NULL
    AND bucket_start IS NOT NULL;

CREATE INDEX idx_user_notifications_company_seek
  ON user_notifications(user_id, company_id, last_event_at DESC, id DESC)
  WHERE company_id IS NOT NULL;

CREATE INDEX idx_user_notifications_account_seek
  ON user_notifications(user_id, last_event_at DESC, id DESC)
  WHERE company_id IS NULL;

CREATE INDEX idx_user_notifications_company_unread
  ON user_notifications(user_id, company_id, last_event_at DESC, id DESC)
  WHERE company_id IS NOT NULL AND read_at IS NULL;

CREATE INDEX idx_user_notifications_account_unread
  ON user_notifications(user_id, last_event_at DESC, id DESC)
  WHERE company_id IS NULL AND read_at IS NULL;

CREATE INDEX idx_user_notifications_expiry
  ON user_notifications(expires_at, id)
  WHERE expires_at IS NOT NULL;


CREATE TABLE notification_unread_counters (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    scope_kind VARCHAR(16) NOT NULL,
    company_id BIGINT,
    unread_count BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_notification_counter_nonnegative CHECK (unread_count >= 0),
    CONSTRAINT ck_notification_counter_scope
      CHECK (
        (scope_kind = 'ACCOUNT' AND company_id IS NULL)
        OR
        (scope_kind = 'COMPANY' AND company_id IS NOT NULL)
      )
);

CREATE UNIQUE INDEX uk_notification_counter_account
  ON notification_unread_counters(user_id)
  WHERE scope_kind = 'ACCOUNT';

CREATE UNIQUE INDEX uk_notification_counter_company
  ON notification_unread_counters(user_id, company_id)
  WHERE scope_kind = 'COMPANY';


CREATE TABLE notification_user_settings (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    company_id BIGINT,
    timezone VARCHAR(80),
    quiet_hours_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    quiet_start TIME,
    quiet_end TIME,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_notification_settings_quiet_times
      CHECK (
        quiet_hours_enabled = FALSE
        OR (quiet_start IS NOT NULL AND quiet_end IS NOT NULL)
      )
);

CREATE UNIQUE INDEX uk_notification_user_settings_account
  ON notification_user_settings(user_id)
  WHERE company_id IS NULL;

CREATE UNIQUE INDEX uk_notification_user_settings_company
  ON notification_user_settings(user_id, company_id)
  WHERE company_id IS NOT NULL;


CREATE TABLE notification_preference_overrides (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    company_id BIGINT,
    notification_type VARCHAR(100) NOT NULL,
    email_mode VARCHAR(24),
    browser_enabled BOOLEAN,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_notification_preference_email_mode
      CHECK (email_mode IS NULL OR email_mode IN ('OFF', 'IMMEDIATE', 'DELAYED', 'DIGEST'))
);

CREATE UNIQUE INDEX uk_notification_preference_account
  ON notification_preference_overrides(user_id, notification_type)
  WHERE company_id IS NULL;

CREATE UNIQUE INDEX uk_notification_preference_company
  ON notification_preference_overrides(user_id, company_id, notification_type)
  WHERE company_id IS NOT NULL;


CREATE TABLE notification_delivery_intents (
    id BIGINT PRIMARY KEY,
    notification_event_id BIGINT NOT NULL,
    notification_id BIGINT,
    company_id BIGINT,
    user_id BIGINT NOT NULL,
    notification_type VARCHAR(100) NOT NULL,
    required_permission_key VARCHAR(160),
    channel VARCHAR(24) NOT NULL,
    delivery_mode VARCHAR(24) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    state VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    not_before TIMESTAMPTZ NOT NULL,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    claim_token VARCHAR(80),
    claim_expires_at TIMESTAMPTZ,
    last_error_code VARCHAR(80),
    sent_at TIMESTAMPTZ,
    suppressed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_notification_delivery_channel
      CHECK (channel IN ('EMAIL', 'WEB_PUSH')),
    CONSTRAINT ck_notification_delivery_mode
      CHECK (delivery_mode IN ('IMMEDIATE', 'DELAYED', 'DIGEST')),
    CONSTRAINT ck_notification_delivery_state
      CHECK (
        state IN (
          'PENDING',
          'DEFERRED',
          'CLAIMED',
          'SENT',
          'SUPPRESSED',
          'FAILED_RETRYABLE',
          'FAILED_PERMANENT',
          'UNKNOWN',
          'DEAD'
        )
      ),
    CONSTRAINT ck_notification_delivery_attempt_count CHECK (attempt_count >= 0)
);

CREATE UNIQUE INDEX uk_notification_delivery_intent_key
  ON notification_delivery_intents(idempotency_key);

CREATE INDEX idx_notification_delivery_intents_due
  ON notification_delivery_intents(next_attempt_at, id)
  WHERE state IN ('PENDING', 'DEFERRED', 'FAILED_RETRYABLE', 'CLAIMED');

CREATE INDEX idx_notification_delivery_intents_user
  ON notification_delivery_intents(user_id, created_at DESC, id DESC);
