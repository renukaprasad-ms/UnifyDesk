package com.messaging.managedsms.control.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.admin.enums.PlatformPermission;
import com.messaging.admin.service.PlatformAdminAuthorizationService;
import com.messaging.broadcast.enums.BroadcastSenderConnectionStatus;
import com.messaging.broadcast.repository.BroadcastSenderConnectionRepository;
import com.messaging.common.exception.*;
import com.messaging.managedsms.control.config.ManagedSmsProperties;
import com.messaging.managedsms.control.dto.ManagedSmsAdminDtos.*;
import com.messaging.managedsms.control.entity.*;
import com.messaging.managedsms.control.enums.ManagedSmsEnums.*;
import com.messaging.managedsms.control.provisioning.ManagedSmsProviderAdapter;
import com.messaging.managedsms.control.provisioning.ManagedSmsProvisioningService;
import com.messaging.managedsms.control.repository.*;
import com.messaging.managedsms.control.service.ManagedSmsCtaPolicyService;
import com.messaging.managedsms.control.service.ManagedSmsEligibilityService;
import com.messaging.security.service.CurrentUserService;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManagedSmsAdminService {

  private final ManagedSmsTenantRepository tenantRepository;
  private final ManagedSmsDltProfileRepository profileRepository;
  private final ManagedSmsHeaderRepository headerRepository;
  private final ManagedSmsTemplateRepository templateRepository;
  private final ManagedSmsTemplateVersionRepository versionRepository;
  private final ManagedSmsTemplateHeaderRepository templateHeaderRepository;
  private final ManagedSmsProviderBindingRepository bindingRepository;
  private final ManagedSmsAdminActionRepository auditRepository;
  private final BroadcastSenderConnectionRepository connectionRepository;
  private final ManagedSmsProvisioningService provisioningService;
  private final ManagedSmsProviderAdapter providerAdapter;
  private final ManagedSmsCtaPolicyService ctaPolicyService;
  private final ManagedSmsEligibilityService eligibilityService;
  private final ManagedSmsProperties properties;
  private final CurrentUserService currentUserService;
  private final PlatformAdminAuthorizationService authorizationService;
  private final ObjectMapper objectMapper;

  @Transactional(readOnly = true)
  public ReviewQueueResponse queue() {
    Long actor = currentUserService.currentUserId();
    authorizationService.requirePermission(actor, PlatformPermission.COMPANY_VIEW);
    List<ReviewItem> items = new ArrayList<>();
    profileRepository
        .findByStatusInOrderByUpdatedAtAsc(
            List.of(DltProfileStatus.WAITING_UNIFYDESK_REVIEW, DltProfileStatus.ACTION_REQUIRED))
        .forEach(
            item -> {
              if (authorizationService.canAccessCompany(actor, item.getCompanyId())) {
                items.add(
                    new ReviewItem(
                        "DLT_PROFILE",
                        item.getId().toString(),
                        item.getCompanyId().toString(),
                        item.getStatus().name(),
                        "PE / PE-TM chain review",
                        item.getLockVersion(),
                        item.getUpdatedAt()));
              }
            });
    headerRepository
        .findByStatusInOrderByUpdatedAtAsc(List.of(HeaderStatus.PENDING_VERIFICATION))
        .forEach(
            item -> {
              if (authorizationService.canAccessCompany(actor, item.getCompanyId())) {
                items.add(
                    new ReviewItem(
                        "HEADER",
                        item.getId().toString(),
                        item.getCompanyId().toString(),
                        item.getStatus().name(),
                        "DLT Header review",
                        item.getLockVersion(),
                        item.getUpdatedAt()));
              }
            });
    versionRepository
        .findByStatusInOrderByCreatedAtAsc(
            List.of(
                TemplateVersionStatus.PENDING_DLT_APPROVAL,
                TemplateVersionStatus.APPROVED_DLT,
                TemplateVersionStatus.PROVIDER_SYNCING))
        .forEach(
            item -> {
              if (authorizationService.canAccessCompany(actor, item.getCompanyId())) {
                items.add(
                    new ReviewItem(
                        "TEMPLATE_VERSION",
                        item.getId().toString(),
                        item.getCompanyId().toString(),
                        item.getStatus().name(),
                        "DLT content template review",
                        item.getLockVersion(),
                        item.getCreatedAt()));
              }
            });
    return new ReviewQueueResponse(List.copyOf(items));
  }

  @Transactional
  public DecisionResponse decideProfile(Long profileId, DecisionRequest request) {
    ManagedSmsDltProfile profile =
        profileRepository.findById(profileId).orElseThrow(() -> new NotFoundException("DLT profile not found"));
    Long actor = requireAdmin(profile.getCompanyId(), PlatformPermission.COMPANY_UPDATE);
    requireVersion(profile.getLockVersion(), request.version());
    String before = auditJson(profile);

    ManagedSmsTenant tenant =
        tenantRepository
            .findByIdAndCompanyId(profile.getTenantId(), profile.getCompanyId())
            .orElseThrow(() -> new NotFoundException("Managed SMS tenant not found"));
    Instant now = Instant.now();
    if (request.decision() == AdminDecision.APPROVE) {
      if (profile.getTmChainReference() == null || profile.getTmChainReference().isBlank()) {
        throw new BadRequestException("MANAGED_SMS_TM_CHAIN_REQUIRED: PE-TM chain evidence is required");
      }
      profile.setStatus(DltProfileStatus.ACTIVE);
      profile.setTmChainStatus(TmChainStatus.ACTIVE);
      profile.setVerifiedAt(now);
      profile.setCheckedAt(now);
      profile.setStaleAfter(now.plus(properties.getComplianceStaleAfter()));
      if (request.providerAccountRef() != null && !request.providerAccountRef().isBlank()) {
        tenant.setProviderAccountRef(request.providerAccountRef().trim());
      }
      if (request.providerClientRef() != null && !request.providerClientRef().isBlank()) {
        tenant.setProviderClientRef(request.providerClientRef().trim());
        tenant.setLastProviderSyncAt(now);
      }
      tenantRepository.save(tenant);
    } else {
      profile.setStatus(DltProfileStatus.REJECTED);
      profile.setTmChainStatus(TmChainStatus.REJECTED);
      profile.setCheckedAt(now);
      profile.setStaleAfter(null);
    }
    profileRepository.saveAndFlush(profile);
    audit(actor, profile.getCompanyId(), ProvisioningResourceType.DLT_PROFILE, profile.getId(), AdminActionType.DLT_PROFILE_DECISION, request.reason(), before, auditJson(profile));
    eligibilityService.recalculate(profile.getCompanyId());
    return new DecisionResponse("DLT_PROFILE", profile.getId().toString(), profile.getStatus().name(), profile.getLockVersion(), now);
  }

  @Transactional
  public DecisionResponse decideHeader(Long headerId, DecisionRequest request) {
    ManagedSmsHeader header =
        headerRepository.findById(headerId).orElseThrow(() -> new NotFoundException("Header not found"));
    Long actor = requireAdmin(header.getCompanyId(), PlatformPermission.COMPANY_UPDATE);
    requireVersion(header.getLockVersion(), request.version());
    String before = auditJson(header);
    Instant now = Instant.now();

    if (request.decision() == AdminDecision.APPROVE) {
      header.setStatus(HeaderStatus.ACTIVE);
      header.setApprovedAt(now);
      header.setCheckedAt(now);
      header.setStaleAfter(now.plus(properties.getComplianceStaleAfter()));
      header.setRejectionReason(null);
      if (request.providerMappingRef() != null && !request.providerMappingRef().isBlank()) {
        header.setProviderMappingStatus(ProviderMappingStatus.READY);
      } else {
        header.setProviderMappingStatus(ProviderMappingStatus.ACTION_REQUIRED);
        ManagedSmsTenant tenant = tenantRepository.findByCompanyId(header.getCompanyId()).orElseThrow();
        provisioningService.enqueueOrReset(
            header.getCompanyId(),
            tenant.getId(),
            ProvisioningResourceType.HEADER,
            header.getId(),
            ProvisioningOperationType.ENSURE_HEADER_MAPPING,
            "managed-sms:header:ensure:" + header.getId());
      }
    } else {
      header.setStatus(HeaderStatus.REJECTED);
      header.setProviderMappingStatus(ProviderMappingStatus.FAILED);
      header.setRejectionReason(request.reason().trim());
      header.setCheckedAt(now);
      header.setStaleAfter(null);
    }
    headerRepository.saveAndFlush(header);
    audit(actor, header.getCompanyId(), ProvisioningResourceType.HEADER, header.getId(), AdminActionType.HEADER_DECISION, request.reason(), before, auditJson(header));
    eligibilityService.recalculate(header.getCompanyId());
    return new DecisionResponse("HEADER", header.getId().toString(), header.getStatus().name(), header.getLockVersion(), now);
  }

  @Transactional
  public DecisionResponse decideTemplate(Long versionId, DecisionRequest request) {
    ManagedSmsTemplateVersion version =
        versionRepository
            .findById(versionId)
            .orElseThrow(() -> new NotFoundException("Template version not found"));
    Long actor = requireAdmin(version.getCompanyId(), PlatformPermission.COMPANY_UPDATE);
    requireVersion(version.getLockVersion(), request.version());
    String before = auditJson(version);
    Instant now = Instant.now();

    ManagedSmsTemplate template =
        templateRepository
            .findByIdAndCompanyId(version.getTemplateId(), version.getCompanyId())
            .orElseThrow(() -> new NotFoundException("Template not found"));

    if (request.decision() == AdminDecision.REJECT) {
      version.setDltStatus(DltTemplateStatus.REJECTED);
      version.setStatus(TemplateVersionStatus.REJECTED);
      version.setCheckedAt(now);
      version.setStaleAfter(null);
      versionRepository.save(version);
      template.setStatus(TemplateStatus.DRAFT);
      templateRepository.save(template);
    } else {
      String authoritativeCtaPolicy =
          ctaPolicyService.finalizeForActivation(
              version.getVariablesSchemaJson(), request.ctaPolicy());

      version.setCtaPolicyJson(authoritativeCtaPolicy);
      version.setDltStatus(DltTemplateStatus.APPROVED_DLT);
      version.setStatus(TemplateVersionStatus.APPROVED_DLT);
      version.setApprovedAt(now);
      version.setCheckedAt(now);
      version.setStaleAfter(now.plus(properties.getComplianceStaleAfter()));

      ManagedSmsTenant tenant =
          tenantRepository
              .findByCompanyId(version.getCompanyId())
              .orElseThrow(() -> new NotFoundException("Managed SMS tenant not found"));
      List<ManagedSmsTemplateHeader> links =
          templateHeaderRepository.findByCompanyIdAndIdTemplateVersionId(
              version.getCompanyId(), version.getId());

      boolean allReady = !links.isEmpty();
      for (ManagedSmsTemplateHeader link : links) {
        Long headerId = link.getId().getHeaderId();
        ManagedSmsHeader header =
            headerRepository
                .findByIdAndCompanyId(headerId, version.getCompanyId())
                .orElseThrow(() -> new NotFoundException("Managed SMS Header not found"));
        ManagedSmsProviderBinding binding =
            bindingRepository
                .findByCompanyIdAndTemplateVersionIdAndHeaderIdAndProvider(
                    version.getCompanyId(),
                    version.getId(),
                    headerId,
                    com.messaging.broadcast.enums.BroadcastSenderProvider.MSG91_SMS)
                .orElseGet(
                    () -> {
                      ManagedSmsProviderBinding item = new ManagedSmsProviderBinding();
                      item.setCompanyId(version.getCompanyId());
                      item.setTenantId(tenant.getId());
                      item.setTemplateVersionId(version.getId());
                      item.setHeaderId(headerId);
                      item.setProvider(
                          com.messaging.broadcast.enums.BroadcastSenderProvider.MSG91_SMS);
                      return item;
                    });

        binding.setProvider(
            com.messaging.broadcast.enums.BroadcastSenderProvider.MSG91_SMS);
        if (trimToNull(request.providerTemplateRef()) != null) {
          binding.setProviderTemplateRef(request.providerTemplateRef().trim());
        }
        if (trimToNull(request.providerFlowRef()) != null) {
          binding.setProviderFlowRef(request.providerFlowRef().trim());
        }
        if (trimToNull(request.providerMappingRef()) != null) {
          binding.setProviderMappingRef(request.providerMappingRef().trim());
        }

        ManagedSmsProviderAdapter.BindingReadiness readiness =
            providerAdapter.bindingReadiness(
                new ManagedSmsProviderAdapter.BindingContext(
                    binding, tenant, version, header));
        if (readiness.ready()) {
          binding.setStatus(ProviderBindingStatus.ACTIVE);
          binding.setLastSyncAt(now);
          binding.setRejectionReason(null);
        } else {
          allReady = false;
          binding.setStatus(ProviderBindingStatus.ACTION_REQUIRED);
          binding.setLastSyncAt(null);
          binding.setRejectionReason(readiness.safeMessage());
        }

        ManagedSmsProviderBinding saved = bindingRepository.save(binding);
        if (!readiness.ready()) {
          provisioningService.enqueueOrReset(
              version.getCompanyId(),
              tenant.getId(),
              ProvisioningResourceType.TEMPLATE_BINDING,
              saved.getId(),
              ProvisioningOperationType.ENSURE_TEMPLATE_BINDING,
              "managed-sms:template-binding:ensure:" + saved.getId());
        }
      }

      if (allReady) {
        versionRepository
            .findFirstByTemplateIdAndCompanyIdAndStatusOrderByVersionNoDesc(
                template.getId(), version.getCompanyId(), TemplateVersionStatus.ACTIVE)
            .filter(previous -> !Objects.equals(previous.getId(), version.getId()))
            .ifPresent(
                previous -> {
                  previous.setStatus(TemplateVersionStatus.SUPERSEDED);
                  versionRepository.save(previous);
                });
        version.setStatus(TemplateVersionStatus.ACTIVE);
        template.setStatus(TemplateStatus.ACTIVE);
      } else {
        version.setStatus(TemplateVersionStatus.APPROVED_DLT);
        template.setStatus(TemplateStatus.DRAFT);
      }
      versionRepository.saveAndFlush(version);
      templateRepository.save(template);
    }

    versionRepository.flush();
    audit(
        actor,
        version.getCompanyId(),
        ProvisioningResourceType.TEMPLATE_BINDING,
        version.getId(),
        AdminActionType.TEMPLATE_DECISION,
        request.reason(),
        before,
        auditJson(version));
    eligibilityService.recalculate(version.getCompanyId());
    return new DecisionResponse(
        "TEMPLATE_VERSION",
        version.getId().toString(),
        version.getStatus().name(),
        version.getLockVersion(),
        now);
  }

  @Transactional
  public DecisionResponse suspendTenant(Long tenantId, TenantActionRequest request) {
    ManagedSmsTenant tenant =
        tenantRepository.findById(tenantId).orElseThrow(() -> new NotFoundException("Managed SMS tenant not found"));
    Long actor = requireAdmin(tenant.getCompanyId(), PlatformPermission.COMPANY_SUSPEND);
    requireVersion(tenant.getLockVersion(), request.version());
    String before = auditJson(tenant);
    tenant.setStatus(TenantStatus.SUSPENDED);
    tenant.setSendingEnabled(false);
    tenant.setSuspendedReason(request.reason().trim());
    tenantRepository.saveAndFlush(tenant);
    connectionRepository.findByIdAndCompanyId(tenant.getBroadcastConnectionId(), tenant.getCompanyId()).ifPresent(
        connection -> {
          connection.setSendingEnabled(false);
          connection.setStatus(BroadcastSenderConnectionStatus.DISABLED);
          connectionRepository.save(connection);
        });
    tenantRepository.flush();
    audit(actor, tenant.getCompanyId(), ProvisioningResourceType.TENANT, tenant.getId(), AdminActionType.TENANT_SUSPEND, request.reason(), before, auditJson(tenant));
    return new DecisionResponse("TENANT", tenant.getId().toString(), tenant.getStatus().name(), tenant.getLockVersion(), Instant.now());
  }

  @Transactional
  public DecisionResponse resumeTenant(Long tenantId, TenantActionRequest request) {
    ManagedSmsTenant tenant =
        tenantRepository.findById(tenantId).orElseThrow(() -> new NotFoundException("Managed SMS tenant not found"));
    Long actor = requireAdmin(tenant.getCompanyId(), PlatformPermission.COMPANY_SUSPEND);
    requireVersion(tenant.getLockVersion(), request.version());
    String before = auditJson(tenant);
    tenant.setStatus(TenantStatus.PROVIDER_SYNCING);
    tenant.setSendingEnabled(false);
    tenant.setSuspendedReason(null);
    tenantRepository.save(tenant);
    connectionRepository.findByIdAndCompanyId(tenant.getBroadcastConnectionId(), tenant.getCompanyId()).ifPresent(
        connection -> {
          connection.setSendingEnabled(false);
          connection.setStatus(BroadcastSenderConnectionStatus.ACTIVE);
          connectionRepository.save(connection);
        });
    provisioningService.enqueueOrReset(
        tenant.getCompanyId(),
        tenant.getId(),
        ProvisioningResourceType.TENANT,
        tenant.getId(),
        ProvisioningOperationType.ENSURE_TENANT,
        "managed-sms:tenant:ensure:" + tenant.getCompanyId());
    eligibilityService.recalculate(tenant.getCompanyId());
    audit(actor, tenant.getCompanyId(), ProvisioningResourceType.TENANT, tenant.getId(), AdminActionType.TENANT_RESUME, request.reason(), before, auditJson(tenant));
    return new DecisionResponse("TENANT", tenant.getId().toString(), tenant.getStatus().name(), tenant.getLockVersion(), Instant.now());
  }

  private Long requireAdmin(Long companyId, PlatformPermission permission) {
    Long actor = currentUserService.currentUserId();
    authorizationService.requirePermission(actor, permission);
    authorizationService.requireCompanyAccess(actor, companyId);
    return actor;
  }

  private void requireVersion(long actual, long expected) {
    if (actual != expected) throw new ConflictException("Managed SMS resource changed; refresh and retry");
  }

  private void audit(
      Long actor,
      Long companyId,
      ProvisioningResourceType resourceType,
      Long resourceId,
      AdminActionType action,
      String reason,
      String before,
      String after) {
    ManagedSmsAdminAction row = new ManagedSmsAdminAction();
    row.setCompanyId(companyId);
    row.setActorUserId(actor);
    row.setResourceType(resourceType);
    row.setResourceId(resourceId);
    row.setAction(action);
    row.setReason(reason.trim());
    row.setBeforeStateJson(before);
    row.setAfterStateJson(after);
    auditRepository.save(row);
  }

  private String auditJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to create Managed SMS audit snapshot", exception);
    }
  }

  private String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
