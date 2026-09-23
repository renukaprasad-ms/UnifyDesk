package com.messaging.ads.google;

import com.messaging.ads.dto.AdAccountHealthResponse;
import com.messaging.common.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GoogleAdsHealthService {

  private final GoogleAdsAccountContextService contextService;
  private final GoogleAdsApiClient apiClient;
  private final GoogleAdsHealthPersistenceService persistenceService;

  public AdAccountHealthResponse sync(Long companyId, Long accountId) {
    var context = contextService.requireManaged(companyId, accountId);
    var rows =
        apiClient.searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            "SELECT customer.id, customer.status, customer.manager, customer.test_account, "
                + "customer.auto_tagging_enabled, customer.optimization_score, "
                + "customer.optimization_score_weight, "
                + "customer.conversion_tracking_setting.conversion_tracking_status, "
                + "customer.conversion_tracking_setting.google_ads_conversion_customer, "
                + "customer.conversion_tracking_setting.enhanced_conversions_for_leads_enabled, "
                + "customer.pay_per_conversion_eligibility_failure_reasons "
                + "FROM customer LIMIT 1");
    if (rows.isEmpty()) {
      throw new BadRequestException("Google Ads did not return account health information");
    }
    return persistenceService.persist(companyId, accountId, rows.get(0));
  }
}
