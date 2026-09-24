package com.messaging.ads.google;

import com.messaging.ads.dto.AdSetResponse;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.entity.AdSet;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.CreateGoogleAdGroupRequest;
import com.messaging.ads.google.dto.UpdateGoogleAdGroupRequest;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.ads.repository.AdSetRepository;
import com.messaging.ads.service.AdActivityLogService;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GoogleAdsAdGroupMutationService {

  private final AdCampaignRepository campaignRepository;
  private final AdSetRepository adSetRepository;
  private final GoogleAdsAccountContextService contextService;
  private final GoogleAdsApiClient apiClient;
  private final GoogleAdsAdGroupSyncService syncService;
  private final GoogleAdsAdGroupPersistenceService persistenceService;
  private final AdActivityLogService activityLogService;

  public AdSetResponse create(
      Long companyId,
      Long campaignId,
      CreateGoogleAdGroupRequest request,
      Long actorUserId) {
    AdCampaign campaign = requireCampaign(companyId, campaignId);
    var context = contextService.requireManaged(companyId, campaign.getAdAccountId());

    Map<String, Object> create = new LinkedHashMap<>();
    create.put("name", request.name().trim());
    create.put(
        "campaign",
        apiClient.customerResourceName(
            context.account().getExternalAccountId(),
            "campaigns",
            campaign.getExternalCampaignId()));
    create.put("status", "PAUSED");
    if ("SHOPPING".equalsIgnoreCase(campaign.getObjective())) {
      create.put("type", "SHOPPING_PRODUCT_ADS");
    }
    if ("DEMAND_GEN".equalsIgnoreCase(campaign.getObjective())
        && request.cpcBidMinor() != null) {
      throw new BadRequestException(
          "Demand Gen uses campaign-level automated bidding; do not set an ad-group CPC bid");
    }
    if (request.cpcBidMinor() != null) {
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
            "adGroups",
            List.of(Map.of("create", create)));
    String externalId = idFromResource(apiClient.firstMutatedResourceName(response));

    AdSetResponse after = refresh(context, campaign, externalId);
    activityLogService.record(
        companyId,
        campaign.getAdAccountId(),
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_AD_GROUP_CREATED",
        "AD_GROUP",
        externalId,
        null,
        after,
        Map.of("initialStatus", "PAUSED"));
    return after;
  }

  public AdSetResponse update(
      Long companyId,
      Long adSetId,
      UpdateGoogleAdGroupRequest request,
      Long actorUserId) {
    AdSet adGroup = requireAdGroup(companyId, adSetId);
    AdCampaign campaign = requireCampaign(companyId, adGroup.getCampaignId());
    var context = contextService.requireManaged(companyId, adGroup.getAdAccountId());
    AdSetResponse before = AdSetResponse.from(adGroup);

    Map<String, Object> update = new LinkedHashMap<>();
    update.put(
        "resourceName",
        apiClient.customerResourceName(
            context.account().getExternalAccountId(),
            "adGroups",
            adGroup.getExternalAdSetId()));
    List<String> masks = new ArrayList<>();

    if (request.name() != null) {
      if (request.name().isBlank()) {
        throw new BadRequestException("Ad group name cannot be blank");
      }
      update.put("name", request.name().trim());
      masks.add("name");
    }
    if (request.cpcBidMinor() != null) {
      update.put(
          "cpcBidMicros",
          GoogleAdsMoney.minorToMicros(
              request.cpcBidMinor(), context.account().getCurrency()));
      masks.add("cpc_bid_micros");
    }
    if (masks.isEmpty()) {
      throw new BadRequestException("No Google ad group changes were supplied");
    }

    apiClient.mutate(
        context.accessToken(),
        context.account().getExternalAccountId(),
        context.loginCustomerId(),
        "adGroups",
        List.of(Map.of("update", update, "updateMask", String.join(",", masks))));

    AdSetResponse after = refresh(context, campaign, adGroup.getExternalAdSetId());
    activityLogService.record(
        companyId,
        adGroup.getAdAccountId(),
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_AD_GROUP_UPDATED",
        "AD_GROUP",
        adGroup.getExternalAdSetId(),
        before,
        after,
        null);
    return after;
  }

  public AdSetResponse setStatus(
      Long companyId,
      Long adSetId,
      String requestedStatus,
      Long actorUserId) {
    AdSet adGroup = requireAdGroup(companyId, adSetId);
    AdCampaign campaign = requireCampaign(companyId, adGroup.getCampaignId());
    var context = contextService.requireManaged(companyId, adGroup.getAdAccountId());
    AdSetResponse before = AdSetResponse.from(adGroup);
    String status = normalizeStatus(requestedStatus);

    Map<String, Object> update =
        Map.of(
            "resourceName",
            apiClient.customerResourceName(
                context.account().getExternalAccountId(),
                "adGroups",
                adGroup.getExternalAdSetId()),
            "status",
            status);
    apiClient.mutate(
        context.accessToken(),
        context.account().getExternalAccountId(),
        context.loginCustomerId(),
        "adGroups",
        List.of(Map.of("update", update, "updateMask", "status")));

    AdSetResponse after = refresh(context, campaign, adGroup.getExternalAdSetId());
    activityLogService.record(
        companyId,
        adGroup.getAdAccountId(),
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_AD_GROUP_STATUS_UPDATED",
        "AD_GROUP",
        adGroup.getExternalAdSetId(),
        before,
        after,
        Map.of("requestedStatus", status));
    return after;
  }

  private AdSetResponse refresh(
      GoogleAdsAccountContextService.Context context,
      AdCampaign campaign,
      String externalAdGroupId) {
    var row =
        syncService.adGroup(
            context, campaign.getExternalCampaignId(), externalAdGroupId);
    return AdSetResponse.from(
        persistenceService.persistOne(
            campaign.getCompanyId(), campaign.getId(), row));
  }

  private AdCampaign requireCampaign(Long companyId, Long campaignId) {
    AdCampaign campaign =
        campaignRepository
            .findByIdAndCompanyId(campaignId, companyId)
            .orElseThrow(() -> new NotFoundException("Advertising campaign not found"));
    if (campaign.getProvider() != AdProvider.GOOGLE_ADS || !campaign.isAccessible()) {
      throw new BadRequestException("Google campaign is not available for management");
    }
    if ("VIDEO".equalsIgnoreCase(campaign.getObjective())) {
      throw new BadRequestException(
          "Google Ads API exposes Video campaigns as read-only");
    }
    if ("PERFORMANCE_MAX".equalsIgnoreCase(campaign.getObjective())) {
      throw new BadRequestException(
          "Performance Max uses Asset Groups instead of Ad Groups");
    }
    return campaign;
  }

  private AdSet requireAdGroup(Long companyId, Long adSetId) {
    AdSet adGroup =
        adSetRepository
            .findByIdAndCompanyId(adSetId, companyId)
            .orElseThrow(() -> new NotFoundException("Advertising ad group not found"));
    if (adGroup.getProvider() != AdProvider.GOOGLE_ADS || !adGroup.isAccessible()) {
      throw new BadRequestException("Google ad group is not available for management");
    }
    return adGroup;
  }

  private String normalizeStatus(String value) {
    String status = value == null ? "" : value.trim().toUpperCase();
    if ("ACTIVE".equals(status)) status = "ENABLED";
    if (!"ENABLED".equals(status) && !"PAUSED".equals(status)) {
      throw new BadRequestException("Google ad group status must be ENABLED/ACTIVE or PAUSED");
    }
    return status;
  }

  private String idFromResource(String resourceName) {
    if (resourceName == null || resourceName.isBlank()) {
      throw new BadRequestException("Google Ads did not return the created ad group");
    }
    int slash = resourceName.lastIndexOf('/');
    if (slash < 0 || slash == resourceName.length() - 1) {
      throw new BadRequestException("Invalid Google Ads ad group resource name");
    }
    return resourceName.substring(slash + 1);
  }
}
