create table if not exists company_preferences (
  company_id bigint primary key,
  brand_color varchar(7) not null,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_company_preferences_company
    foreign key (company_id) references companies(id) on delete cascade,
  constraint ck_company_preferences_brand_color
    check (brand_color ~ '^#[0-9A-Fa-f]{6}$')
);

insert into company_preferences (company_id, brand_color, created_at, updated_at)
select id, '#2563EB', now(), now()
from companies
on conflict (company_id) do nothing;

create table if not exists user_preferences (
  user_id bigint primary key,
  appearance_mode varchar(16) not null default 'LIGHT',
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_user_preferences_user
    foreign key (user_id) references users(id) on delete cascade,
  constraint ck_user_preferences_appearance_mode
    check (appearance_mode in ('LIGHT', 'DARK'))
);

drop index if exists uk_company_custom_roles_name_ci;
create unique index if not exists uk_company_custom_roles_name_ci
  on company_custom_roles (company_id, lower(name))
  where deleted_at is null;
