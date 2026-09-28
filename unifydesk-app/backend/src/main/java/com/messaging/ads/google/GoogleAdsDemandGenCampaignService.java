package com.messaging.ads.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.messaging.ads.dto.AdCampaignResponse;
import com.messaging.ads.dto.AdSetResponse;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.CreateGoogleDemandGenCampaignRequest;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.ads.service.AdActivityLogService;
import com.messaging.common.exception.BadRequestException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GoogleAdsDemandGenCampaignService {

  private static final DateTimeFormatter GOOGLE_DATE_TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final GoogleAdsAccountContextService contextService;
  private final GoogleAdsApiClient apiClient;
  private final GoogleAdsCampaignSyncService campaignSyncService;
  private final GoogleAdsCampaignPersistenceService campaignPersistenceService;
  private final GoogleAdsAdGroupSyncService adGroupSyncService;
  private final GoogleAdsAdGroupPersistenceService adGroupPersistenceService;
  private final AdCampaignRepository campaignRepository;
  private final AdActivityLogService activityLogService;

  public Result create(
      Long companyId,
      Long accountId,
      CreateGoogleDemandGenCampaignRequest request,
      Long actorUserId) {
    var context = contextService.requireManaged(companyId, accountId);
    if (context.managerAccount()) {
      throw new BadRequestException(
          "Google Ads manager accounts cannot directly own Demand Gen campaigns");
    }
    validateSchedule(request.startDate(), request.endDate());

    String customerId = context.account().getExternalAccountId();
    long budgetMicros =
        GoogleAdsMoney.minorToMicros(
            request.dailyBudgetMinor(), context.account().getCurrency());

    String budgetResource = apiClient.customerResourceName(customerId, "campaignBudgets", "-1");
    String campaignResource = apiClient.customerResourceName(customerId, "campaigns", "-2");
    String adGroupResource = apiClient.customerResourceName(customerId, "adGroups", "-3");

    List<Map<String, Object>> operations = new ArrayList<>();

    Map<String, Object> budget = new LinkedHashMap<>();
    budget.put("resourceName", budgetResource);
    budget.put("name", request.name().trim() + " budget " + System.currentTimeMillis());
    budget.put("amountMicros", budgetMicros);
    budget.put("deliveryMethod", "STANDARD");
    budget.put("explicitlyShared", false);
    operations.add(Map.of("campaignBudgetOperation", Map.of("create", budget)));

    Map<String, Object> campaign = new LinkedHashMap<>();
    campaign.put("resourceName", campaignResource);
    campaign.put("name", request.name().trim());
    campaign.put("status", "PAUSED");
    campaign.put("advertisingChannelType", "DEMAND_GEN");
    campaign.put("campaignBudget", budgetResource);
    applyBidStrategy(campaign, request.bidStrategy());
    campaign.put(
        "containsEuPoliticalAdvertising",
        request.containsEuPoliticalAdvertising()
            ? "CONTAINS_EU_POLITICAL_ADVERTISING"
            : "DOES_NOT_CONTAIN_EU_POLITICAL_ADVERTISING");
    if (request.startDate() != null) {
      campaign.put("startDateTime", startDateTime(request.startDate()));
    }
    if (request.endDate() != null) {
      campaign.put("endDateTime", endDateTime(request.endDate()));
    }
    operations.add(Map.of("campaignOperation", Map.of("create", campaign)));

    Map<String, Object> adGroup = new LinkedHashMap<>();
    adGroup.put("resourceName", adGroupResource);
    adGroup.put("campaign", campaignResource);
    adGroup.put("name", request.adGroupName().trim());
    adGroup.put("status", "PAUSED");
    adGroup.put("audienceSetting", Map.of("useAudienceGrouped", true));
    adGroup.put(
        "demandGenAdGroupSettings",
        Map.of("channelControls", Map.of("channelStrategy", "ALL_CHANNELS")));
    operations.add(Map.of("adGroupOperation", Map.of("create", adGroup)));

    JsonNode response =
        apiClient.mutateAll(
            context.accessToken(),
            customerId,
            context.loginCustomerId(),
            operations);

    String campaignResourceName =
        apiClient.firstBulkResourceName(response, "campaignResult");
    String adGroupResourceName =
        apiClient.firstBulkResourceName(response, "adGroupResult");
    String externalCampaignId = suffix(campaignResourceName, "campaign");
    String externalAdGroupId = suffix(adGroupResourceName, "ad group");

    JsonNode campaignRow = campaignSyncService.campaign(context, externalCampaignId);
    AdCampaignResponse campaignResponse =
        AdCampaignResponse.from(
            campaignPersistenceService.persistOne(
                companyId, accountId, campaignRow));

    AdCampaign storedCampaign =
        campaignRepository
            .findByIdAndCompanyId(Long.valueOf(campaignResponse.id()), companyId)
            .orElseThrow(
                () -> new BadRequestException(
                    "Demand Gen campaign was created but could not be persisted"));

    JsonNode adGroupRow =
        adGroupSyncService.adGroup(
            context, externalCampaignId, externalAdGroupId);
    AdSetResponse adGroupResponse =
        AdSetResponse.from(
            adGroupPersistenceService.persistOne(
                companyId, storedCampaign.getId(), adGroupRow));

    activityLogService.record(
        companyId,
        accountId,
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_DEMAND_GEN_CREATED",
        "CAMPAIGN",
        externalCampaignId,
        null,
        campaignResponse,
        Map.of(
            "channel", "DEMAND_GEN",
            "initialStatus", "PAUSED",
            "adGroupId", externalAdGroupId));

    return new Result(campaignResponse, adGroupResponse);
  }

  private void applyBidStrategy(
      Map<String, Object> campaign, String requestedStrategy) {
    String strategy =
        requestedStrategy == null || requestedStrategy.isBlank()
            ? "MAXIMIZE_CONVERSIONS"
            : requestedStrategy.trim().toUpperCase();
    switch (strategy) {
      case "MAXIMIZE_CLICKS" -> campaign.put("targetSpend", Map.of());
      case "MAXIMIZE_CONVERSIONS" -> campaign.put("maximizeConversions", Map.of());
      case "MAXIMIZE_CONVERSION_VALUE" ->
          campaign.put("maximizeConversionValue", Map.of());
      default ->
          throw new BadRequestException(
              "Demand Gen bid strategy must be MAXIMIZE_CLICKS, MAXIMIZE_CONVERSIONS, or MAXIMIZE_CONVERSION_VALUE");
    }
  }

  private void validateSchedule(LocalDate start, LocalDate end) {
    if (start != null && end != null && end.isBefore(start)) {
      throw new BadRequestException(
          "Demand Gen campaign end date must be on or after the start date");
    }
  }

  private String startDateTime(LocalDate date) {
    return LocalDateTime.of(date, LocalTime.MIDNIGHT).format(GOOGLE_DATE_TIME);
  }

  private String endDateTime(LocalDate date) {
    return LocalDateTime.of(date, LocalTime.of(23, 59, 59)).format(GOOGLE_DATE_TIME);
  }

  private String suffix(String resourceName, String label) {
    if (resourceName == null || resourceName.isBlank()) {
      throw new BadRequestException(
          "Google Ads did not return the created " + label + " resource name");
    }
    int slash = resourceName.lastIndexOf('/');
    if (slash < 0 || slash == resourceName.length() - 1) {
      throw new BadRequestException(
          "Invalid Google Ads " + label + " resource name");
    }
    return resourceName.substring(slash + 1);
  }

  public record Result(
      AdCampaignResponse campaign,
      AdSetResponse adGroup) {}
}
