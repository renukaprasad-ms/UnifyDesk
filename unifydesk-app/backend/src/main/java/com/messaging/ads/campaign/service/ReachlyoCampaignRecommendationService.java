package com.messaging.ads.campaign.service;

import com.messaging.ads.campaign.dto.ReachlyoCampaignAnalyticsResponse;
import com.messaging.ads.campaign.dto.ReachlyoCampaignRecommendationsResponse;
import com.messaging.ads.campaign.entity.ProviderDeployment;
import com.messaging.ads.campaign.entity.ReachlyoCampaign;
import com.messaging.ads.campaign.enums.ProviderDeploymentStatus;
import com.messaging.ads.campaign.repository.ProviderDeploymentRepository;
import com.messaging.ads.campaign.repository.ReachlyoCampaignRepository;
import com.messaging.ads.dto.AdRecommendationsResponse;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.enums.AdRecommendationCategory;
import com.messaging.ads.enums.AdRecommendationSeverity;
import com.messaging.ads.enums.AdRecommendationSource;
import com.messaging.ads.google.GoogleAdsNativeRecommendationService;
import com.messaging.ads.service.AdRecommendationLifecycleService;
import com.messaging.common.exception.NotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReachlyoCampaignRecommendationService {

  private final ReachlyoCampaignRepository campaignRepository;
  private final ProviderDeploymentRepository deploymentRepository;
  private final ReachlyoCampaignAnalyticsService analyticsService;
  private final AdRecommendationLifecycleService recommendationLifecycleService;
  private final GoogleAdsNativeRecommendationService googleNativeRecommendationService;

  @Transactional(readOnly = true)
  public ReachlyoCampaignRecommendationsResponse recommendations(
      Long companyId, Long campaignId) {
    ReachlyoCampaign campaign =
        campaignRepository
            .findByIdAndCompanyId(campaignId, companyId)
            .orElseThrow(() -> new NotFoundException("Reachlyo campaign not found"));

    List<ProviderDeployment> deployments =
        deploymentRepository.findByCompanyIdAndReachlyoCampaignIdOrderByIdAsc(
            companyId, campaignId);

    LocalDate to = LocalDate.now(ZoneOffset.UTC);
    LocalDate from = to.minusDays(6);
    ReachlyoCampaignAnalyticsResponse analytics =
        analyticsService.analytics(companyId, campaignId, from, to);

    List<ReachlyoCampaignRecommendationsResponse.Item> items = new ArrayList<>();
    addDeploymentDiagnostics(deployments, items);
    addUnifiedPerformanceDiagnostics(campaign, analytics, items);
    addProviderDiagnostics(companyId, deployments, items);
    addGoogleNativeRecommendations(companyId, deployments, items);

    items.sort(
        Comparator.comparingInt(
                (ReachlyoCampaignRecommendationsResponse.Item item) ->
                    severityRank(item.severity()))
            .thenComparing(item -> item.provider() == null ? "" : item.provider().name())
            .thenComparing(ReachlyoCampaignRecommendationsResponse.Item::title));

    return new ReachlyoCampaignRecommendationsResponse(
        campaign.getId().toString(),
        count(items, AdRecommendationSeverity.CRITICAL),
        count(items, AdRecommendationSeverity.WARNING),
        count(items, AdRecommendationSeverity.OPPORTUNITY),
        count(items, AdRecommendationSeverity.INFO),
        Instant.now(),
        analytics.lastSyncedAt(),
        List.copyOf(items));
  }

  private void addDeploymentDiagnostics(
      List<ProviderDeployment> deployments,
      List<ReachlyoCampaignRecommendationsResponse.Item> items) {
    for (ProviderDeployment deployment : deployments) {
      if (deployment.getDeploymentStatus() == ProviderDeploymentStatus.FAILED
          || deployment.getDeploymentStatus() == ProviderDeploymentStatus.PARTIAL_FAILURE) {
        items.add(
            item(
                "DEPLOYMENT_" + deployment.getDeploymentStatus() + "_" + deployment.getId(),
                AdRecommendationSeverity.CRITICAL,
                AdRecommendationCategory.DELIVERY,
                AdRecommendationSource.REACHLYO_DIAGNOSTIC,
                deployment,
                providerName(deployment.getProvider()) + " deployment needs attention",
                first(
                    deployment.getLastErrorMessage(),
                    "The provider deployment did not complete cleanly. Review the launch run before changing budgets or optimization settings."),
                "Review campaign launch",
                null,
                evidence(
                    "deploymentStatus",
                    deployment.getDeploymentStatus().name(),
                    "errorCode",
                    deployment.getLastErrorCode()),
                null));
      }
    }
  }

  private void addUnifiedPerformanceDiagnostics(
      ReachlyoCampaign campaign,
      ReachlyoCampaignAnalyticsResponse analytics,
      List<ReachlyoCampaignRecommendationsResponse.Item> items) {
    if (analytics.lastSyncedAt() == null) {
      items.add(
          generic(
              "ANALYTICS_NOT_SYNCED",
              AdRecommendationSeverity.INFO,
              AdRecommendationCategory.PERFORMANCE,
              "Campaign analytics have not been synchronized",
              "Refresh provider analytics before making performance decisions for this campaign.",
              "Refresh campaign analytics",
              null,
              Map.of("windowDays", 7)));
    } else if (Duration.between(analytics.lastSyncedAt(), Instant.now()).compareTo(Duration.ofHours(24))
        > 0) {
      items.add(
          generic(
              "ANALYTICS_STALE",
              AdRecommendationSeverity.INFO,
              AdRecommendationCategory.PERFORMANCE,
              "Campaign analytics are stale",
              "The latest synchronized provider analytics are more than 24 hours old. Refresh data before acting on performance changes.",
              "Refresh campaign analytics",
              null,
              evidence("lastSyncedAt", analytics.lastSyncedAt().toString())));
    }

    if (analytics.summary().spend().signum() > 0
        && analytics.summary().conversions().signum() == 0) {
      items.add(
          generic(
              "UNIFIED_SPEND_WITHOUT_CONVERSIONS",
              AdRecommendationSeverity.WARNING,
              AdRecommendationCategory.PERFORMANCE,
              campaign.getName() + " has spend but no recorded conversions",
              "Across the last seven days, provider deployments recorded spend but no conversions. Verify the mapped conversion goal, landing experience, targeting, and creative before increasing spend.",
              "Review analytics",
              null,
              evidence(
                  "sevenDaySpend",
                  analytics.summary().spend(),
                  "sevenDayClicks",
                  analytics.summary().clicks(),
                  "sevenDayConversions",
                  analytics.summary().conversions())));
    }

    List<ReachlyoCampaignAnalyticsResponse.DeploymentRow> measurable =
        analytics.deployments().stream()
            .filter(row -> row.summary().spend().signum() > 0)
            .filter(row -> row.summary().conversions().compareTo(BigDecimal.valueOf(3)) >= 0)
            .filter(row -> row.summary().cpa() != null && row.summary().cpa().signum() > 0)
            .toList();

    if (measurable.size() >= 2) {
      ReachlyoCampaignAnalyticsResponse.DeploymentRow best =
          measurable.stream()
              .min(Comparator.comparing(row -> row.summary().cpa()))
              .orElseThrow();
      ReachlyoCampaignAnalyticsResponse.DeploymentRow worst =
          measurable.stream()
              .max(Comparator.comparing(row -> row.summary().cpa()))
              .orElseThrow();
      BigDecimal ratio =
          worst.summary().cpa().divide(best.summary().cpa(), 4, RoundingMode.HALF_UP);
      if (ratio.compareTo(BigDecimal.valueOf(2)) >= 0) {
        items.add(
            generic(
                "CROSS_PROVIDER_CPA_DIVERGENCE",
                AdRecommendationSeverity.OPPORTUNITY,
                AdRecommendationCategory.PERFORMANCE,
                "CPA differs materially across channels",
                providerName(best.provider())
                    + " is currently producing materially lower CPA than "
                    + providerName(worst.provider())
                    + " over the same seven-day window. Review budget allocation and campaign quality before making any change.",
                "Compare channel performance",
                null,
                evidence(
                    "lowerCpaProvider",
                    best.provider().name(),
                    "lowerCpa",
                    best.summary().cpa(),
                    "higherCpaProvider",
                    worst.provider().name(),
                    "higherCpa",
                    worst.summary().cpa(),
                    "cpaRatio",
                    ratio)));
      }
    }

    for (ReachlyoCampaignAnalyticsResponse.DeploymentRow row : analytics.deployments()) {
      if (row.deploymentStatus() != ProviderDeploymentStatus.ACTIVE) continue;
      if (row.summary().spend().signum() == 0) {
        items.add(
            new ReachlyoCampaignRecommendationsResponse.Item(
                "ACTIVE_NO_SPEND_" + row.deploymentId(),
                AdRecommendationSeverity.WARNING,
                AdRecommendationCategory.DELIVERY,
                AdRecommendationSource.REACHLYO_DIAGNOSTIC,
                row.provider(),
                row.adAccountId(),
                row.deploymentId(),
                row.localCampaignId(),
                providerName(row.provider()) + " deployment has no recent spend",
                "This deployment is marked active but has no spend in the last seven synchronized days. Review provider delivery status, billing, targeting, bid strategy, and approvals.",
                "Open provider campaign",
                row.localCampaignId() == null
                    ? null
                    : "/ad-center/campaigns/" + row.localCampaignId(),
                evidence(
                    "deploymentStatus",
                    row.deploymentStatus().name(),
                    "providerStatus",
                    row.providerStatus(),
                    "windowDays",
                    7),
                null));
      }
    }
  }

  private void addProviderDiagnostics(
      Long companyId,
      List<ProviderDeployment> deployments,
      List<ReachlyoCampaignRecommendationsResponse.Item> items) {
    for (ProviderDeployment deployment : deployments) {
      AdRecommendationsResponse feed;
      try {
        feed =
            recommendationLifecycleService.feed(
                companyId, deployment.getAdAccountId());
      } catch (RuntimeException ignored) {
        continue;
      }
      for (AdRecommendationsResponse.Item providerItem : feed.items()) {
        boolean campaignSpecific =
            "CAMPAIGN".equals(providerItem.entityType())
                && deployment.getLocalCampaignId() != null
                && deployment
                    .getLocalCampaignId()
                    .toString()
                    .equals(providerItem.entityId());
        boolean accountBlocking =
            "AD_ACCOUNT".equals(providerItem.entityType())
                && (providerItem.severity() == AdRecommendationSeverity.CRITICAL
                    || providerItem.severity() == AdRecommendationSeverity.WARNING);
        if (!campaignSpecific && !accountBlocking) continue;

        Map<String, Object> evidence = new LinkedHashMap<>(providerItem.evidence());
        evidence.put("providerRecommendationKey", providerItem.key());
        evidence.put("providerAccountId", deployment.getAdAccountId().toString());

        items.add(
            new ReachlyoCampaignRecommendationsResponse.Item(
                "PROVIDER_" + deployment.getId() + "_" + providerItem.key(),
                providerItem.severity(),
                providerItem.category(),
                providerItem.source(),
                deployment.getProvider(),
                deployment.getAdAccountId().toString(),
                deployment.getId().toString(),
                deployment.getLocalCampaignId() == null
                    ? null
                    : deployment.getLocalCampaignId().toString(),
                providerItem.title(),
                providerItem.description(),
                providerItem.actionLabel(),
                providerItem.actionPath(),
                Map.copyOf(evidence),
                null));
      }
    }
  }

  private void addGoogleNativeRecommendations(
      Long companyId,
      List<ProviderDeployment> deployments,
      List<ReachlyoCampaignRecommendationsResponse.Item> items) {
    for (ProviderDeployment deployment : deployments) {
      if (deployment.getProvider() != AdProvider.GOOGLE_ADS
          || deployment.getExternalCampaignId() == null) {
        continue;
      }

      List<com.messaging.ads.google.dto.GoogleNativeRecommendation> recommendations;
      try {
        recommendations =
            googleNativeRecommendationService.list(
                companyId, deployment.getAdAccountId());
      } catch (RuntimeException ignored) {
        continue;
      }

      String suffix = "/campaigns/" + deployment.getExternalCampaignId();
      for (var recommendation : recommendations) {
        boolean targetsCampaign =
            recommendation.campaignResourceName() != null
                && recommendation.campaignResourceName().endsWith(suffix);
        if (!targetsCampaign) continue;

        items.add(
            item(
                "GOOGLE_NATIVE_" + recommendation.resourceName(),
                AdRecommendationSeverity.OPPORTUNITY,
                AdRecommendationCategory.PERFORMANCE,
                AdRecommendationSource.GOOGLE_PROVIDER,
                deployment,
                "Google Ads recommendation: " + pretty(recommendation.type()),
                "Google Ads currently exposes this recommendation for the deployed campaign. Review the provider recommendation and its estimated impact before applying it.",
                "Review Google recommendation",
                "/ad-center/recommendations?accountId=" + deployment.getAdAccountId(),
                evidence(
                    "recommendationType",
                    recommendation.type(),
                    "impact",
                    recommendation.impactJson()),
                recommendation.resourceName()));
      }
    }
  }

  private ReachlyoCampaignRecommendationsResponse.Item item(
      String key,
      AdRecommendationSeverity severity,
      AdRecommendationCategory category,
      AdRecommendationSource source,
      ProviderDeployment deployment,
      String title,
      String description,
      String actionLabel,
      String actionPath,
      Map<String, Object> evidence,
      String providerRecommendationResourceName) {
    return new ReachlyoCampaignRecommendationsResponse.Item(
        key,
        severity,
        category,
        source,
        deployment.getProvider(),
        deployment.getAdAccountId().toString(),
        deployment.getId().toString(),
        deployment.getLocalCampaignId() == null
            ? null
            : deployment.getLocalCampaignId().toString(),
        title,
        description,
        actionLabel,
        actionPath,
        evidence,
        providerRecommendationResourceName);
  }

  private ReachlyoCampaignRecommendationsResponse.Item generic(
      String key,
      AdRecommendationSeverity severity,
      AdRecommendationCategory category,
      String title,
      String description,
      String actionLabel,
      String actionPath,
      Map<String, Object> evidence) {
    return new ReachlyoCampaignRecommendationsResponse.Item(
        key,
        severity,
        category,
        AdRecommendationSource.REACHLYO_DIAGNOSTIC,
        null,
        null,
        null,
        null,
        title,
        description,
        actionLabel,
        actionPath,
        evidence,
        null);
  }

  private Map<String, Object> evidence(Object... keyValues) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (int index = 0; index + 1 < keyValues.length; index += 2) {
      Object value = keyValues[index + 1];
      if (value != null) result.put(String.valueOf(keyValues[index]), value);
    }
    return Map.copyOf(result);
  }

  private String first(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private String providerName(AdProvider provider) {
    return provider == AdProvider.GOOGLE_ADS ? "Google Ads" : "Meta Ads";
  }

  private String pretty(String value) {
    if (value == null || value.isBlank()) return "Recommendation";
    String[] parts = value.toLowerCase().split("_");
    StringBuilder result = new StringBuilder();
    for (String part : parts) {
      if (part.isBlank()) continue;
      if (!result.isEmpty()) result.append(' ');
      result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
    }
    return result.toString();
  }

  private int severityRank(AdRecommendationSeverity severity) {
    return switch (severity) {
      case CRITICAL -> 0;
      case WARNING -> 1;
      case OPPORTUNITY -> 2;
      case INFO -> 3;
    };
  }

  private int count(
      List<ReachlyoCampaignRecommendationsResponse.Item> items,
      AdRecommendationSeverity severity) {
    return (int) items.stream().filter(item -> item.severity() == severity).count();
  }
}
