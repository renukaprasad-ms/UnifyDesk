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
  private final com.messaging.admin.service.PlatformManagedProviderService routes;
  private final com.messaging.managedsms.metareach.MetaReachProperties metaReach;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;

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
    boolean metareachSelected = "METAREACH_SMS".equals(routes.smsProviderForNewRun());
    Integer metaApproved = metareachSelected && metaReach.ready()
        ? jdbc.queryForObject("""
            select count(*) from managed_sms_metareach_company_approvals
            where company_id=? and active=true
            """, Integer.class,companyId) : 0;
    boolean providerTenantReady =
        (metareachSelected && Integer.valueOf(1).equals(metaApproved))
        || tenant.getProviderMode() == ProviderMode.SHARED_MASTER
        || (tenant.getProviderClientRef() != null && !tenant.getProviderClientRef().isBlank());

    boolean ready =
        properties.isEnabled()
            && profileReady
            && providerTenantReady
            && headerCount > 0
            && templateCount > 0
            && bindingRepository.findByCompanyId(companyId).stream()
                .anyMatch(binding -> binding.getStatus() == ProviderBindingStatus.ACTIVE
                    && (!metareachSelected
                      || (metaReach.ready()
                          && binding.getProviderTemplateRef()!=null
                          && !binding.getProviderTemplateRef().isBlank()
                          && binding.getProviderMappingRef()!=null
                          && !binding.getProviderMappingRef().isBlank())));
    tenantService.updateSendingState(tenant, ready);
    return tenant;
  }

  public boolean fresh(Instant staleAfter) {
    return staleAfter != null && staleAfter.isAfter(Instant.now());
  }
}
