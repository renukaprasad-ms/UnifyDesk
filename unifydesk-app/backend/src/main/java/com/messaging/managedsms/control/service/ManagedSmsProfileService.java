package com.messaging.managedsms.control.service;

import com.messaging.common.exception.*;
import com.messaging.managedsms.control.dto.ManagedSmsDtos.*;
import com.messaging.managedsms.control.entity.*;
import com.messaging.managedsms.control.enums.ManagedSmsEnums.*;
import com.messaging.managedsms.control.provisioning.ManagedSmsProvisioningService;
import com.messaging.managedsms.control.repository.*;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManagedSmsProfileService {

  private static final String REVIEW_EDIT_ACTION = "DLT_PROFILE_REVIEW_INVALIDATED";

  private final ManagedSmsTenantService tenantService;
  private final ManagedSmsTenantRepository tenantRepository;
  private final ManagedSmsDltProfileRepository profileRepository;
  private final ManagedSmsComplianceActionRepository complianceActionRepository;
  private final ManagedSmsProvisioningService provisioningService;
  private final ManagedSmsJsonSupport jsonSupport;
  private final ManagedSmsEligibilityService eligibilityService;

  @Transactional
  public DltProfileResponse create(Long companyId, Long userId, DltProfileRequest request) {
    if (profileRepository
        .findFirstByCompanyIdAndArchivedAtIsNullOrderByCreatedAtDesc(companyId)
        .isPresent()) {
      throw new ConflictException(
          "MANAGED_SMS_DLT_PROFILE_REQUIRED: A live DLT profile already exists");
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
      Long companyId, Long profileId, Long actorUserId, DltProfilePatchRequest request) {
    ManagedSmsDltProfile profile = require(companyId, profileId);
    boolean active = profile.getStatus() == DltProfileStatus.ACTIVE;

    String requestedPeId =
        request.peId() == null || request.peId().isBlank()
            ? profile.getPeId()
            : normalizePeId(request.peId());
    if (active && !requestedPeId.equals(profile.getPeId())) {
      throw new BadRequestException(
          "MANAGED_SMS_DLT_PROFILE_REQUIRED: An active PE ID cannot be replaced; create a new compliance profile");
    }

    String nextLegalName =
        request.legalName() == null || request.legalName().isBlank()
            ? profile.getLegalName()
            : request.legalName().trim();
    DltPlatform nextPlatform =
        request.dltPlatform() == null ? profile.getDltPlatform() : request.dltPlatform();
    String nextTmReference =
        request.tmChainReference() == null
            ? profile.getTmChainReference()
            : trimToNull(request.tmChainReference());
    String nextEvidence =
        request.evidence() == null
            ? profile.getEvidenceJson()
            : jsonSupport.json(request.evidence(), "{}");

    boolean legalNameChanged = !Objects.equals(profile.getLegalName(), nextLegalName);
    boolean platformChanged = profile.getDltPlatform() != nextPlatform;
    boolean tmReferenceChanged = !Objects.equals(profile.getTmChainReference(), nextTmReference);
    boolean evidenceChanged =
        request.evidence() != null && !jsonSupport.sameJson(profile.getEvidenceJson(), request.evidence());
    boolean reviewRelevantChanged =
        legalNameChanged || platformChanged || tmReferenceChanged || evidenceChanged;

    if (!requestedPeId.equals(profile.getPeId())
        && profileRepository.existsByCompanyIdAndPeIdAndArchivedAtIsNull(companyId, requestedPeId)) {
      throw new ConflictException("MANAGED_SMS_DLT_PROFILE_REQUIRED: This PE ID already exists");
    }

    String beforeState = active && reviewRelevantChanged ? auditState(profile) : null;

    profile.setLegalName(nextLegalName);
    profile.setDltPlatform(nextPlatform);
    profile.setPeId(requestedPeId);
    profile.setTmChainReference(nextTmReference);
    profile.setEvidenceJson(nextEvidence);

    if (active && reviewRelevantChanged) {
      profile.setStatus(DltProfileStatus.WAITING_UNIFYDESK_REVIEW);
      profile.setVerifiedAt(null);
      profile.setCheckedAt(null);
      profile.setStaleAfter(null);
      if (tmReferenceChanged) {
        profile.setTmChainStatus(
            nextTmReference == null
                ? TmChainStatus.ACTION_REQUIRED
                : TmChainStatus.WAITING_UNIFYDESK_REVIEW);
      }
      ManagedSmsDltProfile saved = profileRepository.saveAndFlush(profile);
      recordReviewEdit(actorUserId, saved, beforeState, auditState(saved));
      eligibilityService.recalculate(companyId);
      return response(saved);
    }

    if (!active) {
      if (request.tmChainReference() != null) {
        profile.setTmChainStatus(
            nextTmReference == null
                ? TmChainStatus.ACTION_REQUIRED
                : TmChainStatus.WAITING_UNIFYDESK_REVIEW);
      }
      profile.setStatus(DltProfileStatus.DRAFT);
    }

    ManagedSmsDltProfile saved = profileRepository.save(profile);
    if (reviewRelevantChanged) eligibilityService.recalculate(companyId);
    return response(saved);
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
    ManagedSmsDltProfile saved = profileRepository.save(profile);
    eligibilityService.recalculate(companyId);
    return response(saved);
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

  private void recordReviewEdit(
      Long actorUserId, ManagedSmsDltProfile profile, String beforeState, String afterState) {
    ManagedSmsComplianceAction action = new ManagedSmsComplianceAction();
    action.setCompanyId(profile.getCompanyId());
    action.setActorUserId(actorUserId);
    action.setResourceType(ProvisioningResourceType.DLT_PROFILE);
    action.setResourceId(profile.getId());
    action.setAction(REVIEW_EDIT_ACTION);
    action.setBeforeStateJson(beforeState);
    action.setAfterStateJson(afterState);
    action.setReason(null);
    complianceActionRepository.save(action);
  }

  private String auditState(ManagedSmsDltProfile profile) {
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("id", profile.getId());
    state.put("companyId", profile.getCompanyId());
    state.put("legalName", profile.getLegalName());
    state.put("dltPlatform", profile.getDltPlatform());
    state.put("peId", profile.getPeId());
    state.put("status", profile.getStatus());
    state.put("tmChainStatus", profile.getTmChainStatus());
    state.put("tmChainReference", profile.getTmChainReference());
    state.put("evidenceJson", profile.getEvidenceJson());
    state.put("verifiedAt", profile.getVerifiedAt());
    state.put("checkedAt", profile.getCheckedAt());
    state.put("staleAfter", profile.getStaleAfter());
    return jsonSupport.value(state);
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
