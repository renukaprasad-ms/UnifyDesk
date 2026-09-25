package com.messaging.ads.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.messaging.ads.dto.AdCampaignResponse;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.CreateGoogleCampaignRequest;
import com.messaging.ads.google.dto.GoogleSearchCampaignSettings;
import com.messaging.ads.google.dto.UpdateGoogleCampaignRequest;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.ads.service.AdActivityLogService;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
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
public class GoogleAdsCampaignMutationService {

  private static final DateTimeFormatter GOOGLE_DATE_TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final GoogleAdsAccountContextService contextService;
  private final GoogleAdsApiClient apiClient;
  private final GoogleAdsCampaignSyncService syncService;
  private final GoogleAdsCampaignPersistenceService persistenceService;
  private final AdCampaignRepository campaignRepository;
  private final AdActivityLogService activityLogService;

  public AdCampaignResponse create(
      Long companyId,
      Long accountId,
      CreateGoogleCampaignRequest request,
      Long actorUserId) {
    return create(
        companyId,
        accountId,
        request,
        GoogleSearchCampaignSettings.defaults(),
        actorUserId);
  }

  public AdCampaignResponse create(
      Long companyId,
      Long accountId,
      CreateGoogleCampaignRequest request,
      GoogleSearchCampaignSettings settings,
      Long actorUserId) {
    var context = contextService.requireManaged(companyId, accountId);
    if (context.managerAccount()) {
      throw new BadRequestException(
          "Google Ads manager accounts cannot directly own advertising campaigns");
    }
    if (request.dailyBudgetMinor() == null) {
      throw new BadRequestException("Google Search campaigns require a daily budget");
    }
    validateSchedule(request.startDate(), request.endDate());

    String customerId = context.account().getExternalAccountId();
    long amountMicros =
        GoogleAdsMoney.minorToMicros(
            request.dailyBudgetMinor(), context.account().getCurrency());

    Map<String, Object> budgetCreate = new LinkedHashMap<>();
    budgetCreate.put(
        "name",
        request.name().trim() + " budget " + System.currentTimeMillis());
    budgetCreate.put("amountMicros", amountMicros);
    budgetCreate.put("deliveryMethod", "STANDARD");
    budgetCreate.put("explicitlyShared", false);

    JsonNode budgetResponse =
        apiClient.mutate(
            context.accessToken(),
            customerId,
            context.loginCustomerId(),
            "campaignBudgets",
            List.of(Map.of("create", budgetCreate)));
    String budgetResource = apiClient.firstMutatedResourceName(budgetResponse);
    if (budgetResource == null) {
      throw new BadRequestException(
          "Google Ads created the campaign budget without returning its resource name");
    }

    Map<String, Object> campaign = new LinkedHashMap<>();
    campaign.put("name", request.name().trim());
    campaign.put("status", "PAUSED");
    campaign.put("advertisingChannelType", "SEARCH");
    campaign.put("campaignBudget", budgetResource);
    GoogleSearchCampaignSettings effectiveSettings =
        settings == null ? GoogleSearchCampaignSettings.defaults() : settings;
    campaign.put(
        "networkSettings",
        Map.of(
            "targetGoogleSearch", true,
            "targetSearchNetwork", effectiveSettings.targetSearchNetwork(),
            "targetContentNetwork", effectiveSettings.targetContentNetwork(),
            "targetPartnerSearchNetwork", false));
    campaign.put(
        "geoTargetTypeSetting",
        Map.of(
            "positiveGeoTargetType", effectiveSettings.positiveGeoTargetType(),
            "negativeGeoTargetType", "PRESENCE"));
    if (effectiveSettings.aiMaxEnabled()) {
      campaign.put("aiMaxSetting", Map.of("enableAiMax", true));
      List<Map<String, Object>> automationSettings = new ArrayList<>();
      automationSettings.add(
          Map.of(
              "assetAutomationType", "TEXT_ASSET_AUTOMATION",
              "assetAutomationStatus",
              effectiveSettings.textCustomizationEnabled() ? "OPTED_IN" : "OPTED_OUT"));
      automationSettings.add(
          Map.of(
              "assetAutomationType", "FINAL_URL_EXPANSION_TEXT_ASSET_AUTOMATION",
              "assetAutomationStatus",
              effectiveSettings.finalUrlExpansionEnabled() ? "OPTED_IN" : "OPTED_OUT"));
      campaign.put("assetAutomationSettings", automationSettings);
    }
    applyBidStrategy(campaign, request.bidStrategy());
    campaign.put(
        "containsEuPoliticalAdvertising",
        politicalAdvertisingStatus(request.containsEuPoliticalAdvertising()));
    if (request.startDate() != null) {
      campaign.put("startDateTime", startDateTime(request.startDate()));
    }
    if (request.endDate() != null) {
      campaign.put("endDateTime", endDateTime(request.endDate()));
    }

    JsonNode campaignResponse =
        apiClient.mutate(
            context.accessToken(),
            customerId,
            context.loginCustomerId(),
            "campaigns",
            List.of(Map.of("create", campaign)));
    String resourceName = apiClient.firstMutatedResourceName(campaignResponse);
    String externalCampaignId = idFromResource(resourceName, "campaign");

    AdCampaignResponse after = refresh(context, externalCampaignId);
    activityLogService.record(
        companyId,
        accountId,
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_CAMPAIGN_CREATED",
        "CAMPAIGN",
        externalCampaignId,
        null,
        after,
        Map.of("initialStatus", "PAUSED"));

    return after;
  }

  public AdCampaignResponse update(
      Long companyId,
      Long campaignId,
      UpdateGoogleCampaignRequest request,
      Long actorUserId) {
    AdCampaign stored = requireCampaign(companyId, campaignId);
    var context = contextService.requireManaged(companyId, stored.getAdAccountId());
    AdCampaignResponse before = AdCampaignResponse.from(stored);

    validateSchedule(request.startDate(), request.endDate());

    JsonNode currentRow = syncService.campaign(context, stored.getExternalCampaignId());
    String customerId = context.account().getExternalAccountId();

    if (request.dailyBudgetMinor() != null) {
      String budgetResource = currentRow.path("campaignBudget").path("resourceName").asText(null);
      if (budgetResource == null || budgetResource.isBlank()) {
        budgetResource = currentRow.path("campaign").path("campaignBudget").asText(null);
      }
      if (budgetResource == null || budgetResource.isBlank()) {
        throw new BadRequestException("Google campaign budget resource is unavailable");
      }
      long amountMicros =
          GoogleAdsMoney.minorToMicros(
              request.dailyBudgetMinor(), context.account().getCurrency());
      Map<String, Object> budgetUpdate = new LinkedHashMap<>();
      budgetUpdate.put("resourceName", budgetResource);
      budgetUpdate.put("amountMicros", amountMicros);
      apiClient.mutate(
          context.accessToken(),
          customerId,
          context.loginCustomerId(),
          "campaignBudgets",
          List.of(
              Map.of(
                  "update", budgetUpdate,
                  "updateMask", "amount_micros")));
    }

    Map<String, Object> updates = new LinkedHashMap<>();
    List<String> masks = new ArrayList<>();
    updates.put(
        "resourceName",
        apiClient.customerResourceName(
            customerId, "campaigns", stored.getExternalCampaignId()));

    if (request.name() != null) {
      if (request.name().isBlank()) {
        throw new BadRequestException("Campaign name cannot be blank");
      }
      updates.put("name", request.name().trim());
      masks.add("name");
    }
    if (request.startDate() != null) {
      updates.put("startDateTime", startDateTime(request.startDate()));
      masks.add("start_date_time");
    }
    if (request.endDate() != null) {
      updates.put("endDateTime", endDateTime(request.endDate()));
      masks.add("end_date_time");
    }
    if (request.bidStrategy() != null) {
      String strategyMask = applyBidStrategy(updates, request.bidStrategy());
      masks.add(strategyMask);
    }

    if (!masks.isEmpty()) {
      apiClient.mutate(
          context.accessToken(),
          customerId,
          context.loginCustomerId(),
          "campaigns",
          List.of(
              Map.of(
                  "update", updates,
                  "updateMask", String.join(",", masks))));
    } else if (request.dailyBudgetMinor() == null) {
      throw new BadRequestException("No Google campaign changes were supplied");
    }

    AdCampaignResponse after = refresh(context, stored.getExternalCampaignId());
    activityLogService.record(
        companyId,
        stored.getAdAccountId(),
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_CAMPAIGN_UPDATED",
        "CAMPAIGN",
        stored.getExternalCampaignId(),
        before,
        after,
        null);
    return after;
  }

  public AdCampaignResponse setStatus(
      Long companyId,
      Long campaignId,
      String requestedStatus,
      Long actorUserId) {
    AdCampaign stored = requireCampaign(companyId, campaignId);
    var context = contextService.requireManaged(companyId, stored.getAdAccountId());
    String status = normalizeStatus(requestedStatus);
    AdCampaignResponse before = AdCampaignResponse.from(stored);

    Map<String, Object> update =
        Map.of(
            "resourceName",
            apiClient.customerResourceName(
                context.account().getExternalAccountId(),
                "campaigns",
                stored.getExternalCampaignId()),
            "status",
            status);
    apiClient.mutate(
        context.accessToken(),
        context.account().getExternalAccountId(),
        context.loginCustomerId(),
        "campaigns",
        List.of(Map.of("update", update, "updateMask", "status")));

    AdCampaignResponse after = refresh(context, stored.getExternalCampaignId());
    activityLogService.record(
        companyId,
        stored.getAdAccountId(),
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_CAMPAIGN_STATUS_UPDATED",
        "CAMPAIGN",
        stored.getExternalCampaignId(),
        before,
        after,
        Map.of("requestedStatus", status));
    return after;
  }

  private AdCampaignResponse refresh(
      GoogleAdsAccountContextService.Context context,
      String externalCampaignId) {
    JsonNode row = syncService.campaign(context, externalCampaignId);
    return AdCampaignResponse.from(
        persistenceService.persistOne(
            context.account().getCompanyId(),
            context.account().getId(),
            row));
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
          "Google Ads API exposes Video campaigns as read-only. Manage Video campaign settings in Google Ads or use Demand Gen for API-managed video delivery");
    }
    return campaign;
  }

  private String applyBidStrategy(Map<String, Object> target, String requestedStrategy) {
    String strategy =
        requestedStrategy == null || requestedStrategy.isBlank()
            ? "MANUAL_CPC"
            : requestedStrategy.trim().toUpperCase();
    return switch (strategy) {
      case "MANUAL_CPC" -> {
        target.put("manualCpc", Map.of("enhancedCpcEnabled", false));
        yield "manual_cpc";
      }
      case "MAXIMIZE_CLICKS" -> {
        target.put("targetSpend", Map.of());
        yield "target_spend";
      }
      case "MAXIMIZE_CONVERSIONS" -> {
        target.put("maximizeConversions", Map.of());
        yield "maximize_conversions";
      }
      case "MAXIMIZE_CONVERSION_VALUE" -> {
        target.put("maximizeConversionValue", Map.of());
        yield "maximize_conversion_value";
      }
      default -> throw new BadRequestException(
          "Unsupported Google campaign bid strategy: " + strategy);
    };
  }

  private String normalizeStatus(String requestedStatus) {
    String status =
        requestedStatus == null ? "" : requestedStatus.trim().toUpperCase();
    if ("ACTIVE".equals(status)) status = "ENABLED";
    if (!"ENABLED".equals(status) && !"PAUSED".equals(status)) {
      throw new BadRequestException(
          "Google campaign status must be ENABLED/ACTIVE or PAUSED");
    }
    return status;
  }

  private String politicalAdvertisingStatus(Boolean containsEuPoliticalAdvertising) {
    if (containsEuPoliticalAdvertising == null) {
      throw new BadRequestException(
          "Choose whether this campaign contains EU political advertising");
    }
    return containsEuPoliticalAdvertising
        ? "CONTAINS_EU_POLITICAL_ADVERTISING"
        : "DOES_NOT_CONTAIN_EU_POLITICAL_ADVERTISING";
  }

  private String startDateTime(LocalDate date) {
    return LocalDateTime.of(date, LocalTime.MIDNIGHT).format(GOOGLE_DATE_TIME);
  }

  private String endDateTime(LocalDate date) {
    return LocalDateTime.of(date, LocalTime.of(23, 59, 59)).format(GOOGLE_DATE_TIME);
  }

  private void validateSchedule(LocalDate start, LocalDate end) {
    if (start != null && end != null && end.isBefore(start)) {
      throw new BadRequestException(
          "Google campaign end date must be on or after the start date");
    }
  }

  private String idFromResource(String resourceName, String label) {
    if (resourceName == null || resourceName.isBlank()) {
      throw new BadRequestException(
          "Google Ads did not return the created " + label + " resource name");
    }
    int slash = resourceName.lastIndexOf('/');
    if (slash < 0 || slash == resourceName.length() - 1) {
      throw new BadRequestException("Invalid Google Ads " + label + " resource name");
    }
    return resourceName.substring(slash + 1);
  }
}
