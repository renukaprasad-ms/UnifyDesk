alter table users add column if not exists status varchar(24) not null default 'ACTIVE';
alter table users add column if not exists deleted_at timestamp(6) with time zone;

create index if not exists idx_users_status on users(status);

alter table company_custom_roles add column if not exists deleted_at timestamp(6) with time zone;
alter table company_custom_roles add column if not exists deleted_by_user_id bigint;

create index if not exists idx_company_custom_roles_active
  on company_custom_roles(company_id, deleted_at);
