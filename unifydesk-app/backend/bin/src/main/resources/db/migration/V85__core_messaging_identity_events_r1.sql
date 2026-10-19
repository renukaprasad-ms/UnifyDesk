-- Core Messaging Journey Round-1: Inbox <-> Contact identity projection.
-- Contacts remain authoritative. Inbox keeps working for unknown participants and therefore
-- intentionally has no hard foreign key to contacts/contact_identities.

create unique index if not exists uk_inbox_conversations_company_id
  on inbox_conversations(company_id, id);

create table inbox_participant_contact_links (
  id bigint primary key,
  company_id bigint not null,
  conversation_id bigint not null,
  participant_address varchar(320) not null,
  identity_type varchar(16) not null,
  normalized_identity varchar(320),
  contact_id bigint,
  contact_identity_id bigint,
  resolution varchar(16) not null,
  resolved_at timestamp(6) with time zone not null,
  created_at timestamp(6) with time zone not null,
  updated_at timestamp(6) with time zone not null,
  constraint fk_inbox_contact_links_company
    foreign key (company_id) references companies(id) on delete cascade,
  constraint fk_inbox_contact_links_conversation
    foreign key (company_id, conversation_id)
    references inbox_conversations(company_id, id) on delete cascade,
  constraint ck_inbox_contact_links_identity_type check (identity_type in ('EMAIL', 'PHONE')),
  constraint ck_inbox_contact_links_resolution
    check (resolution in ('MATCHED', 'UNMATCHED', 'AMBIGUOUS')),
  constraint ck_inbox_contact_links_match_shape check (
    (resolution = 'MATCHED'
      and normalized_identity is not null
      and contact_id is not null
      and contact_identity_id is not null)
    or
    (resolution <> 'MATCHED'
      and contact_id is null
      and contact_identity_id is null)
  ),
  constraint uk_inbox_contact_links_participant
    unique (company_id, conversation_id, identity_type, participant_address)
);

create index idx_inbox_contact_links_conversation
  on inbox_participant_contact_links(company_id, conversation_id);

create index idx_inbox_contact_links_contact_activity
  on inbox_participant_contact_links(company_id, contact_id, conversation_id)
  where resolution = 'MATCHED' and contact_id is not null;

create index idx_inbox_contact_links_reconcile
  on inbox_participant_contact_links(resolution, resolved_at, id)
  where resolution = 'UNMATCHED';
