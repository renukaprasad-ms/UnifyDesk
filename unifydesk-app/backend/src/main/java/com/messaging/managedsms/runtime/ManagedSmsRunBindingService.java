package com.messaging.managedsms.runtime;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManagedSmsRunBindingService {

  private final JdbcTemplate jdbcTemplate;
  private final com.messaging.admin.service.PlatformManagedProviderService providerRoutes;

  @Transactional
  public void bind(
      Long runId,
      ManagedSmsRuntimeRepository.ManagedTemplateRuntime runtime) {
    jdbcTemplate.update(
        """
        insert into managed_sms_run_bindings (
          run_id, company_id, tenant_id, dlt_profile_id, pe_id_snapshot,
          header_id, header_snapshot, template_id, template_version_id,
          dlt_template_id_snapshot, approved_body_snapshot, body_fingerprint_snapshot,
          variables_schema_snapshot, cta_policy_snapshot, provider_binding_id,
          provider_client_ref_snapshot, provider_template_ref_snapshot,
          provider_flow_ref_snapshot, ruleset_version_snapshot, delivery_provider, created_at
        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
        on conflict (run_id) do nothing
        """,
        runId,
        runtime.companyId(),
        runtime.tenantId(),
        runtime.dltProfileId(),
        runtime.peId(),
        runtime.headerId(),
        runtime.header(),
        runtime.templateId(),
        runtime.templateVersionId(),
        runtime.dltTemplateId(),
        runtime.approvedBody(),
        runtime.bodyFingerprint(),
        runtime.variablesSchemaJson(),
        runtime.ctaPolicyJson(),
        runtime.providerBindingId(),
        runtime.providerAccountKey(),
        runtime.providerTemplateRef(),
        runtime.providerFlowRef(),
        runtime.rulesetVersion(),
        providerRoutes.smsProviderForNewRun());
  }
}
