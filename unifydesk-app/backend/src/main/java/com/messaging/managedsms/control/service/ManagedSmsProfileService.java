package com.messaging.managedsms.control.service;

import com.messaging.common.exception.*;
import com.messaging.managedsms.control.dto.ManagedSmsDtos.*;
import com.messaging.managedsms.control.entity.*;
import com.messaging.managedsms.control.enums.ManagedSmsEnums.*;
import com.messaging.managedsms.control.provisioning.ManagedSmsProvisioningService;
import com.messaging.managedsms.control.repository.*;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManagedSmsProfileService {

  private final ManagedSmsTenantService tenantService;
  private final ManagedSmsTenantRepository tenantRepository;
  private final ManagedSmsDltProfileRepository profileRepository;
  private final ManagedSmsProvisioningService provisioningService;
  private final ManagedSmsJsonSupport jsonSupport;
  private final ManagedSmsEligibilityService eligibilityService;

  @Transactional
  public DltProfileResponse create(Long companyId, Long userId, DltProfileRequest request) {
    if (profileRepository.findFirstByCompanyIdAndArchivedAtIsNullOrderByCreatedAtDesc(companyId).isPresent()) {
      throw new ConflictException("MANAGED_SMS_DLT_PROFILE_REQUIRED: A live DLT profile already exists");
    }
    ManagedSmsTenant tenant = tenantService.ensureTenant(companyId, userId);
    String peId = normalizePeId(request.peId());
    if (profileRepository.existsByCompanyIdAndPeIdAndArchivedAtIsNull(companyId, peId)) {
      throw new ConflictException("MANAGED_SMS_DLT_PROFILE_REQUIRED: This PE ID already exists");
    }
    ManagedSmsDltProfile profile = new ManagedSmsDltProfile();
    profile.setCompanyId(companyId);
    profile.setTenantId(tenant.getId());
    profile.setLegalName(request.legalName().trim());
    profile.setDltPlatform(request.dltPlatform());
    profile.setPeId(peId);
    profile.setTmChainReference(trimToNull(request.tmChainReference()));
    profile.setEvidenceJson(jsonSupport.json(request.evidence(), "{}"));
    profile.setStatus(DltProfileStatus.DRAFT);
    profile.setTmChainStatus(
        profile.getTmChainReference() == null
            ? TmChainStatus.ACTION_REQUIRED
            : TmChainStatus.WAITING_UNIFYDESK_REVIEW);
    profile.setRulesetVersion("INDIA_TCCCPR_2025");
    return response(profileRepository.save(profile));
  }

  @Transactional(readOnly = true)
  public DltProfileResponse get(Long companyId) {
    return response(require(companyId));
  }

  @Transactional
  public DltProfileResponse patch(
      Long companyId, Long profileId, DltProfilePatchRequest request) {
    ManagedSmsDltProfile profile = require(companyId, profileId);
    if (profile.getStatus() == DltProfileStatus.ACTIVE) {
      if (request.peId() != null && !normalizePeId(request.peId()).equals(profile.getPeId())) {
        throw new BadRequestException(
            "MANAGED_SMS_DLT_PROFILE_REQUIRED: An active PE ID cannot be replaced; create a new compliance profile");
      }
    }
    if (request.legalName() != null && !request.legalName().isBlank()) {
      profile.setLegalName(request.legalName().trim());
    }
    if (request.dltPlatform() != null) profile.setDltPlatform(request.dltPlatform());
    if (request.peId() != null && !request.peId().isBlank()) {
      String peId = normalizePeId(request.peId());
      if (!peId.equals(profile.getPeId())
          && profileRepository.existsByCompanyIdAndPeIdAndArchivedAtIsNull(companyId, peId)) {
        throw new ConflictException("MANAGED_SMS_DLT_PROFILE_REQUIRED: This PE ID already exists");
      }
      profile.setPeId(peId);
    }
    if (request.tmChainReference() != null) {
      profile.setTmChainReference(trimToNull(request.tmChainReference()));
      profile.setTmChainStatus(
          profile.getTmChainReference() == null
              ? TmChainStatus.ACTION_REQUIRED
              : TmChainStatus.WAITING_UNIFYDESK_REVIEW);
    }
    if (request.evidence() != null) profile.setEvidenceJson(jsonSupport.json(request.evidence(), "{}"));
    if (profile.getStatus() != DltProfileStatus.ACTIVE) profile.setStatus(DltProfileStatus.DRAFT);
    eligibilityService.recalculate(companyId);
    return response(profileRepository.save(profile));
  }

  @Transactional
  public DltProfileResponse submit(Long companyId, Long profileId) {
    ManagedSmsDltProfile profile = require(companyId, profileId);
    if (profile.getTmChainReference() == null || profile.getTmChainReference().isBlank()) {
      profile.setStatus(DltProfileStatus.ACTION_REQUIRED);
      profile.setTmChainStatus(TmChainStatus.ACTION_REQUIRED);
      profileRepository.save(profile);
      throw new BadRequestException(
          "MANAGED_SMS_TM_CHAIN_REQUIRED: Complete the PE-TM chain before review");
    }
    profile.setStatus(DltProfileStatus.WAITING_UNIFYDESK_REVIEW);
    profile.setTmChainStatus(TmChainStatus.WAITING_UNIFYDESK_REVIEW);
    eligibilityService.recalculate(companyId);
    return response(profileRepository.save(profile));
  }

  @Transactional
  public DltProfileResponse refresh(Long companyId, Long profileId) {
    ManagedSmsDltProfile profile = require(companyId, profileId);
    ManagedSmsTenant tenant =
        tenantRepository
            .findByIdAndCompanyId(profile.getTenantId(), companyId)
            .orElseThrow(() -> new NotFoundException("Managed SMS tenant not found"));
    provisioningService.enqueueOrReset(
        companyId,
        tenant.getId(),
        ProvisioningResourceType.DLT_PROFILE,
        profile.getId(),
        ProvisioningOperationType.REFRESH_DLT_PROFILE,
        "managed-sms:dlt-profile:refresh:" + profile.getId());
    return response(profile);
  }

  ManagedSmsDltProfile require(Long companyId) {
    return profileRepository
        .findFirstByCompanyIdAndArchivedAtIsNullOrderByCreatedAtDesc(companyId)
        .orElseThrow(
            () -> new NotFoundException("MANAGED_SMS_DLT_PROFILE_REQUIRED: DLT profile not found"));
  }

  ManagedSmsDltProfile require(Long companyId, Long profileId) {
    return profileRepository
        .findByIdAndCompanyId(profileId, companyId)
        .filter(item -> item.getArchivedAt() == null)
        .orElseThrow(
            () -> new NotFoundException("MANAGED_SMS_DLT_PROFILE_REQUIRED: DLT profile not found"));
  }

  public DltProfileResponse response(ManagedSmsDltProfile profile) {
    return new DltProfileResponse(
        profile.getId().toString(),
        profile.getLegalName(),
        profile.getDltPlatform(),
        profile.getPeId(),
        profile.getStatus(),
        profile.getTmChainStatus(),
        profile.getTmChainReference(),
        profile.getVerifiedAt(),
        profile.getCheckedAt(),
        profile.getStaleAfter(),
        profile.getLockVersion());
  }

  private String normalizePeId(String value) {
    String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    if (normalized.isBlank()
        || normalized.length() > 160
        || !normalized.matches("[A-Z0-9_-]+")) {
      throw new BadRequestException("MANAGED_SMS_DLT_PROFILE_REQUIRED: Invalid PE ID");
    }
    return normalized;
  }

  private String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
