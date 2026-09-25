package com.messaging.ads.dto;

import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdAccountBilling;
import com.messaging.ads.enums.AdProvider;
import java.time.Instant;

public record AdAccountBillingResponse(
    boolean synced,
    String id,
    String companyId,
    String adAccountId,
    AdProvider provider,
    String currency,
    Long balanceMinor,
    Long amountSpentMinor,
    Long spendCapMinor,
    Long remainingSpendCapMinor,
    boolean spendCapEnabled,
    Long minCampaignGroupSpendCapMinor,
    Long minDailyBudgetMinor,
    Boolean prepayAccount,
    Boolean taxIdRequired,
    String fundingSourceId,
    Integer fundingSourceType,
    String fundingSourceDisplay,
    Integer taxIdStatus,
    String taxIdType,
    Integer disableReason,
    String businessCountryCode,
    Instant lastSyncAt) {

  public static AdAccountBillingResponse from(AdAccountBilling billing) {
    Long spendCap = billing.getSpendCapMinor();
    Long amountSpent = billing.getAmountSpentMinor();
    boolean capEnabled = spendCap != null && spendCap > 0;
    Long remaining =
        capEnabled && amountSpent != null
            ? Math.max(0L, spendCap - amountSpent)
            : null;

    return new AdAccountBillingResponse(
        true,
        billing.getId().toString(),
        billing.getCompanyId().toString(),
        billing.getAdAccountId().toString(),
        billing.getProvider(),
        billing.getCurrency(),
        billing.getBalanceMinor(),
        billing.getAmountSpentMinor(),
        spendCap,
        remaining,
        capEnabled,
        billing.getMinCampaignGroupSpendCapMinor(),
        billing.getMinDailyBudgetMinor(),
        billing.getPrepayAccount(),
        billing.getTaxIdRequired(),
        billing.getFundingSourceId(),
        billing.getFundingSourceType(),
        billing.getFundingSourceDisplay(),
        billing.getTaxIdStatus(),
        billing.getTaxIdType(),
        billing.getDisableReason(),
        billing.getBusinessCountryCode(),
        billing.getLastSyncAt());
  }

  public static AdAccountBillingResponse empty(AdAccount account) {
    return new AdAccountBillingResponse(
        false,
        null,
        account.getCompanyId().toString(),
        account.getId().toString(),
        account.getProvider(),
        account.getCurrency(),
        null,
        null,
        null,
        null,
        false,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }
}
