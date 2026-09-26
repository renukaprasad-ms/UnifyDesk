package com.messaging.ads.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.google.dto.GoogleAdGroupSyncResponse;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GoogleAdsAdGroupSyncService {

  private final AdCampaignRepository campaignRepository;
  private final GoogleAdsAccountContextService contextService;
  private final GoogleAdsApiClient apiClient;
  private final GoogleAdsAdGroupPersistenceService persistenceService;

  public GoogleAdGroupSyncResponse sync(Long companyId, Long campaignId) {
    AdCampaign campaign = requireCampaign(companyId, campaignId);
    var context =
        contextService.requireManaged(companyId, campaign.getAdAccountId());
    List<JsonNode> rows =
        apiClient.searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            query(campaign.getExternalCampaignId(), null));
    return persistenceService.persist(companyId, campaignId, rows);
  }

  public JsonNode adGroup(
      GoogleAdsAccountContextService.Context context,
      String externalCampaignId,
      String externalAdGroupId) {
    List<JsonNode> rows =
        apiClient.searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            query(externalCampaignId, externalAdGroupId));
    if (rows.isEmpty()) {
      throw new NotFoundException("Google Ads ad group not found");
    }
    return rows.get(0);
  }

  private String query(String campaignId, String adGroupId) {
    String query =
        "SELECT campaign.id, ad_group.id, ad_group.name, ad_group.status, "
            + "ad_group.primary_status, ad_group.type, ad_group.cpc_bid_micros, "
            + "ad_group.target_cpa_micros, "
            + "ad_group.ai_max_ad_group_setting.disable_search_term_matching "
            + "FROM ad_group WHERE campaign.id = "
            + numericId(campaignId);
    if (adGroupId != null) {
      query += " AND ad_group.id = " + numericId(adGroupId) + " LIMIT 1";
    } else {
      query += " AND ad_group.status != 'REMOVED'";
    }
    return query;
  }

  private AdCampaign requireCampaign(Long companyId, Long campaignId) {
    AdCampaign campaign =
        campaignRepository
            .findByIdAndCompanyId(campaignId, companyId)
            .orElseThrow(() -> new NotFoundException("Advertising campaign not found"));
    if (campaign.getProvider() != com.messaging.ads.enums.AdProvider.GOOGLE_ADS
        || !campaign.isAccessible()) {
      throw new BadRequestException("Google campaign is not available");
    }
    return campaign;
  }

  private String numericId(String value) {
    if (value == null || !value.matches("\\d+")) {
      throw new BadRequestException("Invalid Google Ads resource ID");
    }
    return value;
  }
}
