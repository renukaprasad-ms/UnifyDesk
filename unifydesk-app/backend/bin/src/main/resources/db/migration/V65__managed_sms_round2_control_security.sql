create table managed_sms_compliance_actions (
  id bigint primary key,
  company_id bigint not null,
  actor_user_id bigint not null,
  resource_type varchar(40) not null,
  resource_id bigint not null,
  action varchar(64) not null,
  before_state_json text not null,
  after_state_json text not null,
  reason varchar(500),
  created_at timestamp(6) with time zone not null,
  constraint fk_managed_sms_compliance_action_company
    foreign key (company_id) references companies (id) on delete restrict,
  constraint fk_managed_sms_compliance_action_actor
    foreign key (actor_user_id) references users (id) on delete restrict
);

create index idx_managed_sms_compliance_action_company_created
  on managed_sms_compliance_actions (company_id, created_at desc);

create index idx_managed_sms_compliance_action_resource
  on managed_sms_compliance_actions (company_id, resource_type, resource_id, created_at desc);
