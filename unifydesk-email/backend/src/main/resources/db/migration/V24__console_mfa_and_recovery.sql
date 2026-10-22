ALTER TABLE email_console_users
  ADD COLUMN mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN mfa_secret_encrypted TEXT,
  ADD COLUMN mfa_pending_encrypted TEXT,
  ADD COLUMN mfa_pending_expires_at TIMESTAMPTZ,
  ADD COLUMN mfa_last_counter BIGINT NOT NULL DEFAULT -1;
CREATE TABLE email_console_recovery_codes (
  user_id BIGINT NOT NULL REFERENCES email_console_users(id),
  code_hash CHAR(64) NOT NULL,
  PRIMARY KEY(user_id,code_hash)
);
CREATE TABLE email_console_password_resets (
  token_hash CHAR(64) PRIMARY KEY,
  organization_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  used_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  FOREIGN KEY(organization_id,user_id) REFERENCES email_console_users(organization_id,id)
);
CREATE INDEX idx_console_password_resets_user ON email_console_password_resets(user_id,expires_at);
CREATE TABLE email_console_security_audit (
  id BIGINT PRIMARY KEY,
  organization_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  action VARCHAR(40) NOT NULL,
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  FOREIGN KEY(organization_id,user_id) REFERENCES email_console_users(organization_id,id),
  CHECK(action IN ('MFA_ENABLED','MFA_DISABLED','RECOVERY_CODE_USED','PASSWORD_CHANGED',
      'PASSWORD_RESET_ISSUED','PASSWORD_RESET_COMPLETED','SESSIONS_REVOKED'))
);
CREATE INDEX idx_console_security_audit_user ON email_console_security_audit(organization_id,user_id,id DESC);
