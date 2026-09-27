package com.messaging.managedsms.runtime;

import java.time.Instant;
import java.util.Optional;

public interface ManagedSmsRuntimeRepository {

  Optional<ManagedTemplateRuntime> findTemplateForCompose(
      Long companyId, Long senderId, Long templateId);

  Optional<ManagedTemplateRuntime> findTemplateForLaunch(
      Long companyId, Long senderId, Long templateId, Long versionNo);

  Optional<DeliveryRuntime> findDeliveryRuntime(Long companyId, Long runId, Long senderId);

  Optional<CorrelationRuntime> findCorrelation(String correlationToken);

  boolean tenantSendingEnabled(Long companyId);

  record ManagedTemplateRuntime(
      Long companyId,
      Long senderId,
      Long connectionId,
      Long tenantId,
      Instant tenantCreatedAt,
      String tenantStatus,
      boolean tenantSendingEnabled,
      Instant tenantLastProviderSyncAt,
      Long dltProfileId,
      String peId,
      String profileStatus,
      String tmChainStatus,
      Instant profileCheckedAt,
      Instant profileStaleAfter,
      Long headerId,
      String header,
      String messageCategory,
      String headerStatus,
      String headerProviderMappingStatus,
      Instant headerCheckedAt,
      Instant headerStaleAfter,
      Long templateId,
      Long templateVersionId,
      Long versionNo,
      String dltTemplateId,
      String approvedBody,
      String bodyFingerprint,
      String variablesSchemaJson,
      String ctaPolicyJson,
      String rulesetVersion,
      String dltStatus,
      String templateVersionStatus,
      Instant templateCheckedAt,
      Instant templateStaleAfter,
      Long providerBindingId,
      String providerAccountKey,
      String providerTemplateRef,
      String providerFlowRef,
      String providerBindingStatus,
      Instant providerBindingLastSyncAt,
      String connectionStatus,
      boolean connectionSendingEnabled,
      String managementMode,
      String senderStatus) {

    public boolean activeForCompose() {
      return "READY".equals(tenantStatus)
          && tenantSendingEnabled
          && "ACTIVE".equals(profileStatus)
          && "ACTIVE".equals(tmChainStatus)
          && "ACTIVE".equals(headerStatus)
          && ("READY".equals(headerProviderMappingStatus)
              || "ACTIVE".equals(headerProviderMappingStatus))
          && "ACTIVE".equals(templateVersionStatus)
          && ("APPROVED_DLT".equals(dltStatus)
              || "APPROVED".equals(dltStatus)
              || "ACTIVE".equals(dltStatus))
          && ("ACTIVE".equals(providerBindingStatus)
              || "READY".equals(providerBindingStatus))
          && "ACTIVE".equals(connectionStatus)
          && connectionSendingEnabled
          && "MESSAGING_MANAGED".equals(managementMode)
          && "ACTIVE".equals(senderStatus);
    }
  }

  record DeliveryRuntime(
      Long companyId,
      Long runId,
      Long senderId,
      Long connectionId,
      Long tenantId,
      Instant tenantCreatedAt,
      String tenantStatus,
      boolean tenantSendingEnabled,
      Long dltProfileId,
      String peIdSnapshot,
      String profileStatus,
      String tmChainStatus,
      Long headerId,
      String headerSnapshot,
      String messageCategory,
      Long templateId,
      Long templateVersionId,
      String dltTemplateIdSnapshot,
      String approvedBodySnapshot,
      String bodyFingerprintSnapshot,
      String variablesSchemaSnapshot,
      String ctaPolicySnapshot,
      Long providerBindingId,
      String providerAccountKey,
      String providerTemplateRefSnapshot,
      String providerFlowRefSnapshot,
      String rulesetVersionSnapshot,
      String headerStatus,
      String templateVersionStatus,
      String providerBindingStatus,
      String connectionStatus,
      boolean connectionSendingEnabled,
      String managementMode,
      String senderStatus) {

    public boolean currentlySendable() {
      return "READY".equals(tenantStatus)
          && tenantSendingEnabled
          && "ACTIVE".equals(profileStatus)
          && "ACTIVE".equals(tmChainStatus)
          && "ACTIVE".equals(headerStatus)
          && ("ACTIVE".equals(templateVersionStatus) || "SUPERSEDED".equals(templateVersionStatus))
          && ("ACTIVE".equals(providerBindingStatus)
              || "READY".equals(providerBindingStatus))
          && "ACTIVE".equals(connectionStatus)
          && connectionSendingEnabled
          && "MESSAGING_MANAGED".equals(managementMode)
          && "ACTIVE".equals(senderStatus);
    }
  }

  record CorrelationRuntime(
      String correlationToken,
      Long companyId,
      Long runId,
      Long recipientId,
      int attemptNo,
      String providerRequestId,
      String providerAccountKey) {}
}
