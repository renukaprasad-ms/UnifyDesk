package com.messaging.managedsms.control.service;

import com.messaging.common.exception.*;
import com.messaging.managedsms.control.dto.ManagedSmsDtos.*;
import com.messaging.managedsms.control.entity.*;
import com.messaging.managedsms.control.enums.ManagedSmsEnums.*;
import com.messaging.managedsms.control.provisioning.ManagedSmsProvisioningService;
import com.messaging.managedsms.control.repository.*;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManagedSmsHeaderService {

  private final ManagedSmsProfileService profileService;
  private final ManagedSmsHeaderRepository headerRepository;
  private final ManagedSmsTenantRepository tenantRepository;
  private final ManagedSmsSenderBindingRepository senderBindingRepository;
  private final com.messaging.broadcast.repository.BroadcastSenderRepository senderRepository;
  private final ManagedSmsProvisioningService provisioningService;
  private final ManagedSmsEligibilityService eligibilityService;

  @Transactional(readOnly = true)
  public List<HeaderResponse> list(Long companyId) {
    return headerRepository.findByCompanyIdAndArchivedAtIsNullOrderByCreatedAtDesc(companyId).stream()
        .map(this::response)
        .toList();
  }

  @Transactional(readOnly = true)
  public HeaderResponse get(Long companyId, Long headerId) {
    return response(require(companyId, headerId));
  }

  @Transactional
  public HeaderResponse create(Long companyId, HeaderRequest request) {
    ManagedSmsDltProfile profile = profileService.require(companyId);
    if (profile.getStatus() != DltProfileStatus.ACTIVE
        || profile.getTmChainStatus() != TmChainStatus.ACTIVE) {
      throw new BadRequestException(
          "MANAGED_SMS_TM_CHAIN_REQUIRED: The PE and PE-TM chain must be active first");
    }
    String value = normalizeHeader(request.header());
    if (headerRepository.existsByCompanyIdAndHeaderIgnoreCaseAndArchivedAtIsNull(companyId, value)) {
      throw new ConflictException("MANAGED_SMS_HEADER_NOT_READY: This Header already exists");
    }
    ManagedSmsHeader header = new ManagedSmsHeader();
    header.setCompanyId(companyId);
    header.setDltProfileId(profile.getId());
    header.setHeader(value);
    header.setMessageCategory(request.messageCategory());
    header.setStatus(HeaderStatus.DRAFT);
    header.setProviderMappingStatus(ProviderMappingStatus.NOT_STARTED);
    return response(headerRepository.save(header));
  }

  @Transactional
  public HeaderResponse submit(Long companyId, Long headerId) {
    ManagedSmsHeader header = require(companyId, headerId);
    header.setStatus(HeaderStatus.PENDING_VERIFICATION);
    header.setRejectionReason(null);
    headerRepository.save(header);
    eligibilityService.recalculate(companyId);
    return response(header);
  }

  @Transactional
  public HeaderResponse refresh(Long companyId, Long headerId) {
    ManagedSmsHeader header = require(companyId, headerId);
    ManagedSmsTenant tenant =
        tenantRepository
            .findByCompanyId(companyId)
            .orElseThrow(() -> new NotFoundException("Managed SMS tenant not found"));
    header.setProviderMappingStatus(ProviderMappingStatus.SYNCING);
    headerRepository.save(header);
    provisioningService.enqueueOrReset(
        companyId,
        tenant.getId(),
        ProvisioningResourceType.HEADER,
        header.getId(),
        ProvisioningOperationType.REFRESH_HEADER_MAPPING,
        "managed-sms:header:refresh:" + header.getId());
    return response(header);
  }

  @Transactional
  public HeaderResponse archive(Long companyId, Long headerId) {
    ManagedSmsHeader header = require(companyId, headerId);
    header.setStatus(HeaderStatus.ARCHIVED);
    header.setProviderMappingStatus(ProviderMappingStatus.NOT_STARTED);
    header.setArchivedAt(Instant.now());
    headerRepository.save(header);
    senderBindingRepository
        .findByCompanyIdAndHeaderId(companyId, headerId)
        .flatMap(binding -> senderRepository.findByIdAndCompanyId(binding.getSenderId(), companyId))
        .ifPresent(
            sender -> {
              sender.setStatus(com.messaging.broadcast.enums.BroadcastSenderStatus.DISABLED);
              senderRepository.save(sender);
            });
    eligibilityService.recalculate(companyId);
    return response(header);
  }

  ManagedSmsHeader require(Long companyId, Long headerId) {
    return headerRepository
        .findByIdAndCompanyId(headerId, companyId)
        .filter(item -> item.getArchivedAt() == null)
        .orElseThrow(
            () -> new NotFoundException("MANAGED_SMS_HEADER_NOT_READY: Header not found"));
  }

  public HeaderResponse response(ManagedSmsHeader header) {
    String providerState =
        switch (header.getProviderMappingStatus()) {
          case READY -> "READY";
          case SYNCING -> "SYNCING";
          case FAILED, ACTION_REQUIRED -> "FAILED";
          case NOT_STARTED -> "NOT_STARTED";
        };
    String action =
        header.getProviderMappingStatus() == ProviderMappingStatus.ACTION_REQUIRED
            ? "UnifyDesk review is required to finish provider mapping."
            : header.getRejectionReason();
    return new HeaderResponse(
        header.getId().toString(),
        header.getHeader(),
        header.getMessageCategory(),
        header.getStatus(),
        providerState,
        action,
        header.getUpdatedAt(),
        header.getLockVersion());
  }

  private String normalizeHeader(String value) {
    String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    if (normalized.length() < 3
        || normalized.length() > 11
        || !normalized.matches("[A-Z0-9]+")) {
      throw new BadRequestException(
          "MANAGED_SMS_HEADER_NOT_READY: Header must be 3-11 letters or digits");
    }
    return normalized;
  }
}
