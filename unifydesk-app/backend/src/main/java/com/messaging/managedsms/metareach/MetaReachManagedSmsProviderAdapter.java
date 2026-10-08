package com.messaging.managedsms.metareach;

import com.messaging.managedsms.control.entity.*;
import com.messaging.managedsms.control.enums.ManagedSmsEnums.*;
import com.messaging.managedsms.control.provisioning.ManagedSmsProviderAdapter;
import java.time.Instant;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** No fabricated provider provisioning: manual DLT/TM evidence is mandatory. */
@Component @RequiredArgsConstructor
public class MetaReachManagedSmsProviderAdapter implements ManagedSmsProviderAdapter {
  private final MetaReachProperties props;
  private final JdbcTemplate jdbc;

  @Override public ProviderResult execute(ProviderRequest request) {
    return ProviderResult.actionRequired("METAREACH_MANUAL_MAPPING_REQUIRED",
      "Super Admin must verify the customer PE/TM mapping for MetaReach.");
  }

  @Override public BindingReadiness bindingReadiness(BindingContext ctx) {
    if (!props.ready() || ctx == null || ctx.binding() == null || ctx.tenant() == null
        || ctx.templateVersion() == null || ctx.header() == null)
      return notReady();
    ManagedSmsProviderBinding b=ctx.binding();
    ManagedSmsTenant t=ctx.tenant();
    ManagedSmsTemplateVersion v=ctx.templateVersion();
    ManagedSmsHeader h=ctx.header();
    if (!Objects.equals(b.getCompanyId(),t.getCompanyId())
        || !Objects.equals(b.getCompanyId(),v.getCompanyId())
        || !Objects.equals(b.getCompanyId(),h.getCompanyId())
        || !Objects.equals(b.getTenantId(),t.getId())
        || !Objects.equals(b.getTemplateVersionId(),v.getId())
        || !Objects.equals(b.getHeaderId(),h.getId())
        || t.getStatus()==TenantStatus.SUSPENDED || t.getStatus()==TenantStatus.ARCHIVED
        || h.getStatus()!=HeaderStatus.ACTIVE
        || h.getProviderMappingStatus()!=ProviderMappingStatus.READY
        || h.getStaleAfter()==null || !h.getStaleAfter().isAfter(Instant.now())
        || v.getDltStatus()!=DltTemplateStatus.APPROVED_DLT
        || v.getStaleAfter()==null || !v.getStaleAfter().isAfter(Instant.now())
        || !v.getDltTemplateId().equals(b.getProviderTemplateRef())
        || b.getProviderMappingRef()==null || b.getProviderMappingRef().isBlank()) return notReady();
    Integer approved=jdbc.queryForObject("""
      select count(*) from managed_sms_metareach_company_approvals a
      join managed_sms_headers hh on hh.company_id=a.company_id and hh.id=?
      join managed_sms_dlt_profiles p on p.id=hh.dlt_profile_id and p.company_id=a.company_id
      where a.company_id=? and a.active=true and p.pe_id=a.pe_id
        and p.status='ACTIVE' and p.tm_chain_status='ACTIVE'
      """,Integer.class,h.getId(),b.getCompanyId());
    return approved != null && approved == 1
      ? BindingReadiness.readyBinding() : notReady();
  }

  private static BindingReadiness notReady() {
    return BindingReadiness.notReady("METAREACH_MAPPING_NOT_VERIFIED",
      "MetaReach DLT template and PE/TM mapping require Super Admin review.");
  }
}
