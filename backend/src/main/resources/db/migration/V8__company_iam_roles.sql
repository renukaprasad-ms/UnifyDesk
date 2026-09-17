create table if not exists permissions (
  permission_key varchar(120) primary key,
  resource varchar(80) not null,
  action varchar(80) not null,
  description varchar(255) not null,
  assignable boolean not null default true
);

create table if not exists company_custom_roles (
  id bigint primary key,
  company_id bigint not null,
  name varchar(120) not null,
  description varchar(500),
  system_role boolean not null default false,
  created_by_user_id bigint not null,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_company_custom_roles_company
    foreign key (company_id) references companies(id) on delete cascade,
  constraint fk_company_custom_roles_created_by
    foreign key (created_by_user_id) references users(id)
);

create unique index if not exists uk_company_custom_roles_name_ci
  on company_custom_roles (company_id, lower(name));
create index if not exists idx_company_custom_roles_company
  on company_custom_roles (company_id);

create table if not exists company_custom_role_permissions (
  id bigint primary key,
  role_id bigint not null,
  permission_key varchar(120) not null,
  constraint uk_company_custom_role_permission unique (role_id, permission_key),
  constraint fk_company_custom_role_permissions_role
    foreign key (role_id) references company_custom_roles(id) on delete cascade,
  constraint fk_company_custom_role_permissions_permission
    foreign key (permission_key) references permissions(permission_key)
);

create index if not exists idx_company_custom_role_permissions_role
  on company_custom_role_permissions (role_id);
create index if not exists idx_company_custom_role_permissions_key
  on company_custom_role_permissions (permission_key);

create table if not exists company_member_role_assignments (
  id bigint primary key,
  company_id bigint not null,
  user_id bigint not null,
  role_id bigint not null,
  assigned_by_user_id bigint not null,
  expires_at timestamp(6) with time zone,
  revoked_at timestamp(6) with time zone,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint uk_company_member_role_assignment unique (company_id, user_id, role_id),
  constraint fk_company_member_role_assignments_company
    foreign key (company_id) references companies(id) on delete cascade,
  constraint fk_company_member_role_assignments_user
    foreign key (user_id) references users(id),
  constraint fk_company_member_role_assignments_role
    foreign key (role_id) references company_custom_roles(id) on delete cascade,
  constraint fk_company_member_role_assignments_assigned_by
    foreign key (assigned_by_user_id) references users(id)
);

create index if not exists idx_company_member_role_assignments_member
  on company_member_role_assignments (company_id, user_id);
create index if not exists idx_company_member_role_assignments_role
  on company_member_role_assignments (role_id);
create index if not exists idx_company_member_role_assignments_expires
  on company_member_role_assignments (expires_at);

insert into permissions (permission_key, resource, action, description, assignable) values
  ('company.read', 'company', 'read', 'View company information', true),
  ('company.modify', 'company', 'modify', 'Modify company information', true),
  ('company.delete', 'company', 'delete', 'Delete the company', false),
  ('company.transfer_ownership', 'company', 'transfer_ownership', 'Transfer company ownership', false),

  ('user.read', 'user', 'read', 'View company members', true),
  ('user.invite', 'user', 'invite', 'Invite company members', true),
  ('user.modify', 'user', 'modify', 'Modify company members', true),
  ('user.remove', 'user', 'remove', 'Remove company members', true),

  ('role.read', 'role', 'read', 'View roles and permission definitions', true),
  ('role.create', 'role', 'create', 'Create company roles', true),
  ('role.modify', 'role', 'modify', 'Modify company roles', true),
  ('role.delete', 'role', 'delete', 'Delete company roles', true),
  ('role.assign', 'role', 'assign', 'Assign and revoke company roles', true),

  ('email.connect', 'email', 'connect', 'Connect an email account', true),
  ('email.disconnect', 'email', 'disconnect', 'Disconnect an email account', true),
  ('email.read', 'email', 'read', 'Read email conversations and account configuration', true),
  ('email.send', 'email', 'send', 'Send email', true),
  ('email.modify', 'email', 'modify', 'Modify email configuration and messages', true),

  ('whatsapp.connect', 'whatsapp', 'connect', 'Connect a WhatsApp account', true),
  ('whatsapp.disconnect', 'whatsapp', 'disconnect', 'Disconnect a WhatsApp account', true),
  ('whatsapp.read', 'whatsapp', 'read', 'Read WhatsApp conversations and configuration', true),
  ('whatsapp.send', 'whatsapp', 'send', 'Send WhatsApp messages', true),
  ('whatsapp.modify', 'whatsapp', 'modify', 'Modify WhatsApp configuration and messages', true),
  ('whatsapp.template.read', 'whatsapp.template', 'read', 'View WhatsApp templates', true),
  ('whatsapp.template.create', 'whatsapp.template', 'create', 'Create WhatsApp templates', true),
  ('whatsapp.template.modify', 'whatsapp.template', 'modify', 'Modify WhatsApp templates', true),
  ('whatsapp.template.delete', 'whatsapp.template', 'delete', 'Delete WhatsApp templates', true),

  ('sms.connect', 'sms', 'connect', 'Connect an SMS provider', true),
  ('sms.disconnect', 'sms', 'disconnect', 'Disconnect an SMS provider', true),
  ('sms.read', 'sms', 'read', 'View SMS configuration and message history', true),
  ('sms.send', 'sms', 'send', 'Send SMS messages', true),
  ('sms.modify', 'sms', 'modify', 'Modify SMS configuration', true),
  ('sms.sender.read', 'sms.sender', 'read', 'View SMS sender identities', true),
  ('sms.sender.create', 'sms.sender', 'create', 'Create SMS sender identities', true),
  ('sms.sender.modify', 'sms.sender', 'modify', 'Modify SMS sender identities', true),
  ('sms.sender.delete', 'sms.sender', 'delete', 'Delete SMS sender identities', true),

  ('broadcast.read', 'broadcast', 'read', 'View broadcasts', true),
  ('broadcast.create', 'broadcast', 'create', 'Create broadcasts', true),
  ('broadcast.send', 'broadcast', 'send', 'Send broadcasts', true),
  ('broadcast.modify', 'broadcast', 'modify', 'Modify broadcasts', true),
  ('broadcast.delete', 'broadcast', 'delete', 'Delete broadcasts', true),

  ('template.read', 'template', 'read', 'View templates', true),
  ('template.create', 'template', 'create', 'Create templates', true),
  ('template.modify', 'template', 'modify', 'Modify templates', true),
  ('template.delete', 'template', 'delete', 'Delete templates', true),

  ('billing.read', 'billing', 'read', 'View billing information', true),
  ('billing.balance.read', 'billing.balance', 'read', 'View account balance', true),
  ('billing.balance.add', 'billing.balance', 'add', 'Add account balance', true),
  ('billing.card.read', 'billing.card', 'read', 'View payment methods', true),
  ('billing.card.add', 'billing.card', 'add', 'Add payment methods', true),
  ('billing.card.modify', 'billing.card', 'modify', 'Modify payment methods', true),
  ('billing.card.delete', 'billing.card', 'delete', 'Delete payment methods', true),
  ('billing.invoice.read', 'billing.invoice', 'read', 'View invoices', true),
  ('billing.invoice.download', 'billing.invoice', 'download', 'Download invoices', true),
  ('billing.subscription.read', 'billing.subscription', 'read', 'View subscription details', true),
  ('billing.subscription.modify', 'billing.subscription', 'modify', 'Modify subscription details', true),
  ('billing.subscription.cancel', 'billing.subscription', 'cancel', 'Cancel a subscription', true),

  ('ads.read', 'ads', 'read', 'View ad accounts and campaigns', true),
  ('ads.create', 'ads', 'create', 'Create advertising campaigns', true),
  ('ads.modify', 'ads', 'modify', 'Modify advertising campaigns', true),
  ('ads.delete', 'ads', 'delete', 'Delete advertising campaigns', true),
  ('ads.balance.add', 'ads.balance', 'add', 'Add advertising provider balance', true),

  ('media.read', 'media', 'read', 'View company media', true),
  ('media.upload', 'media', 'upload', 'Upload company media', true),
  ('media.delete', 'media', 'delete', 'Delete company media', true),

  ('settings.read', 'settings', 'read', 'View company settings', true),
  ('settings.modify', 'settings', 'modify', 'Modify company settings', true)
on conflict (permission_key) do update set
  resource = excluded.resource,
  action = excluded.action,
  description = excluded.description,
  assignable = excluded.assignable;
