package com.messaging.ads.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.messaging.ads.dto.AdCampaignResponse;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.CreateGoogleAdvancedCampaignRequest;
import com.messaging.ads.service.AdActivityLogService;
import com.messaging.common.exception.BadRequestException;
import java.math.BigDecimal;
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
public class GoogleAdsAdvancedCampaignService {

  private static final DateTimeFormatter GOOGLE_DATE_TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final GoogleAdsAccountContextService contextService;
  private final GoogleAdsApiClient apiClient;
  private final GoogleAdsCampaignSyncService campaignSyncService;
  private final GoogleAdsCampaignPersistenceService campaignPersistenceService;
  private final AdActivityLogService activityLogService;

  public AdCampaignResponse create(
      Long companyId,
      Long accountId,
      CreateGoogleAdvancedCampaignRequest request,
      Long actorUserId) {
    var context = contextService.requireManaged(companyId, accountId);
    if (context.managerAccount()) {
      throw new BadRequestException(
          "Google Ads manager accounts cannot directly own advertising campaigns");
    }

    validateSchedule(request.startDate(), request.endDate());
    String channel = normalizeChannel(request.channelType());
    String customerId = context.account().getExternalAccountId();
    long budgetMicros =
        GoogleAdsMoney.minorToMicros(
            request.dailyBudgetMinor(), context.account().getCurrency());

    String budgetResource =
        apiClient.customerResourceName(customerId, "campaignBudgets", "-1");
    String campaignResource =
        apiClient.customerResourceName(customerId, "campaigns", "-2");

    List<Map<String, Object>> operations = new ArrayList<>();

    Map<String, Object> budget = new LinkedHashMap<>();
    budget.put("resourceName", budgetResource);
    budget.put(
        "name",
        request.name().trim() + " budget " + System.currentTimeMillis());
    budget.put("amountMicros", budgetMicros);
    budget.put("deliveryMethod", "STANDARD");
    budget.put("explicitlyShared", false);
    operations.add(
        Map.of("campaignBudgetOperation", Map.of("create", budget)));

    Map<String, Object> campaign = new LinkedHashMap<>();
    campaign.put("resourceName", campaignResource);
    campaign.put("name", request.name().trim());
    campaign.put("status", "PAUSED");
    campaign.put("advertisingChannelType", channel);
    campaign.put("campaignBudget", budgetResource);
    campaign.put(
        "containsEuPoliticalAdvertising",
        Boolean.TRUE.equals(request.containsEuPoliticalAdvertising())
            ? "CONTAINS_EU_POLITICAL_ADVERTISING"
            : "DOES_NOT_CONTAIN_EU_POLITICAL_ADVERTISING");
    if (request.startDate() != null) {
      campaign.put("startDateTime", startDateTime(request.startDate()));
    }
    if (request.endDate() != null) {
      campaign.put("endDateTime", endDateTime(request.endDate()));
    }

    switch (channel) {
      case "DISPLAY" -> configureDisplay(
          campaign, request, context.account().getCurrency());
      case "DEMAND_GEN" -> configureDemandGen(
          campaign, request, context.account().getCurrency());
      case "SHOPPING" -> configureShopping(
          campaign, request, operations, customerId, campaignResource, context);
      default -> throw new BadRequestException(
          "Unsupported advanced Google campaign type");
    }

    operations.add(
        1,
        Map.of("campaignOperation", Map.of("create", campaign)));

    JsonNode response =
        apiClient.mutateAll(
            context.accessToken(),
            customerId,
            context.loginCustomerId(),
            operations);

    String resourceName =
        apiClient.firstBulkResourceName(response, "campaignResult");
    String externalCampaignId = suffix(resourceName, "campaign");

    JsonNode row = campaignSyncService.campaign(context, externalCampaignId);
    AdCampaignResponse created =
        AdCampaignResponse.from(
            campaignPersistenceService.persistOne(
                companyId, accountId, row));

    activityLogService.record(
        companyId,
        accountId,
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_ADVANCED_CAMPAIGN_CREATED",
        "CAMPAIGN",
        externalCampaignId,
        null,
        created,
        Map.of(
            "channelType", channel,
            "initialStatus", "PAUSED"));

    return created;
  }

  private void configureDisplay(
      Map<String, Object> campaign,
      CreateGoogleAdvancedCampaignRequest request,
      String currency) {
    String strategy = normalize(
        request.bidStrategy(), "MAXIMIZE_CONVERSIONS");
    switch (strategy) {
      case "MANUAL_CPC" ->
          campaign.put("manualCpc", Map.of("enhancedCpcEnabled", false));
      case "MAXIMIZE_CONVERSIONS" ->
          campaign.put("maximizeConversions", Map.of());
      case "TARGET_CPA" -> {
        if (request.targetCpaMinor() == null || request.targetCpaMinor() <= 0) {
          throw new BadRequestException(
              "Display Target CPA bidding requires targetCpaMinor");
        }
        campaign.put(
            "targetCpa",
            Map.of(
                "targetCpaMicros",
                GoogleAdsMoney.minorToMicros(
                    request.targetCpaMinor(), currency)));
      }
      default -> throw new BadRequestException(
          "Display bid strategy must be MANUAL_CPC, MAXIMIZE_CONVERSIONS, or TARGET_CPA");
    }
  }

  private void configureDemandGen(
      Map<String, Object> campaign,
      CreateGoogleAdvancedCampaignRequest request,
      String currency) {
    String strategy = normalize(
        request.bidStrategy(), "MAXIMIZE_CONVERSIONS");
    switch (strategy) {
      case "MAXIMIZE_CLICKS" -> campaign.put("maximizeClicks", Map.of());
      case "MAXIMIZE_CONVERSIONS" -> campaign.put("maximizeConversions", Map.of());
      case "TARGET_CPA" -> {
        if (request.targetCpaMinor() == null || request.targetCpaMinor() <= 0) {
          throw new BadRequestException(
              "Demand Gen Target CPA bidding requires targetCpaMinor");
        }
        campaign.put(
            "targetCpa",
            Map.of(
                "targetCpaMicros",
                GoogleAdsMoney.minorToMicros(
                    request.targetCpaMinor(), currency)));
      }
      case "TARGET_ROAS" -> {
        BigDecimal roas = request.targetRoas();
        if (roas == null || roas.compareTo(BigDecimal.ZERO) <= 0) {
          throw new BadRequestException(
              "Demand Gen Target ROAS bidding requires a positive targetRoas");
        }
        campaign.put("targetRoas", Map.of("targetRoas", roas.doubleValue()));
      }
      default -> throw new BadRequestException(
          "Demand Gen bid strategy must be MAXIMIZE_CLICKS, MAXIMIZE_CONVERSIONS, TARGET_CPA, or TARGET_ROAS");
    }
  }

  private void configureShopping(
      Map<String, Object> campaign,
      CreateGoogleAdvancedCampaignRequest request,
      List<Map<String, Object>> operations,
      String customerId,
      String campaignResource,
      GoogleAdsAccountContextService.Context context) {
    String merchantId = numeric(request.merchantId(), "Merchant Center ID");
    int priority =
        request.campaignPriority() == null ? 0 : request.campaignPriority();
    if (priority < 0 || priority > 2) {
      throw new BadRequestException(
          "Shopping campaign priority must be between 0 and 2");
    }

    campaign.put(
        "shoppingSetting",
        Map.of(
            "merchantId", Long.parseLong(merchantId),
            "campaignPriority", priority,
            "enableLocal", Boolean.TRUE.equals(request.enableLocal())));
    campaign.put(
        "manualCpc",
        Map.of("enhancedCpcEnabled", false));

    String adGroupResource =
        apiClient.customerResourceName(customerId, "adGroups", "-3");

    Map<String, Object> adGroup = new LinkedHashMap<>();
    adGroup.put("resourceName", adGroupResource);
    adGroup.put("name", truncate(request.name().trim() + " products", 255));
    adGroup.put("campaign", campaignResource);
    adGroup.put("type", "SHOPPING_PRODUCT_ADS");
    adGroup.put("status", "PAUSED");
    adGroup.put(
        "cpcBidMicros",
        GoogleAdsMoney.minorToMicros(
            Math.max(1L, request.dailyBudgetMinor() / 20L),
            context.account().getCurrency()));
    operations.add(
        Map.of("adGroupOperation", Map.of("create", adGroup)));

    Map<String, Object> ad = new LinkedHashMap<>();
    ad.put("shoppingProductAd", Map.of());

    Map<String, Object> adGroupAd = new LinkedHashMap<>();
    adGroupAd.put("adGroup", adGroupResource);
    adGroupAd.put("status", "PAUSED");
    adGroupAd.put("ad", ad);
    operations.add(
        Map.of("adGroupAdOperation", Map.of("create", adGroupAd)));

    Map<String, Object> listingCriterion = new LinkedHashMap<>();
    listingCriterion.put("adGroup", adGroupResource);
    listingCriterion.put("status", "PAUSED");
    listingCriterion.put("listingGroup", Map.of("type", "UNIT"));
    listingCriterion.put(
        "cpcBidMicros",
        GoogleAdsMoney.minorToMicros(
            Math.max(1L, request.dailyBudgetMinor() / 20L),
            context.account().getCurrency()));
    operations.add(
        Map.of(
            "adGroupCriterionOperation",
            Map.of("create", listingCriterion)));
  }

  private String normalizeChannel(String raw) {
    String channel = normalize(raw, "");
    if (!List.of("DISPLAY", "DEMAND_GEN", "SHOPPING").contains(channel)) {
      throw new BadRequestException(
          "Google advanced campaign type must be DISPLAY, DEMAND_GEN, or SHOPPING");
    }
    return channel;
  }

  private String normalize(String value, String fallback) {
    return value == null || value.isBlank()
        ? fallback
        : value.trim().toUpperCase();
  }

  private String numeric(String value, String label) {
    if (value == null || !value.trim().matches("\\d+")) {
      throw new BadRequestException(label + " must contain digits only");
    }
    return value.trim();
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

  private String truncate(String value, int max) {
    return value.length() <= max ? value : value.substring(0, max);
  }
}
