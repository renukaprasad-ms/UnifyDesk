package com.messaging.ads.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.messaging.ads.entity.AdSet;
import com.messaging.ads.google.dto.GoogleAdSyncResponse;
import com.messaging.ads.repository.AdSetRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GoogleAdsAdSyncService {

  private final AdSetRepository adSetRepository;
  private final GoogleAdsAccountContextService contextService;
  private final GoogleAdsApiClient apiClient;
  private final GoogleAdsAdPersistenceService persistenceService;

  public GoogleAdSyncResponse sync(Long companyId, Long adSetId) {
    AdSet adGroup = requireAdGroup(companyId, adSetId);
    var context = contextService.requireManaged(companyId, adGroup.getAdAccountId());
    List<JsonNode> rows =
        apiClient.searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            query(adGroup.getExternalAdSetId(), null));
    return persistenceService.persist(companyId, adSetId, rows);
  }

  public JsonNode ad(
      GoogleAdsAccountContextService.Context context,
      String externalAdGroupId,
      String externalAdId) {
    String adId = adId(externalAdId);
    List<JsonNode> rows =
        apiClient.searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            query(externalAdGroupId, adId));
    if (rows.isEmpty()) {
      throw new NotFoundException("Google Ads ad not found");
    }
    return rows.get(0);
  }

  private String query(String adGroupId, String adId) {
    String query =
        "SELECT campaign.id, ad_group.id, ad_group_ad.resource_name, "
            + "ad_group_ad.status, ad_group_ad.primary_status, "
            + "ad_group_ad.policy_summary.approval_status, "
            + "ad_group_ad.policy_summary.review_status, "
            + "ad_group_ad.ad.id, ad_group_ad.ad.name, ad_group_ad.ad.type, "
            + "ad_group_ad.ad.final_urls, ad_group_ad.ad.final_url_suffix, "
            + "ad_group_ad.ad.url_custom_parameters, "
            + "ad_group_ad.ad.app_ad.headlines, "
            + "ad_group_ad.ad.app_ad.descriptions, "
            + "ad_group_ad.ad.app_ad.images, "
            + "ad_group_ad.ad.app_ad.youtube_videos, "
            + "ad_group_ad.ad.app_engagement_ad.headlines, "
            + "ad_group_ad.ad.app_engagement_ad.descriptions, "
            + "ad_group_ad.ad.app_engagement_ad.images, "
            + "ad_group_ad.ad.app_engagement_ad.videos, "
            + "ad_group_ad.ad.app_pre_registration_ad.headlines, "
            + "ad_group_ad.ad.app_pre_registration_ad.descriptions, "
            + "ad_group_ad.ad.app_pre_registration_ad.images, "
            + "ad_group_ad.ad.app_pre_registration_ad.youtube_videos, "
            + "ad_group_ad.ad.responsive_search_ad.headlines, "
            + "ad_group_ad.ad.responsive_search_ad.descriptions, "
            + "ad_group_ad.ad.responsive_search_ad.path1, "
            + "ad_group_ad.ad.responsive_search_ad.path2, "
            + "ad_group_ad.ad.responsive_display_ad.business_name, "
            + "ad_group_ad.ad.responsive_display_ad.headlines, "
            + "ad_group_ad.ad.responsive_display_ad.long_headline, "
            + "ad_group_ad.ad.responsive_display_ad.descriptions, "
            + "ad_group_ad.ad.responsive_display_ad.marketing_images, "
            + "ad_group_ad.ad.responsive_display_ad.square_marketing_images, "
            + "ad_group_ad.ad.demand_gen_video_responsive_ad.business_name, "
            + "ad_group_ad.ad.demand_gen_video_responsive_ad.headlines, "
            + "ad_group_ad.ad.demand_gen_video_responsive_ad.long_headlines, "
            + "ad_group_ad.ad.demand_gen_video_responsive_ad.descriptions, "
            + "ad_group_ad.ad.demand_gen_video_responsive_ad.videos, "
            + "ad_group_ad.ad.demand_gen_video_responsive_ad.logo_images, "
            + "ad_group_ad.ad.demand_gen_multi_asset_ad.business_name, "
            + "ad_group_ad.ad.demand_gen_multi_asset_ad.headlines, "
            + "ad_group_ad.ad.demand_gen_multi_asset_ad.descriptions, "
            + "ad_group_ad.ad.demand_gen_multi_asset_ad.marketing_images, "
            + "ad_group_ad.ad.demand_gen_multi_asset_ad.square_marketing_images, "
            + "ad_group_ad.ad.demand_gen_multi_asset_ad.portrait_marketing_images, "
            + "ad_group_ad.ad.demand_gen_multi_asset_ad.logo_images, "
            + "ad_group_ad.ad.demand_gen_carousel_ad.business_name, "
            + "ad_group_ad.ad.demand_gen_carousel_ad.headline, "
            + "ad_group_ad.ad.demand_gen_carousel_ad.description, "
            + "ad_group_ad.ad.demand_gen_carousel_ad.logo_image, "
            + "ad_group_ad.ad.demand_gen_carousel_ad.carousel_cards "
            + "FROM ad_group_ad WHERE ad_group.id = "
            + numericId(adGroupId);
    if (adId != null) {
      query += " AND ad_group_ad.ad.id = " + numericId(adId) + " LIMIT 1";
    } else {
      query += " AND ad_group_ad.status != 'REMOVED'";
    }
    return query;
  }

  private AdSet requireAdGroup(Long companyId, Long adSetId) {
    AdSet adGroup =
        adSetRepository
            .findByIdAndCompanyId(adSetId, companyId)
            .orElseThrow(() -> new NotFoundException("Advertising ad group not found"));
    if (adGroup.getProvider() != com.messaging.ads.enums.AdProvider.GOOGLE_ADS
        || !adGroup.isAccessible()) {
      throw new BadRequestException("Google ad group is not available");
    }
    return adGroup;
  }

  private String adId(String externalAdId) {
    if (externalAdId == null || externalAdId.isBlank()) {
      throw new BadRequestException("Invalid Google Ads ad ID");
    }
    int separator = externalAdId.lastIndexOf('~');
    return separator >= 0 ? externalAdId.substring(separator + 1) : externalAdId;
  }

  private String numericId(String value) {
    if (value == null || !value.matches("\\d+")) {
      throw new BadRequestException("Invalid Google Ads resource ID");
    }
    return value;
  }
}
