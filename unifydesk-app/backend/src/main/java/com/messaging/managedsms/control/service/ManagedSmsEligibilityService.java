package com.messaging.managedsms.control.service;

import com.messaging.managedsms.control.config.ManagedSmsProperties;
import com.messaging.managedsms.control.entity.*;
import com.messaging.managedsms.control.enums.ManagedSmsEnums.*;
import com.messaging.managedsms.control.repository.*;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManagedSmsEligibilityService {

  private final ManagedSmsTenantRepository tenantRepository;
  private final ManagedSmsDltProfileRepository profileRepository;
  private final ManagedSmsHeaderRepository headerRepository;
  private final ManagedSmsTemplateVersionRepository versionRepository;
  private final ManagedSmsProviderBindingRepository bindingRepository;
  private final ManagedSmsTenantService tenantService;
  private final ManagedSmsProperties properties;
  private final com.messaging.managedsms.metareach.MetaReachProperties metaReach;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;
  private final com.messaging.managedsms.control.msg91.Msg91ManagedSmsProviderAdapter msg91Adapter;
  private final com.messaging.managedsms.metareach.MetaReachManagedSmsProviderAdapter metaReachAdapter;

  @Transactional
  public ManagedSmsTenant recalculate(Long companyId) {
    ManagedSmsTenant tenant = tenantRepository.findByCompanyId(companyId).orElse(null);
    if (tenant == null) return null;
    if (tenant.getStatus() == TenantStatus.SUSPENDED || tenant.getStatus() == TenantStatus.ARCHIVED) {
      tenantService.updateSendingState(tenant, false);
      return tenant;
    }

    ManagedSmsDltProfile profile =
        profileRepository
            .findFirstByCompanyIdAndArchivedAtIsNullOrderByCreatedAtDesc(companyId)
            .orElse(null);
    boolean profileReady =
        profile != null
            && profile.getStatus() == DltProfileStatus.ACTIVE
            && profile.getTmChainStatus() == TmChainStatus.ACTIVE
            && fresh(profile.getStaleAfter());

    long headerCount =
        headerRepository.countByCompanyIdAndStatusAndProviderMappingStatusAndArchivedAtIsNull(
            companyId, HeaderStatus.ACTIVE, ProviderMappingStatus.READY);
    long templateCount = versionRepository.countByCompanyIdAndStatus(companyId, TemplateVersionStatus.ACTIVE);
    Integer metaApproved = metaReach.ready()
        ? jdbc.queryForObject("""
            select count(*) from managed_sms_metareach_company_approvals
            where company_id=? and active=true
            """, Integer.class,companyId) : 0;
    boolean metaTenantReady = Integer.valueOf(1).equals(metaApproved);
    boolean msg91TenantReady = tenant.getProviderMode() == ProviderMode.SHARED_MASTER
        || (tenant.getProviderClientRef() != null && !tenant.getProviderClientRef().isBlank());

    boolean ready =
        properties.isEnabled()
            && profileReady
            && headerCount > 0
            && templateCount > 0
            && bindingRepository.findByCompanyId(companyId).stream()
                .anyMatch(binding -> {
                  if (binding.getStatus() != ProviderBindingStatus.ACTIVE) return false;
                  var version=versionRepository.findByIdAndCompanyId(
                      binding.getTemplateVersionId(),companyId).orElse(null);
                  var header=headerRepository.findByIdAndCompanyId(
                      binding.getHeaderId(),companyId).orElse(null);
                  if (version==null || header==null) return false;
                  var ctx=new com.messaging.managedsms.control.provisioning.ManagedSmsProviderAdapter.BindingContext(
                      binding,tenant,version,header);
                  boolean msg91Ready=msg91TenantReady
                      && msg91Adapter.bindingReadiness(ctx).ready();
                  boolean metaReady=metaTenantReady
                      && metaReachAdapter.bindingReadiness(ctx).ready();
                  return msg91Ready || metaReady;
                });
    tenantService.updateSendingState(tenant, ready);
    return tenant;
  }

  public boolean fresh(Instant staleAfter) {
    return staleAfter != null && staleAfter.isAfter(Instant.now());
  }
}
