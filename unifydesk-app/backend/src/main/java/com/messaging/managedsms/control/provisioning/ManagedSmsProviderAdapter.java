package com.messaging.managedsms.control.provisioning;

import com.messaging.managedsms.control.enums.ManagedSmsEnums.ProvisioningOperationType;

public interface ManagedSmsProviderAdapter {

  ProviderResult execute(ProviderRequest request);

  record ProviderRequest(
      ProvisioningOperationType operationType,
      Long companyId,
      Long tenantId,
      Long resourceId,
      String providerClientRef,
      String peId,
      String header,
      String dltTemplateId,
      String bodyText) {}

  enum Outcome {
    SUCCEEDED,
    ACTION_REQUIRED,
    RETRYABLE_FAILURE,
    PERMANENT_FAILURE
  }

  record ProviderResult(
      Outcome outcome,
      String code,
      String safeMessage,
      String providerAccountRef,
      String providerClientRef,
      String providerTemplateRef,
      String providerFlowRef,
      String providerMappingRef) {

    public static ProviderResult actionRequired(String code, String safeMessage) {
      return new ProviderResult(
          Outcome.ACTION_REQUIRED, code, safeMessage, null, null, null, null, null);
    }
  }
}
