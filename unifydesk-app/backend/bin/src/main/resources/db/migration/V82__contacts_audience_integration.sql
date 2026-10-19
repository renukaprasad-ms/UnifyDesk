-- Contacts & Audience Round-1 Lead integration.
-- Adds cross-worker tenant-safe constraints and Broadcast audience provenance.

-- Composite uniqueness enables tenant-safe foreign keys without changing Snowflake primary keys.
create unique index uk_contacts_company_id
  on contacts(company_id, id);
create unique index uk_contact_identities_company_id
  on contact_identities(company_id, id);
create unique index uk_contact_lists_company_id
  on contact_lists(company_id, id);
create unique index uk_contact_segments_company_id
  on contact_segments(company_id, id);
create unique index uk_contact_suppressions_company_id
  on contact_suppressions(company_id, id);
create unique index uk_contact_imports_company_id
  on contact_imports(company_id, id);
create unique index uk_contact_audience_snapshots_company_id
  on contact_audience_snapshots(company_id, id);

-- B2 -> B1/core tenant-safe ownership constraints.
alter table contact_segments
  add constraint fk_contact_segments_company
    foreign key (company_id) references companies(id) on delete restrict,
  add constraint fk_contact_segments_creator
    foreign key (created_by_user_id) references users(id) on delete restrict;

alter table contact_channel_preferences
  add constraint fk_contact_preferences_company
    foreign key (company_id) references companies(id) on delete restrict,
  add constraint fk_contact_preferences_contact
    foreign key (company_id, contact_id)
    references contacts(company_id, id) on delete cascade,
  add constraint fk_contact_preferences_identity
    foreign key (company_id, identity_id)
    references contact_identities(company_id, id) on delete cascade;

alter table contact_preference_events
  alter column contact_id drop not null,
  add constraint fk_contact_preference_events_company
    foreign key (company_id) references companies(id) on delete restrict,
  add constraint fk_contact_preference_events_contact
    foreign key (contact_id) references contacts(id) on delete set null,
  add constraint fk_contact_preference_events_identity
    foreign key (identity_id) references contact_identities(id) on delete set null;

alter table contact_suppressions
  add constraint fk_contact_suppressions_company
    foreign key (company_id) references companies(id) on delete restrict,
  add constraint fk_contact_suppressions_contact
    foreign key (contact_id) references contacts(id) on delete set null,
  add constraint fk_contact_suppressions_identity
    foreign key (identity_id) references contact_identities(id) on delete set null;

alter table contact_suppression_events
  add constraint fk_contact_suppression_events_company
    foreign key (company_id) references companies(id) on delete restrict,
  add constraint fk_contact_suppression_events_suppression
    foreign key (company_id, suppression_id)
    references contact_suppressions(company_id, id) on delete restrict,
  add constraint fk_contact_suppression_events_contact
    foreign key (contact_id) references contacts(id) on delete set null,
  add constraint fk_contact_suppression_events_identity
    foreign key (identity_id) references contact_identities(id) on delete set null;

alter table contact_imports
  add constraint fk_contact_imports_company
    foreign key (company_id) references companies(id) on delete restrict,
  add constraint fk_contact_imports_requester
    foreign key (requested_by_user_id) references users(id) on delete restrict,
  add constraint fk_contact_imports_target_list
    foreign key (target_list_id) references contact_lists(id) on delete set null;

alter table contact_import_error_rows
  add constraint fk_contact_import_errors_import
    foreign key (company_id, import_id)
    references contact_imports(company_id, id) on delete cascade;

alter table contact_exports
  add constraint fk_contact_exports_company
    foreign key (company_id) references companies(id) on delete restrict,
  add constraint fk_contact_exports_requester
    foreign key (requested_by_user_id) references users(id) on delete restrict;

alter table contact_bulk_operations
  add constraint fk_contact_bulk_company
    foreign key (company_id) references companies(id) on delete restrict,
  add constraint fk_contact_bulk_requester
    foreign key (requested_by_user_id) references users(id) on delete restrict;

alter table contact_activity_events
  add constraint fk_contact_activity_company
    foreign key (company_id) references companies(id) on delete restrict,
  add constraint fk_contact_activity_contact
    foreign key (company_id, contact_id)
    references contacts(company_id, id) on delete cascade;

alter table contact_audience_snapshots
  add constraint fk_contact_audience_snapshots_company
    foreign key (company_id) references companies(id) on delete restrict;

-- Snapshot membership is immutable send provenance. Contact/identity references are optional
-- provenance after privacy purge; the normalized destination is already the historical snapshot.
alter table contact_audience_snapshot_members
  drop constraint contact_audience_snapshot_members_pkey,
  alter column contact_id drop not null,
  alter column identity_id drop not null,
  add constraint pk_contact_audience_snapshot_members
    primary key (snapshot_id, sequence_id),
  add constraint fk_contact_audience_members_snapshot
    foreign key (company_id, snapshot_id)
    references contact_audience_snapshots(company_id, id) on delete cascade,
  add constraint fk_contact_audience_members_contact
    foreign key (contact_id) references contacts(id) on delete set null,
  add constraint fk_contact_audience_members_identity
    foreign key (identity_id) references contact_identities(id) on delete set null;

create unique index uk_contact_audience_snapshot_contact
  on contact_audience_snapshot_members(snapshot_id, contact_id)
  where contact_id is not null;

-- Broadcast remains backward compatible with group-based messages while allowing a frozen
-- Contacts List/Segment as the alternative source.
alter table broadcast_messages
  alter column group_id drop not null,
  add column audience_source_type varchar(16),
  add column audience_source_id bigint,
  add constraint ck_broadcast_message_audience_source check (
    (group_id is not null and audience_source_type is null and audience_source_id is null)
    or
    (group_id is null and audience_source_type in ('LIST','SEGMENT') and audience_source_id is not null)
  );

create index idx_broadcast_messages_audience_source
  on broadcast_messages(company_id, audience_source_type, audience_source_id, created_at desc)
  where audience_source_type is not null;

alter table broadcast_runs
  alter column group_id drop not null,
  add column audience_source_type varchar(16),
  add column audience_source_id bigint,
  add column audience_snapshot_id bigint,
  add constraint ck_broadcast_run_audience_source check (
    (group_id is not null and audience_source_type is null and audience_source_id is null
      and audience_snapshot_id is null)
    or
    (group_id is null and audience_source_type in ('LIST','SEGMENT') and audience_source_id is not null
      and audience_snapshot_id is not null)
  ),
  add constraint fk_broadcast_run_audience_snapshot
    foreign key (company_id, audience_snapshot_id)
    references contact_audience_snapshots(company_id, id) on delete restrict;

alter table broadcast_recipients
  alter column source_member_id drop not null,
  add column contact_id bigint,
  add column contact_identity_id bigint,
  add column audience_snapshot_id bigint,
  add column audience_snapshot_sequence_id bigint,
  add constraint ck_broadcast_recipient_source check (
    (source_member_id is not null and contact_id is null and contact_identity_id is null
      and audience_snapshot_id is null and audience_snapshot_sequence_id is null)
    or
    (source_member_id is null
      and audience_snapshot_id is not null and audience_snapshot_sequence_id is not null)
  ),
  add constraint fk_broadcast_recipient_contact
    foreign key (contact_id) references contacts(id) on delete set null,
  add constraint fk_broadcast_recipient_identity
    foreign key (contact_identity_id) references contact_identities(id) on delete set null,
  add constraint fk_broadcast_recipient_audience_snapshot
    foreign key (company_id, audience_snapshot_id)
    references contact_audience_snapshots(company_id, id) on delete restrict;

create index idx_broadcast_recipients_contact_activity
  on broadcast_recipients(company_id, contact_id, updated_at desc, id desc)
  where contact_id is not null;

create unique index uk_broadcast_recipient_audience_member
  on broadcast_recipients(audience_snapshot_id, audience_snapshot_sequence_id)
  where audience_snapshot_id is not null;
