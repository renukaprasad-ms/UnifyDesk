-- Manual mapping of a customer's approved DLT Principal Entity to the MetaReach
-- master account, recorded by Super Admin after checking provider permissions.
create table managed_sms_metareach_company_approvals (
 company_id bigint primary key references companies(id),
 pe_id varchar(100) not null,
 evidence_reference varchar(255) not null,
 approved_by bigint not null references users(id),
 approved_at timestamptz not null,
 active boolean not null default true,
 reviewed_reason varchar(500) not null
);
create index idx_metareach_company_active on managed_sms_metareach_company_approvals(active,approved_at desc);
