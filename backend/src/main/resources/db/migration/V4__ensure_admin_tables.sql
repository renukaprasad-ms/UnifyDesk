create table if not exists platform_admins (
  id bigint primary key,
  user_id bigint not null,
  role varchar(32) not null,
  status varchar(32) not null,
  created_at timestamp(6) with time zone not null,
  constraint uk_platform_admins_user unique (user_id)
);

create table if not exists admin_company_assignments (
  id bigint primary key,
  admin_user_id bigint not null,
  company_id bigint not null,
  assigned_by_user_id bigint not null,
  created_at timestamp(6) with time zone not null,
  constraint uk_admin_company_assignments_admin_company unique (admin_user_id, company_id)
);

create index if not exists idx_platform_admins_role on platform_admins (role);
create index if not exists idx_admin_company_assignments_admin on admin_company_assignments (admin_user_id);
create index if not exists idx_admin_company_assignments_company on admin_company_assignments (company_id);
