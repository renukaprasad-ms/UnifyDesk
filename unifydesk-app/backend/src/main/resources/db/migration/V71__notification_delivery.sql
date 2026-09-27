create table notification_push_subscriptions (
    id bigint primary key,
    user_id bigint not null,
    endpoint_hash varchar(64) not null,
    endpoint_ciphertext text not null,
    p256dh_ciphertext text not null,
    auth_ciphertext text not null,
    expiration_time timestamptz null,
    device_label varchar(120) null,
    status varchar(24) not null,
    created_at timestamptz not null,
    last_success_at timestamptz null,
    last_failure_at timestamptz null,
    revoked_at timestamptz null,
    updated_at timestamptz not null,
    constraint ck_notification_push_subscription_status
        check (status in ('ACTIVE','REVOKED','EXPIRED','INVALID'))
);

create unique index uk_notification_push_subscriptions_endpoint_hash
    on notification_push_subscriptions(endpoint_hash);

create index idx_notification_push_subscriptions_user_status
    on notification_push_subscriptions(user_id, status, created_at desc);

create index idx_notification_push_subscriptions_expiry
    on notification_push_subscriptions(expiration_time, id)
    where status = 'ACTIVE' and expiration_time is not null;

create table notification_delivery_attempts (
    id bigint primary key,
    intent_id bigint not null,
    subscription_id bigint null,
    attempt_no integer not null,
    channel varchar(24) not null,
    started_at timestamptz not null,
    completed_at timestamptz null,
    outcome varchar(32) not null,
    provider_status_code integer null,
    provider_error_code varchar(80) null,
    provider_reference_hash varchar(128) null,
    latency_ms bigint null,
    created_at timestamptz not null,
    constraint ck_notification_delivery_attempt_channel
        check (channel in ('EMAIL','WEB_PUSH')),
    constraint ck_notification_delivery_attempt_outcome
        check (outcome in (
            'ACCEPTED',
            'TRANSIENT_FAILURE',
            'PERMANENT_FAILURE',
            'RATE_LIMITED',
            'UNKNOWN',
            'SUPPRESSED_POLICY'
        )),
    constraint ck_notification_delivery_attempt_number
        check (attempt_no > 0)
);

create unique index uk_notification_delivery_attempt_email
    on notification_delivery_attempts(intent_id, attempt_no)
    where subscription_id is null;

create unique index uk_notification_delivery_attempt_subscription
    on notification_delivery_attempts(intent_id, subscription_id, attempt_no)
    where subscription_id is not null;

create index idx_notification_delivery_attempts_intent
    on notification_delivery_attempts(intent_id, created_at desc, id desc);

create table notification_push_attempts (
    id bigint primary key,
    intent_id bigint not null,
    subscription_id bigint not null,
    attempt_no integer not null default 0,
    state varchar(24) not null,
    next_attempt_at timestamptz not null,
    claim_token varchar(80) null,
    claim_expires_at timestamptz null,
    last_http_status integer null,
    last_error_code varchar(80) null,
    accepted_at timestamptz null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint uk_notification_push_attempt_intent_subscription
        unique (intent_id, subscription_id),
    constraint ck_notification_push_attempt_state
        check (state in (
            'PENDING',
            'CLAIMED',
            'ACCEPTED',
            'FAILED_RETRYABLE',
            'FAILED_PERMANENT',
            'DEAD',
            'UNKNOWN'
        )),
    constraint ck_notification_push_attempt_number
        check (attempt_no >= 0)
);

create index idx_notification_push_attempts_due
    on notification_push_attempts(next_attempt_at, id)
    where state in ('PENDING','FAILED_RETRYABLE','CLAIMED');

create table notification_digest_buckets (
    id bigint primary key,
    user_id bigint not null,
    company_id bigint not null,
    notification_type varchar(100) not null,
    state varchar(24) not null,
    contribution_count integer not null,
    first_contribution_at timestamptz not null,
    last_contribution_at timestamptz not null,
    eligible_at timestamptz not null,
    next_attempt_at timestamptz not null,
    claim_token varchar(80) null,
    claim_expires_at timestamptz null,
    sent_at timestamptz null,
    suppressed_at timestamptz null,
    last_error_code varchar(80) null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint ck_notification_digest_bucket_state
        check (state in (
            'OPEN',
            'ELIGIBLE',
            'CLAIMED',
            'SENT',
            'SUPPRESSED',
            'FAILED_RETRYABLE',
            'DEAD',
            'UNKNOWN'
        )),
    constraint ck_notification_digest_contribution_count
        check (contribution_count > 0)
);

create unique index uk_notification_digest_bucket_active
    on notification_digest_buckets(user_id, company_id, notification_type)
    where state in ('OPEN','ELIGIBLE','CLAIMED');

create index idx_notification_digest_buckets_due
    on notification_digest_buckets(next_attempt_at, id)
    where state in ('OPEN','ELIGIBLE','FAILED_RETRYABLE','CLAIMED');

create index idx_notification_digest_buckets_history
    on notification_digest_buckets(user_id, company_id, notification_type, sent_at desc)
    where state = 'SENT';

create table notification_digest_items (
    digest_bucket_id bigint not null,
    delivery_intent_id bigint not null,
    created_at timestamptz not null,
    primary key (digest_bucket_id, delivery_intent_id),
    constraint uk_notification_digest_items_intent unique (delivery_intent_id),
    constraint fk_notification_digest_items_bucket
        foreign key (digest_bucket_id)
        references notification_digest_buckets(id)
        on delete cascade
);

create index idx_notification_digest_items_bucket
    on notification_digest_items(digest_bucket_id, delivery_intent_id);
