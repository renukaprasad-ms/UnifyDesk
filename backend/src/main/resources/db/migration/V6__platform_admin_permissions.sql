CREATE TABLE IF NOT EXISTS platform_admin_permissions (
    id BIGINT PRIMARY KEY,
    platform_admin_id BIGINT NOT NULL,
    permission VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_platform_admin_permissions_admin
      FOREIGN KEY (platform_admin_id) REFERENCES platform_admins(id) ON DELETE CASCADE,
    CONSTRAINT uk_platform_admin_permission UNIQUE (platform_admin_id, permission)
);

CREATE INDEX IF NOT EXISTS idx_platform_admin_permission_admin
  ON platform_admin_permissions(platform_admin_id);
