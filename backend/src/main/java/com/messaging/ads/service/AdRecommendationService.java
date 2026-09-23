package com.messaging.ads.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.dto.AdRecommendationsResponse;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdAccountBilling;
import com.messaging.ads.entity.AdAccountHealth;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.entity.AdConversion;
import com.messaging.ads.entity.AdInsightDaily;
import com.messaging.ads.enums.AdAccountResourceType;
import com.messaging.ads.enums.AdAccountStatus;
import com.messaging.ads.enums.AdEntityStatus;
import com.messaging.ads.enums.AdInsightGranularity;
import com.messaging.ads.enums.AdInsightLevel;
import com.messaging.ads.enums.AdRecommendationCategory;
import com.messaging.ads.enums.AdRecommendationSeverity;
import com.messaging.ads.enums.AdRecommendationSource;
import com.messaging.ads.repository.AdAccountBillingRepository;
import com.messaging.ads.repository.AdAccountHealthRepository;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdAccountResourceRepository;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.ads.repository.AdConversionRepository;
import com.messaging.ads.repository.AdInsightDailyRepository;
import com.messaging.common.exception.NotFoundException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdRecommendationService {

  private static final Set<String> CONVERSION_OBJECTIVES =
      Set.of("OUTCOME_SALES", "OUTCOME_LEADS", "OUTCOME_APP_PROMOTION");

  private final AdAccountRepository accountRepository;
  private final AdAccountHealthRepository healthRepository;
  private final AdAccountBillingRepository billingRepository;
  private final AdAccountResourceRepository resourceRepository;
  private final AdConversionRepository conversionRepository;
  private final AdCampaignRepository campaignRepository;
  private final AdInsightDailyRepository insightRepository;
  private final ObjectMapper objectMapper;

  @Transactional(readOnly = true)
  public AdRecommendationsResponse recommendations(Long companyId, Long accountId) {
    AdAccount account =
        accountRepository
            .findByIdAndCompanyId(accountId, companyId)
            .orElseThrow(() -> new NotFoundException("Ad account not found"));

    AdAccountHealth health =
        healthRepository.findByCompanyIdAndAdAccountId(companyId, accountId).orElse(null);
    AdAccountBilling billing =
        billingRepository.findByCompanyIdAndAdAccountId(companyId, accountId).orElse(null);

    var resources =
        resourceRepository.findByCompanyIdAndAdAccountId(companyId, accountId);
    List<AdConversion> conversions =
        conversionRepository.findByCompanyIdAndAdAccountIdOrderByLastFiredAtDesc(
            companyId, accountId);
    List<AdCampaign> campaigns =
        campaignRepository.findByCompanyIdAndAdAccountId(companyId, accountId);

    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    LocalDate from = today.minusDays(6);
    List<AdInsightDaily> recentCampaignInsights =
        insightRepository
            .findByCompanyIdAndAdAccountIdAndInsightLevelAndGranularityAndInsightDateBetweenOrderByInsightDateAsc(
                companyId,
                accountId,
                AdInsightLevel.CAMPAIGN,
                AdInsightGranularity.DAILY,
                from,
                today);

    List<AdRecommendationsResponse.Item> items = new ArrayList<>();

    addAccountState(account, items);
    addHealthDiagnostics(account, health, items);
    addResourceDiagnostics(account, resources, items);
    addBillingDiagnostics(account, billing, items);
    addCampaignStatusDiagnostics(campaigns, items);
    addConversionDiagnostics(account, conversions, campaigns, recentCampaignInsights, items);
    addPerformanceDiagnostics(campaigns, recentCampaignInsights, items);

    items.sort(
        Comparator.comparingInt(
                (AdRecommendationsResponse.Item item) ->
                    severityRank(item.severity()))
            .thenComparing(item -> item.category().name())
            .thenComparing(AdRecommendationsResponse.Item::title));

    Instant analyticsLastSyncAt =
        recentCampaignInsights.stream()
            .map(AdInsightDaily::getLastSyncAt)
            .filter(value -> value != null)
            .max(Instant::compareTo)
            .orElse(null);

    return new AdRecommendationsResponse(
        account.getId().toString(),
        health == null ? null : health.getOpportunityScore(),
        health == null ? null : health.getOpportunityScoreWeight(),
        count(items, AdRecommendationSeverity.CRITICAL),
        count(items, AdRecommendationSeverity.WARNING),
        count(items, AdRecommendationSeverity.OPPORTUNITY),
        count(items, AdRecommendationSeverity.INFO),
        Instant.now(),
        health == null ? null : health.getLastSyncAt(),
        billing == null ? null : billing.getLastSyncAt(),
        analyticsLastSyncAt,
        List.copyOf(items));
  }

  private void addAccountState(
      AdAccount account, List<AdRecommendationsResponse.Item> items) {
    if (account.getStatus() == AdAccountStatus.ACTIVE) return;

    items.add(
        item(
            "ACCOUNT_STATUS_" + account.getStatus().name(),
            account.getStatus() == AdAccountStatus.CLOSED
                ? AdRecommendationSeverity.CRITICAL
                : AdRecommendationSeverity.WARNING,
            AdRecommendationCategory.ACCOUNT,
            AdRecommendationSource.META_PROVIDER,
            "Meta ad account is " + account.getStatus().name().toLowerCase(),
            "The selected ad account is not currently reported as active. Campaign delivery and management can be restricted until the provider account state is resolved.",
            "AD_ACCOUNT",
            account.getId().toString(),
            "Review account settings",
            "/ad-center/settings",
            evidence("accountStatus", account.getStatus().name())));
  }

  private void addHealthDiagnostics(
      AdAccount account,
      AdAccountHealth health,
      List<AdRecommendationsResponse.Item> items) {
    if (health == null) {
      items.add(
          item(
              "HEALTH_NOT_SYNCED",
              AdRecommendationSeverity.INFO,
              AdRecommendationCategory.ACCOUNT,
              AdRecommendationSource.REACHLYO_DIAGNOSTIC,
              "Account health has not been synchronized",
              "Sync Meta account health to load delivery checks, account capabilities, authorization state, and access-expiry information.",
              "AD_ACCOUNT",
              account.getId().toString(),
              "Sync account health",
              "/ad-center/settings",
              Map.of()));
      return;
    }

    addFailedDeliveryChecks(account, health, items);

    if (health.getDisableReason() != null && health.getDisableReason() > 0) {
      items.add(
          item(
              "META_DISABLE_REASON_" + health.getDisableReason(),
              AdRecommendationSeverity.CRITICAL,
              AdRecommendationCategory.ACCOUNT,
              AdRecommendationSource.META_PROVIDER,
              "Meta reports an account disable reason",
              "Meta returned disable reason code "
                  + health.getDisableReason()
                  + " for this ad account. Review provider diagnostics before attempting further delivery changes.",
              "AD_ACCOUNT",
              account.getId().toString(),
              "Open diagnostics",
              "/ad-center/settings",
              evidence("disableReason", health.getDisableReason())));
    }

    if (Boolean.FALSE.equals(health.getPageAuthorized())) {
      items.add(
          item(
              "PAGE_NOT_AUTHORIZED",
              AdRecommendationSeverity.WARNING,
              AdRecommendationCategory.SETUP,
              AdRecommendationSource.META_PROVIDER,
              "Facebook Page authorization is incomplete",
              "Meta reports that the ad account does not currently have an authorized Facebook Page. Some identity and creative flows can be unavailable until authorization is restored.",
              "AD_ACCOUNT",
              account.getId().toString(),
              "Review connected resources",
              "/ad-center/settings",
              Map.of()));
    }

    if (Boolean.FALSE.equals(health.getOffsitePixelsTosAccepted())) {
      items.add(
          item(
              "PIXEL_TERMS_NOT_ACCEPTED",
              AdRecommendationSeverity.WARNING,
              AdRecommendationCategory.TRACKING,
              AdRecommendationSource.META_PROVIDER,
              "Meta Pixel terms are not accepted",
              "Meta reports that offsite Pixel terms are not accepted for this account. Website conversion measurement may be restricted.",
              "AD_ACCOUNT",
              account.getId().toString(),
              "Review tracking setup",
              "/ad-center/settings",
              Map.of()));
    }

    Instant expiry = health.getAccessExpiresAt();
    if (expiry != null) {
      Duration remaining = Duration.between(Instant.now(), expiry);
      if (remaining.isNegative() || remaining.isZero()) {
        items.add(
            item(
                "ACCESS_EXPIRED",
                AdRecommendationSeverity.CRITICAL,
                AdRecommendationCategory.ACCOUNT,
                AdRecommendationSource.META_PROVIDER,
                "Meta account access has expired",
                "The provider-reported user access expiry has passed. Reconnect Meta Ads before relying on campaign mutations or synchronization.",
                "AD_ACCOUNT",
                account.getId().toString(),
                "Reconnect Meta Ads",
                "/ad-center",
                evidence("accessExpiresAt", expiry.toString())));
      } else if (remaining.compareTo(Duration.ofDays(7)) <= 0) {
        items.add(
            item(
                "ACCESS_EXPIRING",
                AdRecommendationSeverity.WARNING,
                AdRecommendationCategory.ACCOUNT,
                AdRecommendationSource.META_PROVIDER,
                "Meta account access expires soon",
                "The provider-reported access grant expires within seven days. Reconnect before it expires to avoid synchronization and management interruptions.",
                "AD_ACCOUNT",
                account.getId().toString(),
                "Review connection",
                "/ad-center/settings",
                evidence(
                    "accessExpiresAt",
                    expiry.toString(),
                    "hoursRemaining",
                    Math.max(0L, remaining.toHours()))));
      }
    }
  }

  private void addFailedDeliveryChecks(
      AdAccount account,
      AdAccountHealth health,
      List<AdRecommendationsResponse.Item> items) {
    if (health.getFailedDeliveryChecksJson() == null
        || health.getFailedDeliveryChecksJson().isBlank()) {
      return;
    }

    try {
      JsonNode checks = objectMapper.readTree(health.getFailedDeliveryChecksJson());
      if (checks == null || !checks.isArray()) return;

      int index = 0;
      for (JsonNode check : checks) {
        String checkName = text(check, "check_name");
        String summary = text(check, "summary");
        String description = text(check, "description");
        String stableName =
            checkName == null || checkName.isBlank()
                ? "CHECK_" + index
                : checkName.trim();

        items.add(
            item(
                "META_DELIVERY_" + stableName,
                AdRecommendationSeverity.CRITICAL,
                AdRecommendationCategory.DELIVERY,
                AdRecommendationSource.META_PROVIDER,
                first(summary, checkName, "Meta delivery check requires attention"),
                first(
                    description,
                    "Meta reported a failed delivery check for this ad account."),
                "AD_ACCOUNT",
                account.getId().toString(),
                "Open account diagnostics",
                "/ad-center/settings",
                evidence("checkName", stableName)));
        index++;
      }
    } catch (Exception ignored) {
      // Preserve the health snapshot even when a provider payload is malformed.
    }
  }

  private void addResourceDiagnostics(
      AdAccount account,
      List<com.messaging.ads.entity.AdAccountResource> resources,
      List<AdRecommendationsResponse.Item> items) {
    if (resources.isEmpty()) {
      items.add(
          item(
              "RESOURCES_NOT_DISCOVERED",
              AdRecommendationSeverity.INFO,
              AdRecommendationCategory.SETUP,
              AdRecommendationSource.REACHLYO_DIAGNOSTIC,
              "No Meta account resources are synchronized",
              "Reachlyo has no synchronized Page, Instagram, or Pixel records for this account. Sync resources before treating individual resource types as missing.",
              "AD_ACCOUNT",
              account.getId().toString(),
              "Sync resources",
              "/ad-center/settings",
              Map.of()));
      return;
    }

    long pages =
        resources.stream()
            .filter(resource -> resource.isAccessible())
            .filter(resource -> resource.getResourceType() == AdAccountResourceType.FACEBOOK_PAGE)
            .count();
    long instagram =
        resources.stream()
            .filter(resource -> resource.isAccessible())
            .filter(
                resource ->
                    resource.getResourceType() == AdAccountResourceType.INSTAGRAM_ACCOUNT)
            .count();
    long pixels =
        resources.stream()
            .filter(resource -> resource.isAccessible())
            .filter(resource -> resource.getResourceType() == AdAccountResourceType.META_PIXEL)
            .count();

    if (pages == 0) {
      items.add(
          item(
              "NO_FACEBOOK_PAGE",
              AdRecommendationSeverity.WARNING,
              AdRecommendationCategory.SETUP,
              AdRecommendationSource.REACHLYO_DIAGNOSTIC,
              "No accessible Facebook Page is synchronized",
              "A Facebook Page is commonly required as the ad identity for Meta creatives. Synchronize resources or fix Page access before publishing affected ads.",
              "AD_ACCOUNT",
              account.getId().toString(),
              "Review resources",
              "/ad-center/settings",
              Map.of()));
    }

    if (pixels == 0) {
      items.add(
          item(
              "NO_META_PIXEL",
              AdRecommendationSeverity.OPPORTUNITY,
              AdRecommendationCategory.TRACKING,
              AdRecommendationSource.REACHLYO_DIAGNOSTIC,
              "No accessible Meta Pixel is synchronized",
              "A Pixel is not required for every campaign, but it enables website conversion measurement, optimization, and website-based audiences.",
              "AD_ACCOUNT",
              account.getId().toString(),
              "Set up conversion tracking",
              "/ad-center/conversions",
              Map.of()));
    }

    if (instagram == 0) {
      items.add(
          item(
              "NO_INSTAGRAM_ACCOUNT",
              AdRecommendationSeverity.OPPORTUNITY,
              AdRecommendationCategory.SETUP,
              AdRecommendationSource.REACHLYO_DIAGNOSTIC,
              "No Instagram account is synchronized",
              "Connecting an Instagram identity can expand supported creative identity and placement options for eligible campaigns.",
              "AD_ACCOUNT",
              account.getId().toString(),
              "Review resources",
              "/ad-center/settings",
              Map.of()));
    }
  }

  private void addBillingDiagnostics(
      AdAccount account,
      AdAccountBilling billing,
      List<AdRecommendationsResponse.Item> items) {
    if (billing == null) {
      items.add(
          item(
              "BILLING_NOT_SYNCED",
              AdRecommendationSeverity.INFO,
              AdRecommendationCategory.BILLING,
              AdRecommendationSource.REACHLYO_DIAGNOSTIC,
              "Billing state has not been synchronized",
              "Sync Meta billing to monitor the provider-reported balance, amount spent, and account spending limit.",
              "AD_ACCOUNT",
              account.getId().toString(),
              "Sync billing",
              "/ad-center/billing",
              Map.of()));
      return;
    }

    Long cap = billing.getSpendCapMinor();
    Long spent = billing.getAmountSpentMinor();
    if (cap == null || cap <= 0 || spent == null) return;

    long remaining = Math.max(0L, cap - spent);
    if (remaining == 0L) {
      items.add(
          item(
              "SPEND_CAP_REACHED",
              AdRecommendationSeverity.CRITICAL,
              AdRecommendationCategory.BILLING,
              AdRecommendationSource.REACHLYO_DIAGNOSTIC,
              "Account spending limit is reached",
              "Meta's reported amount spent has reached or exceeded the configured account spending limit. Delivery can be restricted until the limit is changed or reset.",
              "AD_ACCOUNT",
              account.getId().toString(),
              "Review spending limit",
              "/ad-center/billing",
              evidence(
                  "spendCapMinor",
                  cap,
                  "amountSpentMinor",
                  spent,
                  "remainingSpendCapMinor",
                  remaining)));
      return;
    }

    BigDecimal ratio =
        BigDecimal.valueOf(remaining)
            .divide(BigDecimal.valueOf(cap), 4, java.math.RoundingMode.HALF_UP);
    if (ratio.compareTo(new BigDecimal("0.10")) <= 0) {
      items.add(
          item(
              "SPEND_CAP_LOW",
              AdRecommendationSeverity.WARNING,
              AdRecommendationCategory.BILLING,
              AdRecommendationSource.REACHLYO_DIAGNOSTIC,
              "Account spending limit is nearly reached",
              "Ten percent or less of the configured Meta account spending limit remains based on the latest synchronized billing state.",
              "AD_ACCOUNT",
              account.getId().toString(),
              "Review spending limit",
              "/ad-center/billing",
              evidence(
                  "spendCapMinor",
                  cap,
                  "amountSpentMinor",
                  spent,
                  "remainingSpendCapMinor",
                  remaining)));
    }
  }

  private void addCampaignStatusDiagnostics(
      List<AdCampaign> campaigns,
      List<AdRecommendationsResponse.Item> items) {
    for (AdCampaign campaign : campaigns) {
      if (!campaign.isAccessible() || campaign.getStatus() != AdEntityStatus.ACTIVE) continue;

      String effective = campaign.getEffectiveStatus();
      if (effective == null || effective.isBlank() || "ACTIVE".equalsIgnoreCase(effective)) {
        continue;
      }

      items.add(
          item(
              "CAMPAIGN_EFFECTIVE_STATUS_" + campaign.getId(),
              AdRecommendationSeverity.WARNING,
              AdRecommendationCategory.DELIVERY,
              AdRecommendationSource.META_PROVIDER,
              campaign.getName() + " is not effectively active",
              "The campaign is configured as ACTIVE, but Meta currently reports effective status "
                  + effective
                  + ". Review the campaign and its parent/account conditions.",
              "CAMPAIGN",
              campaign.getId().toString(),
              "Open campaign",
              "/ad-center/campaigns/" + campaign.getId(),
              evidence("effectiveStatus", effective)));
    }
  }

  private void addConversionDiagnostics(
      AdAccount account,
      List<AdConversion> conversions,
      List<AdCampaign> campaigns,
      List<AdInsightDaily> insights,
      List<AdRecommendationsResponse.Item> items) {
    List<AdConversion> usable =
        conversions.stream()
            .filter(AdConversion::isAccessible)
            .filter(conversion -> !conversion.isArchived())
            .filter(conversion -> !conversion.isUnavailable())
            .toList();

    if (usable.isEmpty()) {
      items.add(
          item(
              "NO_CONVERSION_SOURCES",
              AdRecommendationSeverity.OPPORTUNITY,
              AdRecommendationCategory.TRACKING,
              AdRecommendationSource.REACHLYO_DIAGNOSTIC,
              "No usable conversion source is configured",
              "No accessible, non-archived conversion source is currently available for this account. Conversion-focused campaigns benefit from a healthy measurement source.",
              "AD_ACCOUNT",
              account.getId().toString(),
              "Review conversions",
              "/ad-center/conversions",
              Map.of()));
      return;
    }

    boolean conversionCampaignSpending =
        campaigns.stream()
            .filter(this::conversionFocusedActiveCampaign)
            .anyMatch(
                campaign ->
                    aggregate(insights, campaign.getId()).spend().signum() > 0);

    if (!conversionCampaignSpending) return;

    Instant latest =
        usable.stream()
            .map(AdConversion::getLastFiredAt)
            .filter(value -> value != null)
            .max(Instant::compareTo)
            .orElse(null);

    if (latest == null) {
      items.add(
          item(
              "CONVERSIONS_NEVER_FIRED",
              AdRecommendationSeverity.WARNING,
              AdRecommendationCategory.TRACKING,
              AdRecommendationSource.REACHLYO_DIAGNOSTIC,
              "Conversion-focused campaigns are spending but no conversion has fired",
              "Recent campaign analytics show spend on a conversion-focused campaign, while synchronized conversion sources have no recorded last-fired time.",
              "AD_ACCOUNT",
              account.getId().toString(),
              "Inspect conversion tracking",
              "/ad-center/conversions",
              Map.of()));
    } else if (latest.isBefore(Instant.now().minus(Duration.ofDays(7)))) {
      items.add(
          item(
              "CONVERSIONS_STALE",
              AdRecommendationSeverity.WARNING,
              AdRecommendationCategory.TRACKING,
              AdRecommendationSource.REACHLYO_DIAGNOSTIC,
              "Conversion activity appears stale",
              "Conversion-focused campaigns are spending, but the latest synchronized conversion event is more than seven days old.",
              "AD_ACCOUNT",
              account.getId().toString(),
              "Inspect conversion tracking",
              "/ad-center/conversions",
              evidence("lastConversionFiredAt", latest.toString())));
    }
  }

  private void addPerformanceDiagnostics(
      List<AdCampaign> campaigns,
      List<AdInsightDaily> insights,
      List<AdRecommendationsResponse.Item> items) {
    for (AdCampaign campaign : campaigns) {
      if (!conversionFocusedActiveCampaign(campaign)) continue;

      CampaignAggregate aggregate = aggregate(insights, campaign.getId());
      if (!aggregate.hasRows() || aggregate.spend().signum() <= 0) continue;

      if (aggregate.conversions().signum() == 0) {
        items.add(
            item(
                "SPEND_WITHOUT_CONVERSIONS_" + campaign.getId(),
                AdRecommendationSeverity.WARNING,
                AdRecommendationCategory.PERFORMANCE,
                AdRecommendationSource.REACHLYO_DIAGNOSTIC,
                campaign.getName() + " spent without recorded conversions",
                "The last seven synchronized daily campaign rows show spend but zero recorded conversions for this conversion-focused campaign. Verify tracking, targeting, creative, and optimization settings before increasing spend.",
                "CAMPAIGN",
                campaign.getId().toString(),
                "Review campaign analytics",
                "/ad-center/campaigns/" + campaign.getId(),
                evidence(
                    "sevenDaySpend",
                    aggregate.spend(),
                    "sevenDayClicks",
                    aggregate.clicks(),
                    "sevenDayConversions",
                    aggregate.conversions())));
      }
    }
  }

  private CampaignAggregate aggregate(List<AdInsightDaily> rows, Long campaignId) {
    BigDecimal spend = BigDecimal.ZERO;
    BigDecimal conversions = BigDecimal.ZERO;
    long clicks = 0L;
    boolean hasRows = false;

    for (AdInsightDaily row : rows) {
      if (campaignId == null || !campaignId.equals(row.getCampaignId())) continue;
      hasRows = true;
      if (row.getSpend() != null) spend = spend.add(row.getSpend());
      if (row.getConversions() != null) conversions = conversions.add(row.getConversions());
      clicks += row.getClicks();
    }

    return new CampaignAggregate(hasRows, spend, clicks, conversions);
  }

  private boolean conversionFocusedActiveCampaign(AdCampaign campaign) {
    return campaign.isAccessible()
        && campaign.getStatus() == AdEntityStatus.ACTIVE
        && campaign.getObjective() != null
        && CONVERSION_OBJECTIVES.contains(campaign.getObjective());
  }

  private int count(
      List<AdRecommendationsResponse.Item> items,
      AdRecommendationSeverity severity) {
    return (int) items.stream().filter(item -> item.severity() == severity).count();
  }

  private int severityRank(AdRecommendationSeverity severity) {
    return switch (severity) {
      case CRITICAL -> 0;
      case WARNING -> 1;
      case OPPORTUNITY -> 2;
      case INFO -> 3;
    };
  }

  private AdRecommendationsResponse.Item item(
      String key,
      AdRecommendationSeverity severity,
      AdRecommendationCategory category,
      AdRecommendationSource source,
      String title,
      String description,
      String entityType,
      String entityId,
      String actionLabel,
      String actionPath,
      Map<String, Object> evidence) {
    return new AdRecommendationsResponse.Item(
        key,
        severity,
        category,
        source,
        title,
        description,
        entityType,
        entityId,
        actionLabel,
        actionPath,
        evidence);
  }

  private Map<String, Object> evidence(Object... keyValues) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (int index = 0; index + 1 < keyValues.length; index += 2) {
      Object value = keyValues[index + 1];
      if (value != null) result.put(String.valueOf(keyValues[index]), value);
    }
    return Map.copyOf(result);
  }

  private String text(JsonNode node, String field) {
    if (node == null || node.path(field).isMissingNode() || node.path(field).isNull()) {
      return null;
    }
    String value = node.path(field).asText(null);
    return value == null || value.isBlank() ? null : value.trim();
  }

  private String first(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) return value.trim();
    }
    return "";
  }

  private record CampaignAggregate(
      boolean hasRows,
      BigDecimal spend,
      long clicks,
      BigDecimal conversions) {}
}
