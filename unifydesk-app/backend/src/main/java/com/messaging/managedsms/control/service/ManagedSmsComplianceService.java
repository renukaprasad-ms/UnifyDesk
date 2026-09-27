package com.messaging.managedsms.control.service;

import com.messaging.managedsms.control.entity.*;
import com.messaging.managedsms.control.enums.ManagedSmsEnums.*;
import com.messaging.managedsms.control.provisioning.ManagedSmsProvisioningService;
import com.messaging.managedsms.control.repository.*;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManagedSmsComplianceService {

  private final ManagedSmsDltProfileRepository profileRepository;
  private final ManagedSmsHeaderRepository headerRepository;
  private final ManagedSmsTemplateRepository templateRepository;
  private final ManagedSmsTemplateVersionRepository versionRepository;
  private final ManagedSmsProviderBindingRepository bindingRepository;
  private final ManagedSmsTenantRepository tenantRepository;
  private final ManagedSmsProvisioningService provisioningService;
  private final ManagedSmsEligibilityService eligibilityService;

  @Transactional
  public void refreshStaleState() {
    Instant now = Instant.now();
    profileRepository.findByStatusInOrderByUpdatedAtAsc(List.of(DltProfileStatus.ACTIVE)).stream()
        .filter(item -> item.getStaleAfter() != null && !item.getStaleAfter().isAfter(now))
        .forEach(
            profile -> {
              profile.setStatus(DltProfileStatus.STALE);
              profile.setTmChainStatus(TmChainStatus.STALE);
              profileRepository.save(profile);
              tenantRepository
                  .findByCompanyId(profile.getCompanyId())
                  .ifPresent(
                      tenant ->
                          provisioningService.enqueueOrReset(
                              profile.getCompanyId(),
                              tenant.getId(),
                              ProvisioningResourceType.DLT_PROFILE,
                              profile.getId(),
                              ProvisioningOperationType.REFRESH_DLT_PROFILE,
                              "managed-sms:dlt-profile:refresh:" + profile.getId()));
              eligibilityService.recalculate(profile.getCompanyId());
            });

    headerRepository.findByStatusInOrderByUpdatedAtAsc(List.of(HeaderStatus.ACTIVE)).stream()
        .filter(item -> item.getStaleAfter() != null && !item.getStaleAfter().isAfter(now))
        .forEach(
            header -> {
              header.setStatus(HeaderStatus.STALE);
              header.setProviderMappingStatus(ProviderMappingStatus.SYNCING);
              headerRepository.save(header);
              tenantRepository
                  .findByCompanyId(header.getCompanyId())
                  .ifPresent(
                      tenant ->
                          provisioningService.enqueueOrReset(
                              header.getCompanyId(),
                              tenant.getId(),
                              ProvisioningResourceType.HEADER,
                              header.getId(),
                              ProvisioningOperationType.REFRESH_HEADER_MAPPING,
                              "managed-sms:header:refresh:" + header.getId()));
              eligibilityService.recalculate(header.getCompanyId());
            });

    versionRepository.findByStatusInOrderByCreatedAtAsc(List.of(TemplateVersionStatus.ACTIVE)).stream()
        .filter(item -> item.getStaleAfter() != null && !item.getStaleAfter().isAfter(now))
        .forEach(
            version -> {
              version.setStatus(TemplateVersionStatus.STALE);
              versionRepository.save(version);
              ManagedSmsTemplate template =
                  templateRepository
                      .findByIdAndCompanyId(version.getTemplateId(), version.getCompanyId())
                      .orElse(null);
              if (template != null) {
                template.setStatus(TemplateStatus.SUSPENDED);
                templateRepository.save(template);
              }
              tenantRepository
                  .findByCompanyId(version.getCompanyId())
                  .ifPresent(
                      tenant ->
                          bindingRepository
                              .findByCompanyIdAndTemplateVersionId(
                                  version.getCompanyId(), version.getId())
                              .forEach(
                                  binding -> {
                                    binding.setStatus(ProviderBindingStatus.SYNCING);
                                    bindingRepository.save(binding);
                                    provisioningService.enqueueOrReset(
                                        version.getCompanyId(),
                                        tenant.getId(),
                                        ProvisioningResourceType.TEMPLATE_BINDING,
                                        binding.getId(),
                                        ProvisioningOperationType.REFRESH_TEMPLATE_BINDING,
                                        "managed-sms:template-binding:refresh:" + binding.getId());
                                  }));
              eligibilityService.recalculate(version.getCompanyId());
            });
  }
}
