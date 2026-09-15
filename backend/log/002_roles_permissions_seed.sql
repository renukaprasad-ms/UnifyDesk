-- Role and permission seed history
-- Role storage today:
-- - Company roles are enum values in company_members.role:
--   OWNER, MANAGER, MEMBER
-- - Platform roles are enum values in platform_admins.role:
--   SUPER_ADMIN, ADMIN
--
-- Permission model:
-- - OWNER has all company permissions in application defaults.
-- - SUPER_ADMIN and ADMIN are platform roles; platform access is authorized through
--   platform_admins and admin_company_assignments, not company_role_permissions.
-- - The rows below seed global company role permissions where company_id is null.
--   Company-specific overrides can use the same table with a concrete company_id.

insert into company_role_permissions (id, company_id, role, permission, effect) values
  (100000000000001001, null, 'OWNER', 'COMPANY_VIEW', 'ALLOW'),
  (100000000000001002, null, 'OWNER', 'COMPANY_UPDATE', 'ALLOW'),
  (100000000000001003, null, 'OWNER', 'MEMBER_INVITE', 'ALLOW'),
  (100000000000001004, null, 'OWNER', 'MEMBER_REMOVE', 'ALLOW'),
  (100000000000001005, null, 'OWNER', 'MEMBER_ROLE_UPDATE', 'ALLOW'),
  (100000000000001006, null, 'OWNER', 'BILLING_VIEW', 'ALLOW'),
  (100000000000001007, null, 'OWNER', 'BILLING_MANAGE', 'ALLOW'),
  (100000000000001008, null, 'OWNER', 'BROADCAST_CREATE', 'ALLOW'),
  (100000000000001009, null, 'OWNER', 'BROADCAST_APPROVE', 'ALLOW'),
  (100000000000001010, null, 'OWNER', 'TEMPLATE_CREATE', 'ALLOW'),
  (100000000000001011, null, 'OWNER', 'TEMPLATE_APPROVE', 'ALLOW'),
  (100000000000001012, null, 'OWNER', 'MESSAGE_VIEW', 'ALLOW'),
  (100000000000001013, null, 'OWNER', 'MESSAGE_SEND', 'ALLOW'),
  (100000000000001014, null, 'OWNER', 'ADS_VIEW', 'ALLOW'),
  (100000000000001015, null, 'OWNER', 'ADS_MANAGE', 'ALLOW'),
  (100000000000001016, null, 'OWNER', 'SETTINGS_VIEW', 'ALLOW'),
  (100000000000001017, null, 'OWNER', 'SETTINGS_MANAGE', 'ALLOW'),
  (100000000000001018, null, 'OWNER', 'MEDIA_UPLOAD', 'ALLOW'),

  (100000000000001101, null, 'MANAGER', 'COMPANY_VIEW', 'ALLOW'),
  (100000000000001102, null, 'MANAGER', 'MEMBER_INVITE', 'ALLOW'),
  (100000000000001103, null, 'MANAGER', 'MESSAGE_VIEW', 'ALLOW'),
  (100000000000001104, null, 'MANAGER', 'MESSAGE_SEND', 'ALLOW'),
  (100000000000001105, null, 'MANAGER', 'BROADCAST_CREATE', 'ALLOW'),
  (100000000000001106, null, 'MANAGER', 'TEMPLATE_CREATE', 'ALLOW'),
  (100000000000001107, null, 'MANAGER', 'MEDIA_UPLOAD', 'ALLOW'),
  (100000000000001108, null, 'MANAGER', 'SETTINGS_VIEW', 'ALLOW'),

  (100000000000001201, null, 'MEMBER', 'COMPANY_VIEW', 'ALLOW'),
  (100000000000001202, null, 'MEMBER', 'MESSAGE_VIEW', 'ALLOW'),
  (100000000000001203, null, 'MEMBER', 'MESSAGE_SEND', 'ALLOW'),
  (100000000000001204, null, 'MEMBER', 'MEDIA_UPLOAD', 'ALLOW')
on conflict (id) do update set
  role = excluded.role,
  permission = excluded.permission,
  effect = excluded.effect;
