package com.messaging.managedsms.control.service;

import com.messaging.managedsms.control.config.ManagedSmsProperties;
import com.messaging.managedsms.control.dto.ManagedSmsDtos.*;
import com.messaging.managedsms.control.entity.*;
import com.messaging.managedsms.control.enums.ManagedSmsEnums.*;
import com.messaging.managedsms.control.repository.*;
import java.time.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManagedSmsStatusService {

  private final ManagedSmsDltProfileRepository profileRepository;
  private final ManagedSmsHeaderRepository headerRepository;
  private final ManagedSmsTemplateVersionRepository versionRepository;
  private final ManagedSmsProviderBindingRepository bindingRepository;
  private final ManagedSmsEligibilityService eligibilityService;
  private final ManagedSmsProperties properties;

  @Transactional
  public StatusResponse get(Long companyId) {
    ManagedSmsTenant tenant = eligibilityService.recalculate(companyId);
    ManagedSmsDltProfile profile =
        profileRepository
            .findFirstByCompanyIdAndArchivedAtIsNullOrderByCreatedAtDesc(companyId)
            .orElse(null);
    long readyHeaders =
        headerRepository.countByCompanyIdAndStatusAndProviderMappingStatusAndArchivedAtIsNull(
            companyId, HeaderStatus.ACTIVE, ProviderMappingStatus.READY);
    long readyTemplates =
        versionRepository.countByCompanyIdAndStatus(companyId, TemplateVersionStatus.ACTIVE);

    CustomerState state;
    String nextAction;
    if (!properties.isEnabled()) {
      state = CustomerState.SUSPENDED;
      nextAction = "Managed SMS is not enabled.";
    } else if (tenant != null && tenant.getStatus() == TenantStatus.SUSPENDED) {
      state = CustomerState.SUSPENDED;
      nextAction = "Managed SMS is suspended. Contact support.";
    } else if (profile == null) {
      state = CustomerState.ACTION_REQUIRED;
      nextAction = "Add your DLT Principal Entity details.";
    } else if (profile.getStatus() == DltProfileStatus.REJECTED
        || profile.getTmChainStatus() == TmChainStatus.REJECTED) {
      state = CustomerState.REJECTED;
      nextAction = "Review the DLT rejection and submit corrected details.";
    } else if (profile.getStatus() == DltProfileStatus.WAITING_DLT_APPROVAL) {
      state = CustomerState.WAITING_DLT_APPROVAL;
      nextAction = "Wait for DLT approval, then refresh your status.";
    } else if (profile.getStatus() == DltProfileStatus.WAITING_UNIFYDESK_REVIEW
        || profile.getTmChainStatus() == TmChainStatus.WAITING_UNIFYDESK_REVIEW) {
      state = CustomerState.WAITING_UNIFYDESK_REVIEW;
      nextAction = "Your compliance details are being reviewed.";
    } else if (profile.getStatus() != DltProfileStatus.ACTIVE
        || profile.getTmChainStatus() != TmChainStatus.ACTIVE
        || !eligibilityService.fresh(profile.getStaleAfter())) {
      state = CustomerState.ACTION_REQUIRED;
      nextAction = "Complete or refresh your PE and PE-TM chain verification.";
    } else if (readyHeaders == 0) {
      state = CustomerState.ACTION_REQUIRED;
      nextAction = "Add and verify an approved DLT Header.";
    } else if (readyTemplates == 0) {
      boolean providerAction =
          bindingRepository.findByCompanyId(companyId).stream()
              .anyMatch(item -> item.getStatus() == ProviderBindingStatus.ACTION_REQUIRED);
      state = providerAction ? CustomerState.ACTION_REQUIRED : CustomerState.PROVIDER_SYNCING;
      nextAction =
          providerAction
              ? "UnifyDesk review is required to finish provider template mapping."
              : "Add an approved DLT template or wait for provider sync.";
    } else if (tenant != null && tenant.getStatus() == TenantStatus.READY) {
      state = CustomerState.READY;
      nextAction = null;
    } else {
      state = CustomerState.PROVIDER_SYNCING;
      nextAction = "UnifyDesk is finishing the managed SMS setup.";
    }

    StatusDltProfile profileResponse =
        profile == null
            ? null
            : new StatusDltProfile(
                profile.getId().toString(),
                mask(profile.getPeId()),
                profile.getStatus(),
                profile.getTmChainStatus());
    Instant start = YearMonth.now(ZoneOffset.UTC).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant end = YearMonth.now(ZoneOffset.UTC).plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    return new StatusResponse(
        properties.isEnabled(),
        state,
        nextAction,
        profileResponse,
        readyHeaders,
        readyTemplates,
        tenant != null && tenant.isSendingEnabled(),
        new UsageSummary(start, end, 0, null));
  }

  private String mask(String value) {
    if (value == null || value.length() <= 4) return "****";
    return "*".repeat(Math.max(4, value.length() - 4)) + value.substring(value.length() - 4);
  }
}
