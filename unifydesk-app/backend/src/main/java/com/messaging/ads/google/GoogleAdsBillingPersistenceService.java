package com.messaging.ads.google;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.dto.AdAccountBillingResponse;
import com.messaging.ads.entity.AdAccountBilling;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.repository.AdAccountBillingRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GoogleAdsBillingPersistenceService {

  private final AdAccountBillingRepository repository;
  private final ObjectMapper objectMapper;

  @Transactional
  public AdAccountBillingResponse persist(
      Long companyId,
      Long accountId,
      String currency,
      JsonNode billingSetupRow,
      JsonNode accountBudgetRow) {
    AdAccountBilling billing =
        repository
            .findByAdAccountIdAndProvider(accountId, AdProvider.GOOGLE_ADS)
            .orElseGet(AdAccountBilling::new);

    if (billing.getId() == null) {
      billing.setCompanyId(companyId);
      billing.setAdAccountId(accountId);
      billing.setProvider(AdProvider.GOOGLE_ADS);
    }

    billing.setCurrency(currency);
    JsonNode setup =
        billingSetupRow == null ? null : billingSetupRow.path("billingSetup");
    JsonNode payments =
        setup == null ? null : setup.path("paymentsAccountInfo");
    JsonNode budget =
        accountBudgetRow == null ? null : accountBudgetRow.path("accountBudget");

    billing.setFundingSourceId(text(payments, "paymentsAccountId"));
    billing.setFundingSourceDisplay(text(payments, "paymentsAccountName"));
    billing.setFundingSourceType(null);
    billing.setFundingSourceDetailsJson(json(payments));
    billing.setExpiredFundingSourceDetailsJson(null);
    billing.setExtendedCreditInvoiceGroupJson(null);

    Long spendingMicros =
        firstLong(
            longValue(budget, "adjustedSpendingLimitMicros"),
            longValue(budget, "approvedSpendingLimitMicros"),
            longValue(budget, "proposedSpendingLimitMicros"));
    billing.setSpendCapMinor(
        GoogleAdsMoney.microsToMinor(spendingMicros, currency));
    billing.setBalanceMinor(null);
    billing.setAmountSpentMinor(null);
    billing.setMinCampaignGroupSpendCapMinor(null);
    billing.setMinDailyBudgetMinor(null);
    billing.setPrepayAccount(null);
    billing.setTaxIdRequired(null);
    billing.setTaxIdStatus(null);
    billing.setTaxIdType(null);
    billing.setDisableReason(null);
    billing.setBusinessCountryCode(null);

    Map<String, Object> provider = new LinkedHashMap<>();
    provider.put("billingSetup", billingSetupRow);
    provider.put("accountBudget", accountBudgetRow);
    billing.setProviderPayloadJson(json(provider));
    billing.setLastSyncAt(Instant.now());

    return AdAccountBillingResponse.from(repository.save(billing));
  }

  private Long firstLong(Long... values) {
    for (Long value : values) {
      if (value != null) return value;
    }
    return null;
  }

  private Long longValue(JsonNode node, String field) {
    String value = text(node, field);
    if (value == null) return null;
    try {
      return Long.valueOf(value);
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private String text(JsonNode node, String field) {
    if (node == null || node.isMissingNode() || node.isNull()) return null;
    JsonNode value = node.path(field);
    if (value.isMissingNode() || value.isNull()) return null;
    String text = value.asText(null);
    return text == null || text.isBlank() ? null : text.trim();
  }

  private String json(Object value) {
    if (value == null) return null;
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize Google Ads billing data", exception);
    }
  }
}
