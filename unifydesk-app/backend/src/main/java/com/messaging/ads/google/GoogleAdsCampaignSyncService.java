package com.messaging.ads.google;

import com.messaging.ads.google.dto.GoogleCampaignSyncResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GoogleAdsCampaignSyncService {

  private final GoogleAdsAccountContextService contextService;
  private final GoogleAdsApiClient apiClient;
  private final GoogleAdsCampaignPersistenceService persistenceService;

  public GoogleCampaignSyncResponse sync(Long companyId, Long accountId) {
    var context = contextService.requireManaged(companyId, accountId);
    List<com.fasterxml.jackson.databind.JsonNode> rows =
        apiClient.searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            query(null));
    return persistenceService.persist(companyId, accountId, rows);
  }

  public com.fasterxml.jackson.databind.JsonNode campaign(
      GoogleAdsAccountContextService.Context context, String externalCampaignId) {
    List<com.fasterxml.jackson.databind.JsonNode> rows =
        apiClient.searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            query(numericId(externalCampaignId)));
    if (rows.isEmpty()) {
      throw new com.messaging.common.exception.NotFoundException(
          "Google Ads campaign not found");
    }
    return rows.get(0);
  }

  private String query(String campaignId) {
    String query =
        "SELECT campaign.id, campaign.name, campaign.status, campaign.primary_status, "
            + "campaign.advertising_channel_type, campaign.advertising_channel_sub_type, "
            + "campaign.bidding_strategy_type, campaign.start_date_time, campaign.end_date_time, "
            + "campaign.app_campaign_setting.app_id, campaign.app_campaign_setting.app_store, "
            + "campaign.app_campaign_setting.bidding_strategy_goal_type, "
            + "campaign.selective_optimization.conversion_actions, "
            + "campaign.optimization_goal_setting.optimization_goal_types, "
            + "campaign.ai_max_setting.enable_ai_max, campaign.ai_max_setting.bundling_required, "
            + "campaign.asset_automation_settings, campaign.text_guidelines.term_exclusions, "
            + "campaign.text_guidelines.messaging_restrictions, "
            + "campaign.aca_migration_date_time, campaign.broad_match_migration_date_time, "
            + "campaign.campaign_budget, campaign_budget.resource_name, "
            + "campaign_budget.amount_micros "
            + "FROM campaign";
    if (campaignId != null) {
      return query + " WHERE campaign.id = " + campaignId + " LIMIT 1";
    }
    return query + " WHERE campaign.status != 'REMOVED'";
  }

  private String numericId(String value) {
    if (value == null || !value.matches("\\d+")) {
      throw new com.messaging.common.exception.BadRequestException(
          "Invalid Google Ads campaign ID");
    }
    return value;
  }
}
