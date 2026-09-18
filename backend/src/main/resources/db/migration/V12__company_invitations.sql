create table if not exists company_invitations (
  id bigint primary key,
  company_id bigint not null,
  email varchar(320) not null,
  status varchar(32) not null,
  token_hash varchar(64) not null,
  invited_by_user_id bigint not null,
  expires_at timestamp(6) with time zone not null,
  accepted_at timestamp(6) with time zone,
  accepted_by_user_id bigint,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_company_invitations_company
    foreign key (company_id) references companies(id),
  constraint fk_company_invitations_invited_by
    foreign key (invited_by_user_id) references users(id),
  constraint fk_company_invitations_accepted_by
    foreign key (accepted_by_user_id) references users(id),
  constraint uk_company_invitations_token_hash unique (token_hash)
);

create table if not exists company_invitation_roles (
  invitation_id bigint not null,
  role_id bigint not null,
  primary key (invitation_id, role_id),
  constraint fk_company_invitation_roles_invitation
    foreign key (invitation_id) references company_invitations(id) on delete cascade,
  constraint fk_company_invitation_roles_role
    foreign key (role_id) references company_custom_roles(id) on delete cascade
);

create index if not exists idx_company_invitations_company_status
  on company_invitations (company_id, status);

create index if not exists idx_company_invitations_email
  on company_invitations (lower(email));

create index if not exists idx_company_invitations_expires
  on company_invitations (expires_at);

create unique index if not exists uk_company_invitations_pending_email
  on company_invitations (company_id, lower(email))
  where status = 'PENDING';

create index if not exists idx_company_invitation_roles_role
  on company_invitation_roles (role_id);
