package com.messaging.ads.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.messaging.ads.entity.AdSet;
import com.messaging.ads.google.dto.GoogleKeywordSyncResponse;
import com.messaging.ads.repository.AdSetRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GoogleAdsKeywordSyncService {

  private final AdSetRepository adSetRepository;
  private final GoogleAdsAccountContextService contextService;
  private final GoogleAdsApiClient apiClient;
  private final GoogleAdsKeywordPersistenceService persistenceService;

  public GoogleKeywordSyncResponse sync(Long companyId, Long adSetId) {
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

  public JsonNode keyword(
      GoogleAdsAccountContextService.Context context,
      String externalAdGroupId,
      String externalKeywordId) {
    String criterionId = criterionId(externalKeywordId);
    List<JsonNode> rows =
        apiClient.searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            query(externalAdGroupId, criterionId));
    if (rows.isEmpty()) {
      throw new NotFoundException("Google Ads keyword not found");
    }
    return rows.get(0);
  }

  private String query(String adGroupId, String criterionId) {
    String query =
        "SELECT campaign.id, ad_group.id, ad_group_criterion.resource_name, "
            + "ad_group_criterion.criterion_id, ad_group_criterion.status, "
            + "ad_group_criterion.negative, ad_group_criterion.cpc_bid_micros, "
            + "ad_group_criterion.keyword.text, ad_group_criterion.keyword.match_type, "
            + "ad_group_criterion.quality_info.quality_score, ad_group_criterion.final_urls "
            + "FROM ad_group_criterion WHERE ad_group.id = "
            + numericId(adGroupId)
            + " AND ad_group_criterion.type = 'KEYWORD'";
    if (criterionId != null) {
      query += " AND ad_group_criterion.criterion_id = " + numericId(criterionId) + " LIMIT 1";
    } else {
      query += " AND ad_group_criterion.status != 'REMOVED'";
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

  private String criterionId(String externalKeywordId) {
    if (externalKeywordId == null || externalKeywordId.isBlank()) {
      throw new BadRequestException("Invalid Google Ads keyword ID");
    }
    int separator = externalKeywordId.lastIndexOf('~');
    return separator >= 0 ? externalKeywordId.substring(separator + 1) : externalKeywordId;
  }

  private String numericId(String value) {
    if (value == null || !value.matches("\\d+")) {
      throw new BadRequestException("Invalid Google Ads resource ID");
    }
    return value;
  }
}
