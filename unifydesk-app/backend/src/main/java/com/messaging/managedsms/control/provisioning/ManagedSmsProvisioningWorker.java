package com.messaging.managedsms.control.provisioning;

import com.messaging.managedsms.control.config.ManagedSmsProperties;
import com.messaging.managedsms.control.entity.*;
import com.messaging.managedsms.control.enums.ManagedSmsEnums.*;
import com.messaging.managedsms.control.repository.*;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@RequiredArgsConstructor
public class ManagedSmsProvisioningWorker {

  private static final int MAX_ATTEMPTS = 8;

  private final ManagedSmsProvisioningOperationRepository operationRepository;
  private final ManagedSmsTenantRepository tenantRepository;
  private final ManagedSmsDltProfileRepository profileRepository;
  private final ManagedSmsHeaderRepository headerRepository;
  private final ManagedSmsTemplateRepository templateRepository;
  private final ManagedSmsTemplateVersionRepository versionRepository;
  private final ManagedSmsProviderBindingRepository bindingRepository;
  private final ManagedSmsProviderAdapter providerAdapter;
  private final ManagedSmsProperties properties;
  private final com.messaging.managedsms.control.service.ManagedSmsCtaPolicyService ctaPolicyService;
  private final com.messaging.managedsms.control.service.ManagedSmsEligibilityService eligibilityService;
  private final TransactionTemplate transactionTemplate;

  public boolean runOnce() {
    Work work =
        transactionTemplate.execute(
            status ->
                operationRepository
                    .findNextClaimable(Instant.now())
                    .map(
                        operation -> {
                          operation.setStatus(ProvisioningOperationStatus.RUNNING);
                          operation.setAttemptCount(operation.getAttemptCount() + 1);
                          operation.setLeaseUntil(Instant.now().plus(properties.getProvisioningStaleAfter()));
                          operationRepository.save(operation);
                          return loadWork(operation);
                        })
                    .orElse(null));
    if (work == null) return false;

    ManagedSmsProviderAdapter.ProviderResult result;
    try {
      result = providerAdapter.execute(work.request());
    } catch (RuntimeException exception) {
      result =
          new ManagedSmsProviderAdapter.ProviderResult(
              ManagedSmsProviderAdapter.Outcome.RETRYABLE_FAILURE,
              "PROVIDER_CALL_FAILED",
              "Managed SMS provider operation failed.",
              null,
              null,
              null,
              null,
              null);
    }
    apply(work.operationId(), result);
    eligibilityService.recalculate(work.request().companyId());
    return true;
  }

  private Work loadWork(ManagedSmsProvisioningOperation operation) {
    ManagedSmsTenant tenant =
        tenantRepository.findByIdAndCompanyId(operation.getTenantId(), operation.getCompanyId()).orElseThrow();
    String peId = null;
    String header = null;
    String dltTemplateId = null;
    String bodyText = null;

    if (operation.getResourceType() == ProvisioningResourceType.DLT_PROFILE) {
      ManagedSmsDltProfile profile =
          profileRepository
              .findByIdAndCompanyId(operation.getResourceId(), operation.getCompanyId())
              .orElseThrow();
      peId = profile.getPeId();
    } else if (operation.getResourceType() == ProvisioningResourceType.HEADER) {
      ManagedSmsHeader managedHeader =
          headerRepository
              .findByIdAndCompanyId(operation.getResourceId(), operation.getCompanyId())
              .orElseThrow();
      ManagedSmsDltProfile profile =
          profileRepository
              .findByIdAndCompanyId(managedHeader.getDltProfileId(), operation.getCompanyId())
              .orElseThrow();
      peId = profile.getPeId();
      header = managedHeader.getHeader();
    } else if (operation.getResourceType() == ProvisioningResourceType.TEMPLATE_BINDING) {
      ManagedSmsProviderBinding binding =
          bindingRepository
              .findByIdAndCompanyId(operation.getResourceId(), operation.getCompanyId())
              .orElseThrow();
      ManagedSmsTemplateVersion version =
          versionRepository
              .findByIdAndCompanyId(binding.getTemplateVersionId(), operation.getCompanyId())
              .orElseThrow();
      ManagedSmsHeader managedHeader =
          headerRepository
              .findByIdAndCompanyId(binding.getHeaderId(), operation.getCompanyId())
              .orElseThrow();
      ManagedSmsDltProfile profile =
          profileRepository
              .findByIdAndCompanyId(managedHeader.getDltProfileId(), operation.getCompanyId())
              .orElseThrow();
      peId = profile.getPeId();
      header = managedHeader.getHeader();
      dltTemplateId = version.getDltTemplateId();
      bodyText = version.getBodyText();
    }

    return new Work(
        operation.getId(),
        operation.getResourceType(),
        operation.getResourceId(),
        new ManagedSmsProviderAdapter.ProviderRequest(
            operation.getOperationType(),
            operation.getCompanyId(),
            tenant.getId(),
            operation.getResourceId(),
            tenant.getProviderClientRef(),
            peId,
            header,
            dltTemplateId,
            bodyText));
  }

  private void apply(Long operationId, ManagedSmsProviderAdapter.ProviderResult result) {
    transactionTemplate.executeWithoutResult(
        status -> {
          ManagedSmsProvisioningOperation operation = operationRepository.findById(operationId).orElseThrow();
          operation.setLeaseUntil(null);
          operation.setLastErrorCode(result.code());
          operation.setLastErrorMessage(result.safeMessage());

          switch (result.outcome()) {
            case SUCCEEDED -> {
              operation.setStatus(ProvisioningOperationStatus.SUCCEEDED);
              operation.setNextAttemptAt(null);
              applySuccess(operation, result);
            }
            case ACTION_REQUIRED -> {
              operation.setStatus(ProvisioningOperationStatus.ACTION_REQUIRED);
              operation.setNextAttemptAt(null);
              applyActionRequired(operation);
            }
            case RETRYABLE_FAILURE -> {
              if (operation.getAttemptCount() >= MAX_ATTEMPTS) {
                operation.setStatus(ProvisioningOperationStatus.FAILED);
                operation.setNextAttemptAt(null);
              } else {
                operation.setStatus(ProvisioningOperationStatus.RETRY);
                operation.setNextAttemptAt(Instant.now().plus(backoff(operation.getAttemptCount())));
              }
            }
            case PERMANENT_FAILURE -> {
              operation.setStatus(ProvisioningOperationStatus.FAILED);
              operation.setNextAttemptAt(null);
            }
          }
          operationRepository.save(operation);
        });
  }

  private void applySuccess(
      ManagedSmsProvisioningOperation operation, ManagedSmsProviderAdapter.ProviderResult result) {
    Instant now = Instant.now();
    if (operation.getResourceType() == ProvisioningResourceType.TENANT) {
      tenantRepository
          .findByIdAndCompanyId(operation.getTenantId(), operation.getCompanyId())
          .ifPresent(
              tenant -> {
                if (result.providerAccountRef() != null) tenant.setProviderAccountRef(result.providerAccountRef());
                if (result.providerClientRef() != null) tenant.setProviderClientRef(result.providerClientRef());
                tenant.setLastProviderSyncAt(now);
                tenantRepository.save(tenant);
              });
    } else if (operation.getResourceType() == ProvisioningResourceType.HEADER) {
      headerRepository
          .findByIdAndCompanyId(operation.getResourceId(), operation.getCompanyId())
          .ifPresent(
              header -> {
                header.setProviderMappingStatus(ProviderMappingStatus.READY);
                header.setCheckedAt(now);
                header.setStaleAfter(now.plus(properties.getComplianceStaleAfter()));
                headerRepository.save(header);
              });
    } else if (operation.getResourceType() == ProvisioningResourceType.TEMPLATE_BINDING) {
      bindingRepository
          .findByIdAndCompanyId(operation.getResourceId(), operation.getCompanyId())
          .ifPresent(
              binding -> {
                binding.setProviderTemplateRef(result.providerTemplateRef());
                binding.setProviderFlowRef(result.providerFlowRef());
                binding.setProviderMappingRef(result.providerMappingRef());
                ManagedSmsProviderAdapter.BindingReadiness readiness =
                    bindingReadiness(operation.getCompanyId(), binding);
                if (readiness.ready()) {
                  binding.setStatus(ProviderBindingStatus.ACTIVE);
                  binding.setLastSyncAt(now);
                  binding.setRejectionReason(null);
                } else {
                  binding.setStatus(ProviderBindingStatus.ACTION_REQUIRED);
                  binding.setLastSyncAt(null);
                  binding.setRejectionReason(readiness.safeMessage());
                }
                bindingRepository.save(binding);
                if (readiness.ready()) {
                  promoteTemplateIfReady(
                      operation.getCompanyId(), binding.getTemplateVersionId());
                }
              });
    }
  }

  private void promoteTemplateIfReady(Long companyId, Long versionId) {
    var bindings = bindingRepository.findByCompanyIdAndTemplateVersionId(companyId, versionId);
    if (bindings.isEmpty()
        || bindings.stream()
            .anyMatch(
                item ->
                    item.getStatus() != ProviderBindingStatus.ACTIVE
                        || !bindingReadiness(companyId, item).ready())) {
      return;
    }
    ManagedSmsTemplateVersion version =
        versionRepository.findByIdAndCompanyId(versionId, companyId).orElseThrow();
    ManagedSmsTemplate template =
        templateRepository.findByIdAndCompanyId(version.getTemplateId(), companyId).orElseThrow();
    try {
      ctaPolicyService.requireReadyForActivation(
          version.getVariablesSchemaJson(), version.getCtaPolicyJson());
    } catch (com.messaging.common.exception.BadRequestException exception) {
      version.setStatus(TemplateVersionStatus.APPROVED_DLT);
      versionRepository.save(version);
      template.setStatus(TemplateStatus.DRAFT);
      templateRepository.save(template);
      return;
    }
    versionRepository
        .findFirstByTemplateIdAndCompanyIdAndStatusOrderByVersionNoDesc(
            template.getId(), companyId, TemplateVersionStatus.ACTIVE)
        .filter(previous -> !java.util.Objects.equals(previous.getId(), version.getId()))
        .ifPresent(
            previous -> {
              previous.setStatus(TemplateVersionStatus.SUPERSEDED);
              versionRepository.save(previous);
            });
    version.setStatus(TemplateVersionStatus.ACTIVE);
    version.setCheckedAt(Instant.now());
    version.setStaleAfter(Instant.now().plus(properties.getComplianceStaleAfter()));
    versionRepository.save(version);
    template.setStatus(TemplateStatus.ACTIVE);
    templateRepository.save(template);
  }

  private ManagedSmsProviderAdapter.BindingReadiness bindingReadiness(
      Long companyId, ManagedSmsProviderBinding binding) {
    ManagedSmsTenant tenant =
        tenantRepository
            .findByIdAndCompanyId(binding.getTenantId(), companyId)
            .orElseThrow();
    ManagedSmsTemplateVersion version =
        versionRepository
            .findByIdAndCompanyId(binding.getTemplateVersionId(), companyId)
            .orElseThrow();
    ManagedSmsHeader header =
        headerRepository.findByIdAndCompanyId(binding.getHeaderId(), companyId).orElseThrow();
    return providerAdapter.bindingReadiness(
        new ManagedSmsProviderAdapter.BindingContext(binding, tenant, version, header));
  }

  private void applyActionRequired(ManagedSmsProvisioningOperation operation) {
    if (operation.getResourceType() == ProvisioningResourceType.HEADER) {
      headerRepository
          .findByIdAndCompanyId(operation.getResourceId(), operation.getCompanyId())
          .ifPresent(
              header -> {
                header.setProviderMappingStatus(ProviderMappingStatus.ACTION_REQUIRED);
                headerRepository.save(header);
              });
    } else if (operation.getResourceType() == ProvisioningResourceType.TEMPLATE_BINDING) {
      bindingRepository
          .findByIdAndCompanyId(operation.getResourceId(), operation.getCompanyId())
          .ifPresent(
              binding -> {
                binding.setStatus(ProviderBindingStatus.ACTION_REQUIRED);
                binding.setRejectionReason("UnifyDesk provider mapping review required");
                bindingRepository.save(binding);
                versionRepository
                    .findByIdAndCompanyId(binding.getTemplateVersionId(), operation.getCompanyId())
                    .ifPresent(
                        version -> {
                          if (version.getDltStatus() == DltTemplateStatus.APPROVED_DLT) {
                            version.setStatus(TemplateVersionStatus.APPROVED_DLT);
                            versionRepository.save(version);
                          }
                        });
              });
    }
  }

  private Duration backoff(int attempt) {
    return Duration.ofSeconds(Math.min(900L, 2L << Math.min(attempt, 8)));
  }

  private record Work(
      Long operationId,
      ProvisioningResourceType resourceType,
      Long resourceId,
      ManagedSmsProviderAdapter.ProviderRequest request) {}
}
