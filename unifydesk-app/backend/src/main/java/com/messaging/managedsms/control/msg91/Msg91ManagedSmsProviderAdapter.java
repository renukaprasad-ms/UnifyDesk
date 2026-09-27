package com.messaging.managedsms.control.msg91;

import com.messaging.managedsms.control.config.ManagedSmsProperties;
import com.messaging.managedsms.control.provisioning.ManagedSmsProviderAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class Msg91ManagedSmsProviderAdapter implements ManagedSmsProviderAdapter {

  private final ManagedSmsProperties properties;

  @Override
  public ProviderResult execute(ProviderRequest request) {
    if (!properties.isEnabled()) {
      return ProviderResult.actionRequired(
          "MANAGED_SMS_DISABLED", "Managed SMS is not enabled for provider provisioning.");
    }
    if (!properties.hasMasterAuthKey()) {
      return ProviderResult.actionRequired(
          "MSG91_MASTER_CREDENTIAL_REQUIRED",
          "Managed SMS provider provisioning requires platform configuration.");
    }

    /*
     * MSG91 documents B2B/subaccount and template APIs, but the exact production-contract
     * request/response shape for India PE/Header mapping is a frozen real-account validation
     * item. Never manufacture provider identifiers or READY state from local input.
     */
    return ProviderResult.actionRequired(
        "MSG91_MANUAL_REVIEW_REQUIRED",
        "Provider mapping requires UnifyDesk review before it can be activated.");
  }
}
