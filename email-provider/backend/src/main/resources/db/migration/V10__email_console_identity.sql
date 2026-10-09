-- Independent product account identities; never join to UnifyDesk App users.
CREATE TABLE email_console_users (
    id BIGINT PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES email_product_organizations(id),
    email VARCHAR(320) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    password_hash VARCHAR(255),
    role VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_email_console_user UNIQUE (organization_id, email)
);
CREATE TABLE email_console_invitations (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES email_console_users(id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE email_console_sessions (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES email_console_users(id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_email_console_sessions_user ON email_console_sessions(user_id);
CREATE INDEX idx_email_console_invitations_user ON email_console_invitations(user_id);
