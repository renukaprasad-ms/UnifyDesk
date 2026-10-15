create table if not exists crm_sales_quotas (
  id bigint not null,
  company_id bigint not null,
  user_id bigint not null,
  period_type varchar(20) not null,
  period_key varchar(20) not null,
  target_amount numeric(20,3) not null default 0,
  currency varchar(3) not null default 'INR',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint pk_crm_sales_quotas primary key (id),
  constraint uq_crm_sales_quotas unique (company_id, user_id, period_type, period_key, currency),
  constraint fk_crm_sales_quotas_company foreign key (company_id) references companies(id) on delete cascade,
  constraint fk_crm_sales_quotas_user foreign key (user_id) references users(id) on delete cascade,
  constraint ck_crm_sales_quotas_period_type check (period_type in ('MONTHLY', 'QUARTERLY')),
  constraint ck_crm_sales_quotas_target check (target_amount >= 0)
);

create index if not exists idx_crm_sales_quotas_company_period
  on crm_sales_quotas (company_id, period_type, period_key);
