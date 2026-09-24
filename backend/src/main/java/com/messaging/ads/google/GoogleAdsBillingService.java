package com.messaging.ads.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.messaging.ads.dto.AdAccountBillingResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GoogleAdsBillingService {

  private final GoogleAdsAccountContextService contextService;
  private final GoogleAdsApiClient apiClient;
  private final GoogleAdsBillingPersistenceService persistenceService;

  public AdAccountBillingResponse sync(Long companyId, Long accountId) {
    var context = contextService.requireManaged(companyId, accountId);

    List<JsonNode> setups =
        apiClient.searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            "SELECT billing_setup.id, billing_setup.resource_name, billing_setup.status, "
                + "billing_setup.payments_account, "
                + "billing_setup.payments_account_info.payments_account_id, "
                + "billing_setup.payments_account_info.payments_account_name, "
                + "billing_setup.payments_account_info.payments_profile_id, "
                + "billing_setup.payments_account_info.payments_profile_name, "
                + "billing_setup.start_date_time, billing_setup.end_date_time, "
                + "billing_setup.end_time_type FROM billing_setup");

    List<JsonNode> budgets =
        apiClient.searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            "SELECT account_budget.resource_name, account_budget.status, "
                + "account_budget.billing_setup, "
                + "account_budget.approved_spending_limit_micros, "
                + "account_budget.approved_spending_limit_type, "
                + "account_budget.proposed_spending_limit_micros, "
                + "account_budget.proposed_spending_limit_type, "
                + "account_budget.adjusted_spending_limit_micros, "
                + "account_budget.adjusted_spending_limit_type, "
                + "account_budget.approved_start_date_time, "
                + "account_budget.approved_end_date_time "
                + "FROM account_budget");

    JsonNode setup = preferredSetup(setups);
    JsonNode budget = preferredBudget(budgets);

    return persistenceService.persist(
        companyId,
        accountId,
        context.account().getCurrency(),
        setup,
        budget);
  }

  private JsonNode preferredSetup(List<JsonNode> rows) {
    for (JsonNode row : rows) {
      String status = row.path("billingSetup").path("status").asText("");
      if ("APPROVED".equals(status) || "APPROVED_HELD".equals(status)) return row;
    }
    return rows.isEmpty() ? null : rows.get(0);
  }

  private JsonNode preferredBudget(List<JsonNode> rows) {
    for (JsonNode row : rows) {
      String status = row.path("accountBudget").path("status").asText("");
      if ("APPROVED".equals(status)) return row;
    }
    return rows.isEmpty() ? null : rows.get(0);
  }
}
