-- Independent UnifyDesk Email organizations/projects. Existing company_id fields in the
-- delivery engine are populated with email_product_projects.id, not UnifyDesk App IDs.
CREATE TABLE email_product_organizations (
  id BIGINT PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  contact_email VARCHAR(320) NOT NULL,
  status VARCHAR(24) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE email_product_projects (
  id BIGINT PRIMARY KEY,
  organization_id BIGINT NOT NULL REFERENCES email_product_organizations(id),
  name VARCHAR(120) NOT NULL,
  status VARCHAR(24) NOT NULL,
  sending_enabled BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_email_product_projects_organization ON email_product_projects(organization_id);

CREATE TABLE email_product_api_keys (
  id BIGINT PRIMARY KEY,
  project_id BIGINT NOT NULL REFERENCES email_product_projects(id),
  name VARCHAR(100) NOT NULL,
  lookup_prefix VARCHAR(24) NOT NULL UNIQUE,
  secret_hash VARCHAR(64) NOT NULL,
  scopes VARCHAR(160) NOT NULL,
  expires_at TIMESTAMPTZ,
  revoked_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_email_product_keys_project ON email_product_api_keys(project_id);
