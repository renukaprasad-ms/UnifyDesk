-- Managed SMS integration ownership constraints.
-- Added by Lead after B1/B2 integration so isolated worker branches remained independently compilable.

create unique index if not exists uk_managed_sms_tenants_company_id_id
  on managed_sms_tenants(company_id, id);
create unique index if not exists uk_managed_sms_profiles_company_id_id
  on managed_sms_dlt_profiles(company_id, id);
create unique index if not exists uk_managed_sms_headers_company_id_id
  on managed_sms_headers(company_id, id);
create unique index if not exists uk_managed_sms_templates_company_id_id
  on managed_sms_templates(company_id, id);
create unique index if not exists uk_managed_sms_template_versions_company_id_id
  on managed_sms_template_versions(company_id, id);
create unique index if not exists uk_managed_sms_provider_bindings_company_id_id
  on managed_sms_provider_bindings(company_id, id);

alter table managed_sms_dlt_profiles
  add constraint fk_managed_sms_profile_company_tenant
  foreign key (company_id, tenant_id)
  references managed_sms_tenants(company_id, id);

alter table managed_sms_headers
  add constraint fk_managed_sms_header_company_profile
  foreign key (company_id, dlt_profile_id)
  references managed_sms_dlt_profiles(company_id, id);

alter table managed_sms_template_versions
  add constraint fk_managed_sms_template_version_company_template
  foreign key (company_id, template_id)
  references managed_sms_templates(company_id, id);

alter table managed_sms_template_headers
  add constraint fk_managed_sms_template_header_company_version
  foreign key (company_id, template_version_id)
  references managed_sms_template_versions(company_id, id),
  add constraint fk_managed_sms_template_header_company_header
  foreign key (company_id, header_id)
  references managed_sms_headers(company_id, id);

alter table managed_sms_provider_bindings
  add constraint fk_managed_sms_provider_binding_company_tenant
  foreign key (company_id, tenant_id)
  references managed_sms_tenants(company_id, id),
  add constraint fk_managed_sms_provider_binding_company_version
  foreign key (company_id, template_version_id)
  references managed_sms_template_versions(company_id, id),
  add constraint fk_managed_sms_provider_binding_company_header
  foreign key (company_id, header_id)
  references managed_sms_headers(company_id, id);

alter table managed_sms_sender_bindings
  add constraint fk_managed_sms_sender_binding_company_tenant
  foreign key (company_id, tenant_id)
  references managed_sms_tenants(company_id, id),
  add constraint fk_managed_sms_sender_binding_company_header
  foreign key (company_id, header_id)
  references managed_sms_headers(company_id, id);

alter table managed_sms_run_bindings
  add constraint fk_managed_sms_run_binding_company_tenant
  foreign key (company_id, tenant_id)
  references managed_sms_tenants(company_id, id),
  add constraint fk_managed_sms_run_binding_company_profile
  foreign key (company_id, dlt_profile_id)
  references managed_sms_dlt_profiles(company_id, id),
  add constraint fk_managed_sms_run_binding_company_header
  foreign key (company_id, header_id)
  references managed_sms_headers(company_id, id),
  add constraint fk_managed_sms_run_binding_company_template
  foreign key (company_id, template_id)
  references managed_sms_templates(company_id, id),
  add constraint fk_managed_sms_run_binding_company_template_version
  foreign key (company_id, template_version_id)
  references managed_sms_template_versions(company_id, id),
  add constraint fk_managed_sms_run_binding_company_provider_binding
  foreign key (company_id, provider_binding_id)
  references managed_sms_provider_bindings(company_id, id);
