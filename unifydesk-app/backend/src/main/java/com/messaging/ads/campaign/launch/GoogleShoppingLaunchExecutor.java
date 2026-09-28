package com.messaging.ads.campaign.launch;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.campaign.compiler.PlanIssueSeverity;
import com.messaging.ads.campaign.compiler.ProviderCampaignPlan;
import com.messaging.ads.campaign.compiler.ProviderPlanIssue;
import com.messaging.ads.campaign.entity.AudienceDefinition;
import com.messaging.ads.campaign.entity.CampaignChannel;
import com.messaging.ads.campaign.entity.ConversionGoalMapping;
import com.messaging.ads.campaign.entity.ReachlyoCampaign;
import com.messaging.ads.campaign.enums.BudgetPeriod;
import com.messaging.ads.campaign.enums.CampaignChannelType;
import com.messaging.ads.campaign.repository.AudienceDefinitionRepository;
import com.messaging.ads.campaign.repository.CampaignChannelRepository;
import com.messaging.ads.campaign.repository.ConversionGoalMappingRepository;
import com.messaging.ads.campaign.repository.ReachlyoCampaignRepository;
import com.messaging.ads.campaign.service.ProviderDeploymentService;
import com.messaging.ads.dto.AdCampaignResponse;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.GoogleAdsAdvancedCampaignService;
import com.messaging.ads.google.GoogleAdsCampaignConversionGoalService;
import com.messaging.ads.google.GoogleAdsCampaignSyncService;
import com.messaging.ads.google.GoogleAdsCampaignTargetingService;
import com.messaging.ads.google.GoogleMerchantService;
import com.messaging.ads.google.dto.CreateGoogleAdvancedCampaignRequest;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GoogleShoppingLaunchExecutor implements LaunchProviderExecutor {

  private final ReachlyoCampaignRepository campaignRepository;
  private final CampaignChannelRepository channelRepository;
  private final AudienceDefinitionRepository audienceDefinitionRepository;
  private final ConversionGoalMappingRepository conversionGoalMappingRepository;
  private final AdAccountRepository accountRepository;
  private final AdCampaignRepository adCampaignRepository;
  private final GoogleAdsCampaignSyncService campaignSyncService;
  private final GoogleAdsAdvancedCampaignService advancedCampaignService;
  private final GoogleAdsCampaignTargetingService campaignTargetingService;
  private final GoogleAdsCampaignConversionGoalService conversionGoalService;
  private final GoogleMerchantService merchantService;
  private final ProviderDeploymentService deploymentService;
  private final ObjectMapper objectMapper;

  @Override
  public Set<CampaignChannelType> supportedChannels() {
    return Set.of(CampaignChannelType.GOOGLE_SHOPPING);
  }

  @Override
  public List<ProviderPlanIssue> preflight(
      Long companyId, LaunchRun run, ProviderCampaignPlan plan) {
    List<ProviderPlanIssue> issues = new ArrayList<>();
    ReachlyoCampaign campaign = campaign(companyId, run.getReachlyoCampaignId());
    CampaignChannel channel = channel(companyId, campaign.getId(), Long.valueOf(plan.channelId()));
    AdAccount account = account(companyId, Long.valueOf(plan.adAccountId()));
    JsonNode override = override(channel);

    if (!override.path("containsEuPoliticalAdvertising").isBoolean()) {
      issues.add(error(
          "GOOGLE_POLITICAL_DECLARATION_REQUIRED",
          "providerOverride.containsEuPoliticalAdvertising",
          "Shopping requires an explicit EU political advertising declaration."));
    }

    String merchantId = text(override, "merchantId");
    if (!hasText(merchantId)) {
      issues.add(error(
          "SHOPPING_MERCHANT_REQUIRED",
          "providerOverride.merchantId",
          "Choose a Merchant Center account before launching Shopping."));
    } else {
      try {
        boolean accessible =
            merchantService.accounts(companyId, account.getId()).stream()
                .anyMatch(value -> merchantId.equals(value.accountId()));
        if (!accessible) {
          issues.add(error(
              "SHOPPING_MERCHANT_INACCESSIBLE",
              "providerOverride.merchantId",
              "The selected Merchant Center account is not accessible through this Google connection."));
        } else {
          var page =
              merchantService.products(companyId, account.getId(), merchantId, null, 100);
          if (page.products().isEmpty()) {
            issues.add(error(
                "SHOPPING_PRODUCTS_MISSING",
                "merchant",
                "The selected Merchant Center account has no processed products available for Shopping."));
          } else if (page.products().stream().allMatch(product -> !product.issues().isEmpty())) {
            issues.add(warning(
                "SHOPPING_PRODUCTS_HAVE_ISSUES",
                "merchant",
                "The first Merchant product page contains product issues. Google may restrict or reject affected items."));
          }
        }
      } catch (RuntimeException exception) {
        issues.add(error(
            "SHOPPING_MERCHANT_PREFLIGHT_FAILED",
            "merchant",
            exception.getMessage()));
      }
    }

    Integer priority = integer(override, "campaignPriority");
    if (priority == null || priority < 0 || priority > 2) {
      issues.add(error(
          "SHOPPING_PRIORITY_INVALID",
          "providerOverride.campaignPriority",
          "Shopping campaign priority must be 0, 1, or 2."));
    }

    if (dailyBudget(plan, campaign, account, false) == null) {
      issues.add(error(
          "GOOGLE_DAILY_BUDGET_UNRESOLVED",
          "budget",
          "Shopping needs a daily budget, or a total budget with a valid start/end schedule."));
    }

    AudienceDefinition audience =
        audience(companyId, campaign.getId(), plan.audienceDefinitionId(), false);
    if (audience != null) {
      try {
        campaignTargetingService.resolve(
            companyId,
            account.getId(),
            geoLanguageOnly(audience),
            objectMapper.createObjectNode());
      } catch (RuntimeException exception) {
        issues.add(error(
            "SHOPPING_GEO_LANGUAGE_INVALID",
            "audience",
            exception.getMessage()));
      }
      if (audience.getAgeMin() != null
          || audience.getAgeMax() != null
          || hasText(audience.getGendersJson())
          || hasText(audience.getInterestsJson())
          || hasText(audience.getSearchIntentJson())) {
        issues.add(warning(
            "SHOPPING_AUDIENCE_PARTIAL",
            "audience",
            "Unified Shopping currently maps canonical geo/language targeting. Demographics, interests, and search intent are not converted into Shopping product filters."));
      }
    }

    if (hasText(plan.conversionGoalMappingId())) {
      try {
        conversionGoalService.validate(
            companyId,
            account.getId(),
            conversion(companyId, campaign.getId(), plan.conversionGoalMappingId()));
      } catch (RuntimeException exception) {
        issues.add(error(
            "GOOGLE_CONVERSION_MAPPING_INVALID",
            "conversion",
            exception.getMessage()));
      }
    }

    return List.copyOf(issues);
  }

  @Override
  public LaunchStepExecutionResult execute(
      Long companyId, LaunchRun run, ProviderCampaignPlan plan, LaunchStep step) {
    ReachlyoCampaign campaign = campaign(companyId, run.getReachlyoCampaignId());
    CampaignChannel channel =
        channel(companyId, campaign.getId(), step.getCampaignChannelId());

    return switch (step.getOperationKey()) {
      case "create-shopping-paused" ->
          createShopping(companyId, run, plan, step, campaign, channel);
      case "configure-shopping-targeting" ->
          configureTargeting(companyId, plan, step, campaign);
      case "configure-conversion-goal" ->
          configureConversion(companyId, run, plan, step, campaign);
      default -> throw new BadRequestException(
          "Unsupported Shopping launch operation: " + step.getOperationKey());
    };
  }

  private LaunchStepExecutionResult createShopping(
      Long companyId,
      LaunchRun run,
      ProviderCampaignPlan plan,
      LaunchStep step,
      ReachlyoCampaign campaign,
      CampaignChannel channel) {
    AdAccount account = account(companyId, step.getAdAccountId());
    String name = launchName(campaign.getName(), run.getId(), plan.channelId());

    if (step.getAttemptCount() > 1) {
      campaignSyncService.sync(companyId, account.getId());
    }

    AdCampaign existing =
        adCampaignRepository
            .findFirstByCompanyIdAndAdAccountIdAndProviderAndNameOrderByIdAsc(
                companyId, account.getId(), AdProvider.GOOGLE_ADS, name)
            .filter(value -> "SHOPPING".equalsIgnoreCase(value.getObjective()))
            .orElse(null);

    AdCampaignResponse result;
    if (existing != null) {
      result = AdCampaignResponse.from(existing);
    } else {
      JsonNode override = override(channel);
      String merchantId = requiredText(override, "merchantId", "Merchant Center account");
      Integer priority = integer(override, "campaignPriority");
      if (priority == null) priority = 0;

      result =
          advancedCampaignService.create(
              companyId,
              account.getId(),
              new CreateGoogleAdvancedCampaignRequest(
                  name,
                  "SHOPPING",
                  dailyBudget(plan, campaign, account, true),
                  null,
                  "MANUAL_CPC",
                  null,
                  null,
                  merchantId,
                  priority,
                  override.path("enableLocal").asBoolean(false),
                  override.path("containsEuPoliticalAdvertising").booleanValue(),
                  localDate(campaign.getStartAt(), account),
                  localDate(campaign.getEndAt(), account)),
              run.getRequestedByUserId());
    }

    deploymentService.bindProviderCampaign(
        companyId, step.getProviderDeploymentId(), Long.valueOf(result.id()));

    return LaunchStepExecutionResult.succeeded(
        Long.valueOf(result.id()),
        result.externalCampaignId(),
        Map.of(
            "reused", existing != null,
            "channel", "SHOPPING",
            "merchantId", requiredText(override(channel), "merchantId", "Merchant Center account")));
  }

  private LaunchStepExecutionResult configureTargeting(
      Long companyId,
      ProviderCampaignPlan plan,
      LaunchStep step,
      ReachlyoCampaign campaign) {
    AudienceDefinition audience =
        audience(companyId, campaign.getId(), plan.audienceDefinitionId(), false);
    if (audience == null) {
      return LaunchStepExecutionResult.skipped("No canonical audience was selected.");
    }

    AdCampaign providerCampaign =
        providerCampaign(companyId, step.getProviderDeploymentId());
    var result =
        campaignTargetingService.apply(
            companyId,
            providerCampaign.getId(),
            geoLanguageOnly(audience),
            objectMapper.createObjectNode());

    return LaunchStepExecutionResult.succeeded(
        null,
        result.firstExternalResource(),
        Map.of(
            "createdCount", result.createdCount(),
            "reusedCount", result.reusedCount(),
            "locationCount", result.locationCount(),
            "languageCount", result.languageCount()));
  }

  private LaunchStepExecutionResult configureConversion(
      Long companyId,
      LaunchRun run,
      ProviderCampaignPlan plan,
      LaunchStep step,
      ReachlyoCampaign campaign) {
    if (!hasText(plan.conversionGoalMappingId())) {
      return LaunchStepExecutionResult.skipped(
          "No Google conversion mapping is configured.");
    }

    AdCampaign providerCampaign =
        providerCampaign(companyId, step.getProviderDeploymentId());
    var result =
        conversionGoalService.apply(
            companyId,
            step.getAdAccountId(),
            providerCampaign.getExternalCampaignId(),
            run.getReachlyoCampaignId(),
            conversion(companyId, campaign.getId(), plan.conversionGoalMappingId()));

    return LaunchStepExecutionResult.succeeded(
        null,
        result.customConversionGoalResource(),
        Map.of(
            "created", result.created(),
            "updated", result.updated(),
            "disabledCampaignGoals", result.disabledCampaignGoals()));
  }

  private AudienceDefinition geoLanguageOnly(AudienceDefinition source) {
    AudienceDefinition copy = new AudienceDefinition();
    copy.setLocationsJson(source.getLocationsJson());
    copy.setLanguagesJson(source.getLanguagesJson());
    return copy;
  }

  private ReachlyoCampaign campaign(Long companyId, Long campaignId) {
    return campaignRepository
        .findByIdAndCompanyId(campaignId, companyId)
        .orElseThrow(() -> new NotFoundException("Reachlyo campaign not found"));
  }

  private CampaignChannel channel(
      Long companyId, Long campaignId, Long channelId) {
    return channelRepository
        .findByIdAndCompanyIdAndReachlyoCampaignId(
            channelId, companyId, campaignId)
        .orElseThrow(() -> new NotFoundException("Campaign channel not found"));
  }

  private AdAccount account(Long companyId, Long accountId) {
    return accountRepository
        .findByIdAndCompanyId(accountId, companyId)
        .orElseThrow(() -> new NotFoundException("Advertising account not found"));
  }

  private AudienceDefinition audience(
      Long companyId, Long campaignId, String audienceId, boolean required) {
    if (!hasText(audienceId)) {
      if (required) throw new BadRequestException("Audience definition is required");
      return null;
    }
    return audienceDefinitionRepository
        .findByIdAndCompanyIdAndReachlyoCampaignId(
            Long.valueOf(audienceId), companyId, campaignId)
        .orElseThrow(() -> new NotFoundException("Audience definition not found"));
  }

  private ConversionGoalMapping conversion(
      Long companyId, Long campaignId, String mappingId) {
    return conversionGoalMappingRepository
        .findByIdAndCompanyIdAndReachlyoCampaignId(
            Long.valueOf(mappingId), companyId, campaignId)
        .orElseThrow(() -> new NotFoundException("Conversion goal mapping not found"));
  }

  private AdCampaign providerCampaign(Long companyId, Long deploymentId) {
    var deployment = deploymentService.get(companyId, deploymentId);
    if (deployment.getLocalCampaignId() == null) {
      throw new BadRequestException("Provider campaign has not been created yet");
    }
    return adCampaignRepository
        .findByIdAndCompanyId(deployment.getLocalCampaignId(), companyId)
        .orElseThrow(() -> new NotFoundException("Advertising campaign not found"));
  }

  private JsonNode override(CampaignChannel channel) {
    try {
      return hasText(channel.getProviderOverrideJson())
          ? objectMapper.readTree(channel.getProviderOverrideJson())
          : objectMapper.createObjectNode();
    } catch (Exception exception) {
      throw new BadRequestException(
          "Campaign channel provider override is invalid JSON");
    }
  }

  private Long dailyBudget(
      ProviderCampaignPlan plan,
      ReachlyoCampaign campaign,
      AdAccount account,
      boolean required) {
    Long budget = plan.effectiveBudgetMinor();
    if (budget == null || budget <= 0) return required ? failBudget() : null;
    if (plan.budgetPeriod() == BudgetPeriod.DAILY) return budget;

    LocalDate start = localDate(campaign.getStartAt(), account);
    LocalDate end = localDate(campaign.getEndAt(), account);
    if (start == null || end == null || end.isBefore(start)) {
      return required ? failBudget() : null;
    }
    long days = ChronoUnit.DAYS.between(start, end) + 1;
    return Math.max(1L, (budget + days - 1) / days);
  }

  private Long failBudget() {
    throw new BadRequestException(
        "Google total budget requires a valid campaign schedule so UnifyDesk can derive the daily budget");
  }

  private LocalDate localDate(Instant instant, AdAccount account) {
    if (instant == null) return null;
    ZoneId zone = ZoneId.of("UTC");
    if (hasText(account.getTimezone())) {
      try {
        zone = ZoneId.of(account.getTimezone());
      } catch (Exception ignored) {
        zone = ZoneId.of("UTC");
      }
    }
    return instant.atZone(zone).toLocalDate();
  }

  private Integer integer(JsonNode node, String key) {
    JsonNode value = node.get(key);
    if (value == null || value.isNull() || !value.canConvertToInt()) return null;
    return value.intValue();
  }

  private String requiredText(JsonNode node, String key, String label) {
    String value = text(node, key);
    if (!hasText(value)) throw new BadRequestException(label + " is required");
    return value;
  }

  private String text(JsonNode node, String key) {
    JsonNode value = node.get(key);
    if (value == null || value.isNull()) return null;
    String result = value.asText(null);
    return result == null || result.isBlank() ? null : result.trim();
  }

  private String launchName(String base, Long runId, String channelId) {
    String suffix = " [RLY-" + runId + "-" + channelId + "]";
    String normalized = hasText(base) ? base.trim() : "UnifyDesk";
    int maxBase = Math.max(1, 255 - suffix.length());
    return (normalized.length() > maxBase
            ? normalized.substring(0, maxBase)
            : normalized)
        + suffix;
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private ProviderPlanIssue error(
      String code, String field, String message) {
    return new ProviderPlanIssue(
        PlanIssueSeverity.ERROR, code, field, message);
  }

  private ProviderPlanIssue warning(
      String code, String field, String message) {
    return new ProviderPlanIssue(
        PlanIssueSeverity.WARNING, code, field, message);
  }
}
