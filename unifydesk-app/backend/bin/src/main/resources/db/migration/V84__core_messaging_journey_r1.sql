-- Core Messaging Journey R1: immutable Broadcast audience candidates + WhatsApp campaign snapshots.

alter table contact_audience_snapshots
  drop constraint if exists ck_contact_audience_snapshot_source,
  add constraint ck_contact_audience_snapshot_source
    check (source_type in ('LIST','SEGMENT','CONTACTS'));

create table contact_audience_selections (
  id bigint primary key,
  company_id bigint not null,
  selection_hash varchar(64) not null,
  candidate_count integer not null,
  created_by_user_id bigint not null,
  created_at timestamp(6) with time zone not null,
  constraint fk_contact_audience_selections_company
    foreign key (company_id) references companies(id) on delete cascade,
  constraint fk_contact_audience_selections_user
    foreign key (created_by_user_id) references users(id) on delete restrict,
  constraint ck_contact_audience_selections_count check (candidate_count > 0)
);

create unique index uk_contact_audience_selections_company_id
  on contact_audience_selections(company_id, id);

create table contact_audience_selection_members (
  selection_id bigint not null,
  company_id bigint not null,
  contact_id bigint not null,
  sequence_id integer not null,
  created_at timestamp(6) with time zone not null,
  primary key (selection_id, sequence_id),
  constraint fk_contact_audience_selection_members_selection
    foreign key (company_id, selection_id)
    references contact_audience_selections(company_id, id) on delete cascade,
  constraint fk_contact_audience_selection_members_contact
    foreign key (contact_id) references contacts(id) on delete cascade,
  constraint uk_contact_audience_selection_contact unique(selection_id, contact_id)
);

create index idx_contact_audience_selection_members_page
  on contact_audience_selection_members(company_id, selection_id, contact_id);

-- Deliberately no Contact FK: this table is immutable send provenance. If a Contact is
-- privacy-purged after freeze but before materialization, eligibility fails closed later.
create table contact_audience_snapshot_candidates (
  snapshot_id bigint not null,
  company_id bigint not null,
  contact_id bigint not null,
  created_at timestamp(6) with time zone not null,
  primary key (snapshot_id, contact_id),
  constraint fk_contact_audience_snapshot_candidates_snapshot
    foreign key (company_id, snapshot_id)
    references contact_audience_snapshots(company_id, id) on delete cascade
);

create index idx_contact_audience_snapshot_candidates_page
  on contact_audience_snapshot_candidates(company_id, snapshot_id, contact_id);

alter table broadcast_messages
  drop constraint if exists ck_broadcast_message_audience_source,
  add constraint ck_broadcast_message_audience_source check (
    (group_id is not null and audience_source_type is null and audience_source_id is null)
    or
    (group_id is null and audience_source_type in ('LIST','SEGMENT','CONTACTS')
      and audience_source_id is not null)
  ),
  add column frozen_audience_snapshot_id bigint,
  add column whatsapp_template_id bigint,
  add column whatsapp_template_parameters_json text not null default '{}',
  add constraint fk_broadcast_message_frozen_audience_snapshot
    foreign key (company_id, frozen_audience_snapshot_id)
    references contact_audience_snapshots(company_id, id) on delete restrict;

create unique index uk_broadcast_message_frozen_audience_snapshot
  on broadcast_messages(frozen_audience_snapshot_id)
  where frozen_audience_snapshot_id is not null;

alter table broadcast_runs
  drop constraint if exists ck_broadcast_run_audience_source,
  add constraint ck_broadcast_run_audience_source check (
    (group_id is not null and audience_source_type is null and audience_source_id is null
      and audience_snapshot_id is null)
    or
    (group_id is null and audience_source_type in ('LIST','SEGMENT','CONTACTS')
      and audience_source_id is not null and audience_snapshot_id is not null)
  ),
  add column whatsapp_template_id_snapshot bigint,
  add column whatsapp_template_name_snapshot varchar(512),
  add column whatsapp_template_language_snapshot varchar(64),
  add column whatsapp_template_components_json_snapshot text;

create index idx_broadcast_messages_frozen_snapshot
  on broadcast_messages(company_id, frozen_audience_snapshot_id)
  where frozen_audience_snapshot_id is not null;
