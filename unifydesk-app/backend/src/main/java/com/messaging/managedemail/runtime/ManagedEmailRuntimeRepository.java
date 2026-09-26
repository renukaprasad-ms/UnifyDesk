package com.messaging.managedemail.runtime;

import java.util.Optional;

public interface ManagedEmailRuntimeRepository {

  Optional<ManagedSenderRuntime> findSenderRuntime(Long companyId, Long senderId);

  Optional<ManagedRecipientRuntime> findRecipientRuntime(Long recipientId);

  Optional<ManagedTenantRuntime> findTenantRuntime(Long companyId);

  void suspendTenant(Long companyId, Long tenantId, String reason);

  record ManagedTenantRuntime(
      Long companyId,
      Long tenantId,
      String status,
      String sendingStatus,
      String reputationStatus,
      String rampStage,
      String region,
      String providerTenantName) {

    public boolean isSendingEnabled() {
      return "ACTIVE".equals(status) && "ENABLED".equals(sendingStatus);
    }
  }

  record ManagedSenderRuntime(
      Long companyId,
      Long senderId,
      Long connectionId,
      Long tenantId,
      Long domainId,
      String senderStatus,
      String connectionStatus,
      String managementMode,
      boolean connectionSendingEnabled,
      String tenantStatus,
      String tenantSendingStatus,
      String tenantReputationStatus,
      String rampStage,
      String domainStatus,
      String normalizedDomain,
      String region,
      String providerTenantName,
      String configurationSetName,
      Double providerMaxSendRate,
      Double providerMax24HourSend) {

    public boolean isSendable() {
      return "ACTIVE".equals(senderStatus)
          && "ACTIVE".equals(connectionStatus)
          && "MESSAGING_MANAGED".equals(managementMode)
          && connectionSendingEnabled
          && "ACTIVE".equals(tenantStatus)
          && "ENABLED".equals(tenantSendingStatus)
          && "ACTIVE".equals(domainStatus);
    }
  }

  record ManagedRecipientRuntime(
      Long recipientId,
      Long companyId,
      Long runId,
      Long senderId,
      Long connectionId,
      Long tenantId,
      Long domainId,
      String recipientStatus,
      String normalizedDestination,
      String providerMessageId,
      String senderStatus,
      String connectionStatus,
      String managementMode,
      boolean connectionSendingEnabled,
      String tenantStatus,
      String tenantSendingStatus,
      String tenantReputationStatus,
      String rampStage,
      String domainStatus,
      String normalizedDomain,
      String region,
      String providerTenantName,
      String configurationSetName,
      Double providerMaxSendRate,
      Double providerMax24HourSend) {

    public boolean isSendable() {
      return "ACTIVE".equals(senderStatus)
          && "ACTIVE".equals(connectionStatus)
          && "MESSAGING_MANAGED".equals(managementMode)
          && connectionSendingEnabled
          && "ACTIVE".equals(tenantStatus)
          && "ENABLED".equals(tenantSendingStatus)
          && "ACTIVE".equals(domainStatus);
    }
  }
}
