-- Each Zoho mailbox has its own capability URL, encrypted provider signing secret,
-- and durable, coalesced inbox-refresh trigger. Tokens and signing keys are never plaintext.
create table zoho_mail_webhook_registrations (
    connection_id bigint primary key references channel_connections(id) on delete cascade,
    company_id bigint not null,
    token_sha256 varchar(64) not null unique,
    signing_secret_encrypted text,
    status varchar(24) not null default 'AWAITING_HANDSHAKE',
    pending_at timestamptz,
    last_received_at timestamptz,
    last_processed_at timestamptz,
    next_attempt_at timestamptz not null default now(),
    lease_until timestamptz,
    attempts integer not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_zoho_webhook_status check (status in ('AWAITING_HANDSHAKE', 'ACTIVE'))
);

create index idx_zoho_webhook_pending
    on zoho_mail_webhook_registrations(next_attempt_at, lease_until)
    where pending_at is not null;
