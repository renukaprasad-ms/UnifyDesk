package com.messaging.ads.google;

import com.messaging.ads.dto.AdKeywordResponse;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.entity.AdKeyword;
import com.messaging.ads.entity.AdSet;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.CreateGoogleKeywordRequest;
import com.messaging.ads.google.dto.UpdateGoogleKeywordRequest;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.ads.repository.AdKeywordRepository;
import com.messaging.ads.repository.AdSetRepository;
import com.messaging.ads.service.AdActivityLogService;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GoogleAdsKeywordMutationService {

  private static final Set<String> MATCH_TYPES = Set.of("BROAD", "PHRASE", "EXACT");

  private final AdSetRepository adSetRepository;
  private final AdCampaignRepository campaignRepository;
  private final AdKeywordRepository keywordRepository;
  private final GoogleAdsAccountContextService contextService;
  private final GoogleAdsApiClient apiClient;
  private final GoogleAdsKeywordSyncService syncService;
  private final GoogleAdsKeywordPersistenceService persistenceService;
  private final AdActivityLogService activityLogService;

  public AdKeywordResponse create(
      Long companyId,
      Long adSetId,
      CreateGoogleKeywordRequest request,
      Long actorUserId) {
    AdSet adGroup = requireAdGroup(companyId, adSetId);
    var context = contextService.requireManaged(companyId, adGroup.getAdAccountId());

    String matchType = request.matchType().trim().toUpperCase();
    if (!MATCH_TYPES.contains(matchType)) {
      throw new BadRequestException("Google keyword match type must be BROAD, PHRASE, or EXACT");
    }

    Map<String, Object> create = new LinkedHashMap<>();
    create.put(
        "adGroup",
        apiClient.customerResourceName(
            context.account().getExternalAccountId(),
            "adGroups",
            adGroup.getExternalAdSetId()));
    create.put("status", "ENABLED");
    create.put("keyword", Map.of("text", request.text().trim(), "matchType", matchType));
    create.put("negative", request.negative());

    if (!request.negative() && request.cpcBidMinor() != null) {
      create.put(
          "cpcBidMicros",
          GoogleAdsMoney.minorToMicros(
              request.cpcBidMinor(), context.account().getCurrency()));
    }

    var response =
        apiClient.mutate(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            "adGroupCriteria",
            List.of(Map.of("create", create)));

    String resourceName = apiClient.firstMutatedResourceName(response);
    String externalId = suffix(resourceName);
    AdKeywordResponse after = refresh(context, adGroup, externalId);

    activityLogService.record(
        companyId,
        adGroup.getAdAccountId(),
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_KEYWORD_CREATED",
        "KEYWORD",
        externalId,
        null,
        after,
        Map.of("matchType", matchType, "negative", request.negative()));
    return after;
  }

  public AdKeywordResponse update(
      Long companyId,
      Long keywordId,
      UpdateGoogleKeywordRequest request,
      Long actorUserId) {
    AdKeyword keyword = requireKeyword(companyId, keywordId);
    AdSet adGroup = requireAdGroup(companyId, keyword.getAdSetId());
    var context = contextService.requireManaged(companyId, keyword.getAdAccountId());
    AdKeywordResponse before = AdKeywordResponse.from(keyword);

    Map<String, Object> update = new LinkedHashMap<>();
    update.put(
        "resourceName",
        apiClient.customerResourceName(
            context.account().getExternalAccountId(),
            "adGroupCriteria",
            keyword.getExternalKeywordId()));
    List<String> masks = new ArrayList<>();

    if (request.cpcBidMinor() != null) {
      if (keyword.isNegative()) {
        throw new BadRequestException("Negative Google keywords do not support CPC bids");
      }
      update.put(
          "cpcBidMicros",
          GoogleAdsMoney.minorToMicros(
              request.cpcBidMinor(), context.account().getCurrency()));
      masks.add("cpc_bid_micros");
    }
    if (request.status() != null) {
      String status = normalizeStatus(request.status());
      update.put("status", status);
      masks.add("status");
    }

    if (masks.isEmpty()) {
      throw new BadRequestException("No Google keyword changes were supplied");
    }

    apiClient.mutate(
        context.accessToken(),
        context.account().getExternalAccountId(),
        context.loginCustomerId(),
        "adGroupCriteria",
        List.of(Map.of("update", update, "updateMask", String.join(",", masks))));

    AdKeywordResponse after =
        refresh(context, adGroup, keyword.getExternalKeywordId());

    activityLogService.record(
        companyId,
        keyword.getAdAccountId(),
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_KEYWORD_UPDATED",
        "KEYWORD",
        keyword.getExternalKeywordId(),
        before,
        after,
        null);
    return after;
  }

  private AdKeywordResponse refresh(
      GoogleAdsAccountContextService.Context context,
      AdSet adGroup,
      String externalKeywordId) {
    var row =
        syncService.keyword(
            context, adGroup.getExternalAdSetId(), externalKeywordId);
    return AdKeywordResponse.from(
        persistenceService.persistOne(
            adGroup.getCompanyId(), adGroup.getId(), row));
  }

  private AdSet requireAdGroup(Long companyId, Long adSetId) {
    AdSet adGroup =
        adSetRepository
            .findByIdAndCompanyId(adSetId, companyId)
            .orElseThrow(() -> new NotFoundException("Advertising ad group not found"));
    if (adGroup.getProvider() != AdProvider.GOOGLE_ADS || !adGroup.isAccessible()) {
      throw new BadRequestException("Google ad group is not available for management");
    }
    AdCampaign campaign =
        campaignRepository
            .findByIdAndCompanyId(adGroup.getCampaignId(), companyId)
            .orElseThrow(() -> new NotFoundException("Advertising campaign not found"));
    if (!"SEARCH".equalsIgnoreCase(campaign.getObjective())) {
      throw new BadRequestException(
          "Google keywords are supported only for Search campaign ad groups");
    }
    return adGroup;
  }

  private AdKeyword requireKeyword(Long companyId, Long keywordId) {
    AdKeyword keyword =
        keywordRepository
            .findByIdAndCompanyId(keywordId, companyId)
            .orElseThrow(() -> new NotFoundException("Advertising keyword not found"));
    if (keyword.getProvider() != AdProvider.GOOGLE_ADS || !keyword.isAccessible()) {
      throw new BadRequestException("Google keyword is not available for management");
    }
    return keyword;
  }

  private String normalizeStatus(String value) {
    String status = value.trim().toUpperCase();
    if ("ACTIVE".equals(status)) status = "ENABLED";
    if (!"ENABLED".equals(status) && !"PAUSED".equals(status)) {
      throw new BadRequestException("Google keyword status must be ENABLED/ACTIVE or PAUSED");
    }
    return status;
  }

  private String suffix(String resourceName) {
    if (resourceName == null || resourceName.isBlank()) {
      throw new BadRequestException("Google Ads did not return the created keyword");
    }
    int slash = resourceName.lastIndexOf('/');
    if (slash < 0 || slash == resourceName.length() - 1) {
      throw new BadRequestException("Invalid Google Ads keyword resource name");
    }
    return resourceName.substring(slash + 1);
  }
}
