CREATE TABLE email_console_roles (
  id BIGINT PRIMARY KEY,
  organization_id BIGINT NOT NULL REFERENCES email_product_organizations(id),
  name VARCHAR(80) NOT NULL,
  permissions VARCHAR(200) NOT NULL,
  UNIQUE(organization_id,id),
  UNIQUE(organization_id,name)
);
ALTER TABLE email_console_users ADD COLUMN custom_role_id BIGINT,
  ADD CONSTRAINT fk_console_custom_role_tenant FOREIGN KEY(organization_id,custom_role_id)
    REFERENCES email_console_roles(organization_id,id);
CREATE TABLE email_console_role_audit (
  id BIGINT PRIMARY KEY,
  organization_id BIGINT NOT NULL,
  actor_user_id BIGINT NOT NULL,
  target_user_id BIGINT,
  role_id BIGINT NOT NULL,
  action VARCHAR(24) NOT NULL CHECK(action IN ('ROLE_CREATED','ROLE_UPDATED','ROLE_ASSIGNED','ROLE_REMOVED')),
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  FOREIGN KEY(organization_id,actor_user_id) REFERENCES email_console_users(organization_id,id),
  FOREIGN KEY(organization_id,target_user_id) REFERENCES email_console_users(organization_id,id),
  FOREIGN KEY(organization_id,role_id) REFERENCES email_console_roles(organization_id,id)
);
CREATE INDEX idx_console_role_audit ON email_console_role_audit(organization_id,id DESC);
