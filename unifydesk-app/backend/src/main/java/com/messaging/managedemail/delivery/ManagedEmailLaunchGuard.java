package com.messaging.managedemail.delivery;

import com.messaging.billing.exception.PlanEntitlementException;
import com.messaging.billing.service.EntitlementService;
import com.messaging.billing.service.ManagedFinancialExposureGuard;
import com.messaging.broadcast.entity.BroadcastSender;
import com.messaging.broadcast.entity.BroadcastSenderConnection;
import com.messaging.broadcast.enums.BroadcastSenderManagementMode;
import com.messaging.broadcast.enums.BroadcastSenderProvider;
import com.messaging.common.exception.BadRequestException;
import com.messaging.managedemail.metering.ManagedEmailUsageService;
import com.messaging.managedemail.runtime.ManagedEmailRuntimeProperties;
import com.messaging.managedemail.runtime.ManagedEmailRuntimeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ManagedEmailLaunchGuard {

  private static final String ENABLED_ENTITLEMENT = "MANAGED_EMAIL_ENABLED";

  private final ManagedEmailRuntimeRepository runtimeRepository;
  private final ManagedEmailRuntimeProperties properties;
  private final EntitlementService entitlementService;
  private final ManagedFinancialExposureGuard financialExposureGuard;
  private final ManagedEmailUsageService usageService;
  private final ManagedEmailAddressPolicy addressPolicy;
  private final ManagedEmailProviderBudgetService providerBudgetService;

  public void validateLaunch(
      Long companyId,
      BroadcastSender sender,
      BroadcastSenderConnection connection,
      long recipientCount) {
    if (connection.getManagementMode() != BroadcastSenderManagementMode.MESSAGING_MANAGED) {
      return;
    }
    financialExposureGuard.assertAllowed(companyId);
    if (sender.getProvider() != BroadcastSenderProvider.AMAZON_SES
        || connection.getProvider() != BroadcastSenderProvider.AMAZON_SES) {
      throw new BadRequestException("MANAGED_EMAIL_PROVIDER_MISMATCH");
    }
    if (!properties.isSendingEnabled()) {
      throw new BadRequestException("MANAGED_EMAIL_PLATFORM_DISABLED");
    }
    if (!properties.reputationPolicyConfigured()) {
      throw new BadRequestException("MANAGED_EMAIL_SENDING_SUSPENDED");
    }
    if (!entitlementService.booleanValue(companyId, ENABLED_ENTITLEMENT)) {
      throw new PlanEntitlementException("MANAGED_EMAIL_NOT_ENABLED");
    }

    ManagedEmailRuntimeRepository.ManagedSenderRuntime runtime =
        runtimeRepository
            .findSenderRuntime(companyId, sender.getId())
            .orElseThrow(() -> new BadRequestException("MANAGED_EMAIL_SENDING_SUSPENDED"));

    if (!runtime.companyId().equals(companyId)
        || !runtime.connectionId().equals(connection.getId())
        || !runtime.isSendable()
        || !addressPolicy.matchesBoundDomain(
            runtime.senderFromAddress(), runtime.normalizedDomain())
        || !providerBudgetService.hasUsableProviderSnapshot(runtime)) {
      throw new BadRequestException("MANAGED_EMAIL_SENDING_SUSPENDED");
    }

    long campaignLimit = properties.getCampaignSizeCap();
    if (campaignLimit <= 0 || recipientCount > campaignLimit) {
      throw new BadRequestException("MANAGED_EMAIL_CAMPAIGN_LIMIT_EXCEEDED");
    }
    if (isNewTenant(runtime.rampStage())) {
      long newTenantDailyCap = properties.getNewTenantDailyCap();
      if (newTenantDailyCap <= 0 || recipientCount > newTenantDailyCap) {
        throw new BadRequestException("MANAGED_EMAIL_CAMPAIGN_LIMIT_EXCEEDED");
      }
    }
    if (!usageService.canReserve(companyId, recipientCount)) {
      throw new PlanEntitlementException("MANAGED_EMAIL_MONTHLY_LIMIT_REACHED");
    }
  }

  private boolean isNewTenant(String rampStage) {
    return "NEW".equals(rampStage) || "RAMPING".equals(rampStage);
  }
}
