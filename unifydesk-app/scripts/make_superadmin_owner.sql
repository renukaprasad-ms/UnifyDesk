-- ============================================================================
-- Ensure Super Admin (renukaprasadms@unifydesk.in) is OWNER in ALL companies
-- ============================================================================

CREATE OR REPLACE FUNCTION ensure_superadmin_owner_trigger()
RETURNS TRIGGER AS $$
DECLARE
  v_superadmin_id bigint := 369407586547535872;
  v_member_id bigint;
  v_assign_id bigint;
BEGIN
  IF EXISTS (SELECT 1 FROM users WHERE id = v_superadmin_id) THEN
    v_member_id := (((extract(epoch from clock_timestamp()) * 1000)::bigint - 1704067200000::bigint) << 22) | (1 << 12) | (abs(hashtext(NEW.id::text)) % 4096);
    v_assign_id := (((extract(epoch from clock_timestamp()) * 1000)::bigint - 1704067200000::bigint) << 22) | (1 << 12) | ((abs(hashtext(NEW.id::text)) + 1) % 4096);

    INSERT INTO company_members (id, company_id, user_id, role, status, created_at, updated_at)
    VALUES (v_member_id, NEW.id, v_superadmin_id, 'OWNER', 'ACTIVE', now(), now())
    ON CONFLICT (company_id, user_id) DO UPDATE SET role = 'OWNER', status = 'ACTIVE';

    INSERT INTO admin_company_assignments (id, admin_user_id, company_id, assigned_by_user_id, created_at)
    VALUES (v_assign_id, v_superadmin_id, NEW.id, v_superadmin_id, now())
    ON CONFLICT (admin_user_id, company_id) DO NOTHING;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_ensure_superadmin_owner ON companies;
CREATE TRIGGER trg_ensure_superadmin_owner
AFTER INSERT ON companies
FOR EACH ROW
EXECUTE FUNCTION ensure_superadmin_owner_trigger();

-- Backfill all existing companies immediately
DO $$
DECLARE
  v_superadmin_id bigint := 369407586547535872;
  c record;
  v_member_id bigint;
  v_assign_id bigint;
  v_count int := 0;
BEGIN
  IF EXISTS (SELECT 1 FROM users WHERE id = v_superadmin_id) THEN
    FOR c IN SELECT id, name FROM companies LOOP
      v_member_id := (((extract(epoch from clock_timestamp()) * 1000)::bigint - 1704067200000::bigint) << 22) | (1 << 12) | (abs(hashtext(c.id::text)) % 4096);
      v_assign_id := (((extract(epoch from clock_timestamp()) * 1000)::bigint - 1704067200000::bigint) << 22) | (1 << 12) | ((abs(hashtext(c.id::text)) + 1) % 4096);

      INSERT INTO company_members (id, company_id, user_id, role, status, created_at, updated_at)
      VALUES (v_member_id, c.id, v_superadmin_id, 'OWNER', 'ACTIVE', now(), now())
      ON CONFLICT (company_id, user_id) DO UPDATE SET role = 'OWNER', status = 'ACTIVE';

      INSERT INTO admin_company_assignments (id, admin_user_id, company_id, assigned_by_user_id, created_at)
      VALUES (v_assign_id, v_superadmin_id, c.id, v_superadmin_id, now())
      ON CONFLICT (admin_user_id, company_id) DO NOTHING;

      v_count := v_count + 1;
    END LOOP;
    RAISE NOTICE 'SUCCESS: Super Admin granted OWNER role in all % companies!', v_count;
  ELSE
    RAISE WARNING 'User id % not found!', v_superadmin_id;
  END IF;
END;
$$;
