package com.messaging.ads.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.CreateGooglePortfolioBiddingStrategyRequest;
import com.messaging.ads.google.dto.CreateGoogleSharedBudgetRequest;
import com.messaging.ads.google.dto.GoogleCampaignPortfolioAssignmentRequest;
import com.messaging.ads.google.dto.GoogleCampaignPortfolioAssignmentResponse;
import com.messaging.ads.google.dto.GooglePortfolioAlignmentRequest;
import com.messaging.ads.google.dto.GooglePortfolioBiddingStrategyResponse;
import com.messaging.ads.google.dto.GoogleSharedBudgetResponse;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.ads.service.AdActivityLogService;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GoogleAdsPortfolioBiddingService {

  private static final Set<String> STRATEGY_TYPES =
      Set.of(
          "TARGET_CPA",
          "TARGET_ROAS",
          "MAXIMIZE_CONVERSIONS",
          "MAXIMIZE_CONVERSION_VALUE");
  private static final Set<String> COMPATIBLE_CAMPAIGN_TYPES =
      Set.of("SEARCH", "DISPLAY", "SHOPPING");

  private final GoogleAdsAccountContextService contextService;
  private final GoogleAdsApiClient apiClient;
  private final AdCampaignRepository campaignRepository;
  private final AdActivityLogService activityLogService;

  public List<GooglePortfolioBiddingStrategyResponse> strategies(
      Long companyId, Long accountId, Integer requestedDays) {
    var context = contextService.requireManaged(companyId, accountId);
    DateRange range = range(requestedDays);
    String query =
        "SELECT bidding_strategy.id, bidding_strategy.resource_name, "
            + "bidding_strategy.name, bidding_strategy.type, bidding_strategy.status, "
            + "bidding_strategy.campaign_count, bidding_strategy.non_removed_campaign_count, "
            + "bidding_strategy.aligned_campaign_budget_id, "
            + "bidding_strategy.target_cpa.target_cpa_micros, "
            + "bidding_strategy.target_roas.target_roas, "
            + "bidding_strategy.maximize_conversions.target_cpa_micros, "
            + "bidding_strategy.maximize_conversion_value.target_roas, "
            + "metrics.impressions, metrics.clicks, metrics.conversions, "
            + "metrics.conversions_value, metrics.cost_micros "
            + "FROM bidding_strategy WHERE bidding_strategy.status != 'REMOVED' "
            + "AND segments.date BETWEEN '"
            + range.start()
            + "' AND '"
            + range.end()
            + "'";

    List<GooglePortfolioBiddingStrategyResponse> result = new ArrayList<>();
    for (JsonNode row :
        apiClient.searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            query)) {
      result.add(strategy(row));
    }
    return List.copyOf(result);
  }

  public GooglePortfolioBiddingStrategyResponse createStrategy(
      Long companyId,
      Long accountId,
      CreateGooglePortfolioBiddingStrategyRequest request,
      Long actorUserId) {
    var context = contextService.requireManaged(companyId, accountId);
    String type = strategyType(request.type());
    long targetCpaMicros =
        request.targetCpaMinor() == null
            ? 0L
            : GoogleAdsMoney.minorToMicros(
                request.targetCpaMinor(), context.account().getCurrency());
    validateTargets(type, targetCpaMicros, request.targetRoas());

    Map<String, Object> create = new LinkedHashMap<>();
    create.put("name", request.name().trim());
    switch (type) {
      case "TARGET_CPA" ->
          create.put("targetCpa", Map.of("targetCpaMicros", targetCpaMicros));
      case "TARGET_ROAS" ->
          create.put("targetRoas", Map.of("targetRoas", request.targetRoas()));
      case "MAXIMIZE_CONVERSIONS" -> {
        Map<String, Object> scheme = new LinkedHashMap<>();
        if (targetCpaMicros > 0) scheme.put("targetCpaMicros", targetCpaMicros);
        create.put("maximizeConversions", scheme);
      }
      case "MAXIMIZE_CONVERSION_VALUE" -> {
        Map<String, Object> scheme = new LinkedHashMap<>();
        if (request.targetRoas() != null) scheme.put("targetRoas", request.targetRoas());
        create.put("maximizeConversionValue", scheme);
      }
      default -> throw new IllegalStateException("Unsupported portfolio strategy type");
    }

    JsonNode response =
        apiClient.mutate(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            "biddingStrategies",
            List.of(Map.of("create", create)));
    String resource = apiClient.firstMutatedResourceName(response);
    String id = idFromResource(resource);

    GooglePortfolioBiddingStrategyResponse created =
        strategies(companyId, accountId, 30).stream()
            .filter(value -> id.equals(value.id()))
            .findFirst()
            .orElse(
                new GooglePortfolioBiddingStrategyResponse(
                    id,
                    resource,
                    request.name().trim(),
                    type,
                    "ENABLED",
                    0L,
                    0L,
                    null,
                    targetCpaMicros == 0 ? null : targetCpaMicros,
                    request.targetRoas(),
                    null,
                    null,
                    null,
                    null,
                    null));

    activityLogService.record(
        companyId,
        accountId,
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_PORTFOLIO_BIDDING_STRATEGY_CREATED",
        "BIDDING_STRATEGY",
        id,
        null,
        created,
        Map.of("type", type));

    return created;
  }

  public List<GoogleSharedBudgetResponse> sharedBudgets(
      Long companyId, Long accountId, Integer requestedDays) {
    var context = contextService.requireManaged(companyId, accountId);
    DateRange range = range(requestedDays);
    String query =
        "SELECT campaign_budget.id, campaign_budget.resource_name, "
            + "campaign_budget.name, campaign_budget.status, "
            + "campaign_budget.delivery_method, campaign_budget.amount_micros, "
            + "campaign_budget.reference_count, campaign_budget.aligned_bidding_strategy_id, "
            + "metrics.impressions, metrics.clicks, metrics.conversions, "
            + "metrics.conversions_value, metrics.cost_micros "
            + "FROM campaign_budget "
            + "WHERE campaign_budget.explicitly_shared = TRUE "
            + "AND campaign_budget.status != 'REMOVED' "
            + "AND segments.date BETWEEN '"
            + range.start()
            + "' AND '"
            + range.end()
            + "'";

    List<GoogleSharedBudgetResponse> result = new ArrayList<>();
    for (JsonNode row :
        apiClient.searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            query)) {
      result.add(sharedBudget(row));
    }
    return List.copyOf(result);
  }

  public GoogleSharedBudgetResponse createSharedBudget(
      Long companyId,
      Long accountId,
      CreateGoogleSharedBudgetRequest request,
      Long actorUserId) {
    var context = contextService.requireManaged(companyId, accountId);
    long amountMicros =
        GoogleAdsMoney.minorToMicros(
            request.amountMinor(), context.account().getCurrency());

    Map<String, Object> create =
        Map.of(
            "name", request.name().trim(),
            "amountMicros", amountMicros,
            "deliveryMethod", "STANDARD",
            "explicitlyShared", true);

    JsonNode response =
        apiClient.mutate(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            "campaignBudgets",
            List.of(Map.of("create", create)));
    String resource = apiClient.firstMutatedResourceName(response);
    String id = idFromResource(resource);

    GoogleSharedBudgetResponse created =
        sharedBudgets(companyId, accountId, 30).stream()
            .filter(value -> id.equals(value.id()))
            .findFirst()
            .orElse(
                new GoogleSharedBudgetResponse(
                    id,
                    resource,
                    request.name().trim(),
                    "ENABLED",
                    "STANDARD",
                    amountMicros,
                    0L,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null));

    activityLogService.record(
        companyId,
        accountId,
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_SHARED_BUDGET_CREATED",
        "CAMPAIGN_BUDGET",
        id,
        null,
        created,
        Map.of("amountMicros", amountMicros));

    return created;
  }

  public GoogleCampaignPortfolioAssignmentResponse assignment(
      Long companyId, Long campaignId) {
    AdCampaign campaign = requireGoogleCampaign(companyId, campaignId);
    var context = contextService.requireManaged(companyId, campaign.getAdAccountId());
    String externalId = numeric(campaign.getExternalCampaignId(), "Google campaign ID");

    List<JsonNode> rows =
        apiClient.searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            "SELECT campaign.id, campaign.bidding_strategy, "
                + "campaign.bidding_strategy_type, campaign.campaign_budget "
                + "FROM campaign WHERE campaign.id = "
                + externalId
                + " LIMIT 1");
    if (rows.isEmpty()) {
      throw new NotFoundException("Google campaign could not be reloaded");
    }
    JsonNode provider = rows.getFirst().path("campaign");
    return new GoogleCampaignPortfolioAssignmentResponse(
        externalId,
        text(provider, "biddingStrategy"),
        text(provider, "campaignBudget"),
        text(provider, "biddingStrategyType"),
        compatible(campaign));
  }

  public GoogleCampaignPortfolioAssignmentResponse assign(
      Long companyId,
      Long campaignId,
      GoogleCampaignPortfolioAssignmentRequest request,
      Long actorUserId) {
    AdCampaign campaign = requireGoogleCampaign(companyId, campaignId);
    requireCompatible(campaign);
    var context = contextService.requireManaged(companyId, campaign.getAdAccountId());
    String customerId = context.account().getExternalAccountId();

    String strategyId = optionalNumeric(request.biddingStrategyId(), "Google bidding strategy ID");
    String budgetId = optionalNumeric(request.sharedBudgetId(), "Google shared budget ID");
    if (strategyId == null && budgetId == null) {
      throw new BadRequestException(
          "Choose a portfolio bidding strategy, a shared budget, or both");
    }

    GooglePortfolioBiddingStrategyResponse strategy =
        strategyId == null
            ? null
            : strategies(companyId, campaign.getAdAccountId(), 30).stream()
                .filter(value -> strategyId.equals(value.id()))
                .findFirst()
                .orElseThrow(
                    () -> new BadRequestException("Google portfolio bidding strategy is unavailable"));
    GoogleSharedBudgetResponse budget =
        budgetId == null
            ? null
            : sharedBudgets(companyId, campaign.getAdAccountId(), 30).stream()
                .filter(value -> budgetId.equals(value.id()))
                .findFirst()
                .orElseThrow(
                    () -> new BadRequestException("Google shared budget is unavailable"));

    if (strategy != null && strategy.alignedCampaignBudgetId() != null) {
      if (budget == null || !strategy.alignedCampaignBudgetId().equals(budget.id())) {
        throw new BadRequestException(
            "This portfolio bidding strategy is aligned to shared budget "
                + strategy.alignedCampaignBudgetId()
                + "; assign that aligned budget at the same time");
      }
    }
    if (budget != null && budget.alignedBiddingStrategyId() != null) {
      if (strategy == null || !budget.alignedBiddingStrategyId().equals(strategy.id())) {
        throw new BadRequestException(
            "This shared budget is aligned to portfolio bidding strategy "
                + budget.alignedBiddingStrategyId()
                + "; assign that aligned strategy at the same time");
      }
    }

    Map<String, Object> update = new LinkedHashMap<>();
    update.put(
        "resourceName",
        apiClient.customerResourceName(
            customerId,
            "campaigns",
            numeric(campaign.getExternalCampaignId(), "Google campaign ID")));
    List<String> masks = new ArrayList<>();
    if (strategy != null) {
      update.put("biddingStrategy", strategy.resourceName());
      masks.add("bidding_strategy");
    }
    if (budget != null) {
      update.put("campaignBudget", budget.resourceName());
      masks.add("campaign_budget");
    }

    GoogleCampaignPortfolioAssignmentResponse before = assignment(companyId, campaignId);
    apiClient.mutate(
        context.accessToken(),
        customerId,
        context.loginCustomerId(),
        "campaigns",
        List.of(
            Map.of(
                "update", update,
                "updateMask", String.join(",", masks))));
    GoogleCampaignPortfolioAssignmentResponse after = assignment(companyId, campaignId);

    activityLogService.record(
        companyId,
        campaign.getAdAccountId(),
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_CAMPAIGN_PORTFOLIO_ASSIGNMENT_UPDATED",
        "CAMPAIGN",
        campaign.getExternalCampaignId(),
        before,
        after,
        Map.of(
            "strategyId", strategyId == null ? "" : strategyId,
            "budgetId", budgetId == null ? "" : budgetId));

    return after;
  }

  public void align(
      Long companyId,
      Long accountId,
      GooglePortfolioAlignmentRequest request,
      Long actorUserId) {
    var context = contextService.requireManaged(companyId, accountId);
    String strategyId = numeric(request.biddingStrategyId(), "Google bidding strategy ID");
    String budgetId = numeric(request.sharedBudgetId(), "Google shared budget ID");

    GooglePortfolioBiddingStrategyResponse strategy =
        strategies(companyId, accountId, 30).stream()
            .filter(value -> strategyId.equals(value.id()))
            .findFirst()
            .orElseThrow(
                () -> new BadRequestException("Google portfolio bidding strategy is unavailable"));
    GoogleSharedBudgetResponse budget =
        sharedBudgets(companyId, accountId, 30).stream()
            .filter(value -> budgetId.equals(value.id()))
            .findFirst()
            .orElseThrow(
                () -> new BadRequestException("Google shared budget is unavailable"));

    if (strategy.alignedCampaignBudgetId() != null
        && !budgetId.equals(strategy.alignedCampaignBudgetId())) {
      throw new BadRequestException(
          "Portfolio bidding strategy is already aligned to a different shared budget");
    }
    if (budget.alignedBiddingStrategyId() != null
        && !strategyId.equals(budget.alignedBiddingStrategyId())) {
      throw new BadRequestException(
          "Shared budget is already aligned to a different portfolio bidding strategy");
    }

    String customerId = context.account().getExternalAccountId();
    String strategyResource =
        apiClient.customerResourceName(customerId, "biddingStrategies", strategyId);
    String budgetResource =
        apiClient.customerResourceName(customerId, "campaignBudgets", budgetId);

    Set<String> strategyCampaigns =
        campaignIds(context, "campaign.bidding_strategy = '" + strategyResource + "'");
    Set<String> budgetCampaigns =
        campaignIds(context, "campaign.campaign_budget = '" + budgetResource + "'");
    if (!strategyCampaigns.equals(budgetCampaigns)) {
      throw new BadRequestException(
          "Google requires a shared budget and portfolio strategy to be attached to the exact same campaign set before alignment");
    }

    List<Map<String, Object>> operations =
        List.of(
            Map.of(
                "biddingStrategyOperation",
                Map.of(
                    "update",
                    Map.of(
                        "resourceName", strategyResource,
                        "alignedCampaignBudgetId", Long.valueOf(budgetId)),
                    "updateMask", "aligned_campaign_budget_id")),
            Map.of(
                "campaignBudgetOperation",
                Map.of(
                    "update",
                    Map.of(
                        "resourceName", budgetResource,
                        "alignedBiddingStrategyId", Long.valueOf(strategyId)),
                    "updateMask", "aligned_bidding_strategy_id")));

    apiClient.mutateAll(
        context.accessToken(),
        customerId,
        context.loginCustomerId(),
        operations);

    activityLogService.record(
        companyId,
        accountId,
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_PORTFOLIO_BUDGET_ALIGNED",
        "BIDDING_STRATEGY",
        strategyId,
        null,
        Map.of("strategyId", strategyId, "budgetId", budgetId),
        Map.of("campaignCount", strategyCampaigns.size()));
  }

  private Set<String> campaignIds(
      GoogleAdsAccountContextService.Context context, String predicate) {
    List<JsonNode> rows =
        apiClient.searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            "SELECT campaign.id FROM campaign WHERE campaign.status != 'REMOVED' AND "
                + predicate);
    Set<String> result = new HashSet<>();
    for (JsonNode row : rows) {
      String id = text(row.path("campaign"), "id");
      if (id != null) result.add(id);
    }
    return Set.copyOf(result);
  }

  private GooglePortfolioBiddingStrategyResponse strategy(JsonNode row) {
    JsonNode value = row.path("biddingStrategy");
    String type = text(value, "type");
    Long targetCpa =
        "TARGET_CPA".equals(type)
            ? longOrNull(value.path("targetCpa"), "targetCpaMicros")
            : longOrNull(value.path("maximizeConversions"), "targetCpaMicros");
    Double targetRoas =
        "TARGET_ROAS".equals(type)
            ? doubleOrNull(value.path("targetRoas"), "targetRoas")
            : doubleOrNull(value.path("maximizeConversionValue"), "targetRoas");
    JsonNode metrics = row.path("metrics");
    return new GooglePortfolioBiddingStrategyResponse(
        text(value, "id"),
        text(value, "resourceName"),
        text(value, "name"),
        type,
        text(value, "status"),
        longOrNull(value, "campaignCount"),
        longOrNull(value, "nonRemovedCampaignCount"),
        text(value, "alignedCampaignBudgetId"),
        targetCpa,
        targetRoas,
        longOrNull(metrics, "impressions"),
        longOrNull(metrics, "clicks"),
        doubleOrNull(metrics, "conversions"),
        doubleOrNull(metrics, "conversionsValue"),
        longOrNull(metrics, "costMicros"));
  }

  private GoogleSharedBudgetResponse sharedBudget(JsonNode row) {
    JsonNode value = row.path("campaignBudget");
    JsonNode metrics = row.path("metrics");
    return new GoogleSharedBudgetResponse(
        text(value, "id"),
        text(value, "resourceName"),
        text(value, "name"),
        text(value, "status"),
        text(value, "deliveryMethod"),
        longOrNull(value, "amountMicros"),
        longOrNull(value, "referenceCount"),
        text(value, "alignedBiddingStrategyId"),
        longOrNull(metrics, "impressions"),
        longOrNull(metrics, "clicks"),
        doubleOrNull(metrics, "conversions"),
        doubleOrNull(metrics, "conversionsValue"),
        longOrNull(metrics, "costMicros"));
  }

  private void validateTargets(String type, long targetCpaMicros, Double targetRoas) {
    if ("TARGET_CPA".equals(type) && targetCpaMicros <= 0) {
      throw new BadRequestException("Target CPA portfolio strategy requires a positive target CPA");
    }
    if ("TARGET_ROAS".equals(type)
        && (targetRoas == null || targetRoas < 0.01d || targetRoas > 1000d)) {
      throw new BadRequestException(
          "Target ROAS portfolio strategy requires a target ROAS between 0.01 and 1000");
    }
    if (targetRoas != null
        && !"TARGET_ROAS".equals(type)
        && !"MAXIMIZE_CONVERSION_VALUE".equals(type)) {
      throw new BadRequestException("Target ROAS is not valid for this portfolio strategy");
    }
    if (targetCpaMicros > 0
        && !"TARGET_CPA".equals(type)
        && !"MAXIMIZE_CONVERSIONS".equals(type)) {
      throw new BadRequestException("Target CPA is not valid for this portfolio strategy");
    }
  }

  private String strategyType(String value) {
    String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    if (!STRATEGY_TYPES.contains(normalized)) {
      throw new BadRequestException(
          "Google portfolio strategy must be TARGET_CPA, TARGET_ROAS, MAXIMIZE_CONVERSIONS, or MAXIMIZE_CONVERSION_VALUE");
    }
    return normalized;
  }

  private AdCampaign requireGoogleCampaign(Long companyId, Long campaignId) {
    AdCampaign campaign =
        campaignRepository
            .findByIdAndCompanyId(campaignId, companyId)
            .orElseThrow(() -> new NotFoundException("Advertising campaign not found"));
    if (campaign.getProvider() != AdProvider.GOOGLE_ADS || !campaign.isAccessible()) {
      throw new BadRequestException("Google campaign is not accessible");
    }
    return campaign;
  }

  private void requireCompatible(AdCampaign campaign) {
    if (!compatible(campaign)) {
      throw new BadRequestException(
          "Portfolio bidding and shared budgets are supported in UnifyDesk only for Google Search, Display, and Shopping campaigns; Performance Max, Demand Gen, and App campaigns are not compatible");
    }
  }

  private boolean compatible(AdCampaign campaign) {
    return campaign.getObjective() != null
        && COMPATIBLE_CAMPAIGN_TYPES.contains(
            campaign.getObjective().trim().toUpperCase(Locale.ROOT));
  }

  private DateRange range(Integer requestedDays) {
    int days = requestedDays == null ? 30 : Math.max(1, Math.min(requestedDays, 90));
    LocalDate end = LocalDate.now();
    return new DateRange(end.minusDays(days - 1L), end);
  }

  private String optionalNumeric(String value, String label) {
    if (value == null || value.isBlank()) return null;
    return numeric(value, label);
  }

  private String numeric(String value, String label) {
    if (value == null || !value.trim().matches("\\d+")) {
      throw new BadRequestException("Invalid " + label);
    }
    return value.trim();
  }

  private String idFromResource(String value) {
    if (value == null || value.isBlank()) return null;
    int slash = value.lastIndexOf('/');
    return slash < 0 ? value : value.substring(slash + 1);
  }

  private String text(JsonNode node, String field) {
    if (node == null || node.isNull() || node.isMissingNode()) return null;
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) return null;
    String result = value.asText(null);
    return result == null || result.isBlank() ? null : result;
  }

  private Long longOrNull(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) return null;
    if (value.isIntegralNumber()) return value.longValue();
    try {
      return new BigDecimal(value.asText()).longValue();
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private Double doubleOrNull(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) return null;
    if (value.isNumber()) return value.doubleValue();
    try {
      return new BigDecimal(value.asText()).doubleValue();
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private record DateRange(LocalDate start, LocalDate end) {}
}
