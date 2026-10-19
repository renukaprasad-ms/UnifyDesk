-- Contacts & Audience Round-2 security/integrity hardening.

-- Company purge clears merged tombstones before physical deletion, but retain the normal
-- application-level RESTRICT semantics for explicit non-purge contact deletion.

-- A current identity-scoped preference/suppression must point to an identity owned by
-- the same company/contact. First reconcile malformed Round-1 rows conservatively.
delete from contact_channel_preferences p
where p.identity_id is not null
  and not exists (
    select 1
    from contact_identities i
    where i.company_id = p.company_id
      and i.contact_id = p.contact_id
      and i.id = p.identity_id
  );

update contact_suppressions s
set identity_id = null,
    updated_at = now(),
    version = version + 1
where s.identity_id is not null
  and s.contact_id is not null
  and not exists (
    select 1
    from contact_identities i
    where i.company_id = s.company_id
      and i.contact_id = s.contact_id
      and i.id = s.identity_id
  );

create unique index uk_contact_identities_company_contact_id
  on contact_identities(company_id, contact_id, id);

alter table contact_channel_preferences
  add constraint fk_contact_preferences_owned_identity
    foreign key (company_id, contact_id, identity_id)
    references contact_identities(company_id, contact_id, id)
    on delete cascade;

-- Keep the Contact/company columns when an identity is physically purged. PostgreSQL's
-- column-list SET NULL makes only identity_id null, preserving a conservative contact-level
-- suppression rather than dropping the suppression.
alter table contact_suppressions
  add constraint fk_contact_suppressions_owned_identity
    foreign key (company_id, contact_id, identity_id)
    references contact_identities(company_id, contact_id, id)
    on delete set null (identity_id);
