package com.messaging.ads.meta;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.dto.AdAccountBillingResponse;
import com.messaging.ads.entity.AdAccountBilling;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.meta.MetaAdsApiClient.AdAccountBillingSummary;
import com.messaging.ads.repository.AdAccountBillingRepository;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MetaAdsBillingPersistenceService {

  private final AdAccountBillingRepository billingRepository;
  private final ObjectMapper objectMapper;

  @Transactional
  public AdAccountBillingResponse persist(
      Long companyId,
      Long accountId,
      AdAccountBillingSummary summary) {
    AdAccountBilling billing =
        billingRepository
            .findByAdAccountIdAndProvider(accountId, AdProvider.META_ADS)
            .orElseGet(AdAccountBilling::new);

    if (billing.getId() == null) {
      billing.setCompanyId(companyId);
      billing.setAdAccountId(accountId);
      billing.setProvider(AdProvider.META_ADS);
    }

    billing.setCurrency(blank(summary.currency()));
    billing.setBalanceMinor(minor(summary.balance()));
    billing.setAmountSpentMinor(minor(summary.amountSpent()));
    billing.setSpendCapMinor(minor(summary.spendCap()));
    billing.setMinCampaignGroupSpendCapMinor(minor(summary.minCampaignGroupSpendCap()));
    billing.setMinDailyBudgetMinor(summary.minDailyBudget());
    billing.setPrepayAccount(summary.prepayAccount());
    billing.setTaxIdRequired(summary.taxIdRequired());
    billing.setFundingSourceId(
        summary.fundingSourceDetails() != null
                && blank(summary.fundingSourceDetails().id()) != null
            ? summary.fundingSourceDetails().id().trim()
            : blank(summary.fundingSource()));
    billing.setFundingSourceType(
        summary.fundingSourceDetails() == null
            ? null
            : summary.fundingSourceDetails().type());
    billing.setFundingSourceDisplay(
        summary.fundingSourceDetails() == null
            ? null
            : blank(summary.fundingSourceDetails().displayString()));
    billing.setFundingSourceDetailsJson(json(summary.fundingSourceDetails()));
    billing.setExpiredFundingSourceDetailsJson(
        json(summary.expiredFundingSourceDetails()));
    billing.setExtendedCreditInvoiceGroupJson(
        json(summary.extendedCreditInvoiceGroup()));
    billing.setTaxIdStatus(summary.taxIdStatus());
    billing.setTaxIdType(blank(summary.taxIdType()));
    billing.setDisableReason(summary.disableReason());
    billing.setBusinessCountryCode(blank(summary.businessCountryCode()));
    billing.setProviderPayloadJson(json(summary));
    billing.setLastSyncAt(Instant.now());

    return AdAccountBillingResponse.from(billingRepository.save(billing));
  }

  private Long minor(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return new BigDecimal(value.trim()).longValueExact();
    } catch (ArithmeticException | NumberFormatException ignored) {
      return null;
    }
  }

  private String blank(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private String json(Object value) {
    if (value == null) return null;
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException(
          "Unable to serialize Meta billing provider data", exception);
    }
  }
}
