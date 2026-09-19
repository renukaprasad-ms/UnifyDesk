create table if not exists async_jobs (
  id bigint primary key,
  company_id bigint,
  user_id bigint not null,
  job_type varchar(48) not null,
  status varchar(24) not null,
  total_units bigint not null default 0,
  completed_units bigint not null default 0,
  progress_percent integer not null default 0,
  status_message varchar(500),
  metadata_json text,
  error_message varchar(1000),
  created_at timestamp(6) with time zone not null,
  started_at timestamp(6) with time zone,
  completed_at timestamp(6) with time zone,
  updated_at timestamp(6) with time zone not null,
  constraint fk_async_jobs_company foreign key (company_id) references companies(id) on delete cascade,
  constraint fk_async_jobs_user foreign key (user_id) references users(id)
);

create index if not exists idx_async_jobs_user_created on async_jobs(user_id, created_at desc);
create index if not exists idx_async_jobs_company_status on async_jobs(company_id, status);
