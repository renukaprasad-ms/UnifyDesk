package com.messaging.ads.optimization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.dto.AdAnalyticsResponse;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.enums.AdInsightLevel;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.GoogleAdsCampaignMutationService;
import com.messaging.ads.meta.MetaAdsCampaignMutationService;
import com.messaging.ads.optimization.dto.AdOptimizationPolicyEvaluation;
import com.messaging.ads.optimization.dto.AdOptimizationPolicyRequest;
import com.messaging.ads.optimization.dto.AdOptimizationPolicyResponse;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.ads.service.AdActivityLogService;
import com.messaging.ads.service.AdAnalyticsService;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdOptimizationPolicyService {

  private static final List<String> POLICY_TYPES =
      List.of("AUTOMATION_RULE", "LIFECYCLE_RETARGETING");
  private static final List<String> MODES =
      List.of("RECOMMENDATION_ONLY", "APPROVAL_REQUIRED", "AUTOMATIC");
  private static final List<String> RULE_ACTIONS =
      List.of(
          "NOTIFY_ONLY",
          "PAUSE_CAMPAIGN",
          "ENABLE_CAMPAIGN",
          "RECOMMEND_BUDGET_CHANGE",
          "RECOMMEND_NEGATIVE_KEYWORD",
          "RECOMMEND_CREATIVE_REFRESH");

  private final AdOptimizationPolicyRepository repository;
  private final AdAccountRepository accountRepository;
  private final AdCampaignRepository campaignRepository;
  private final AdAnalyticsService analyticsService;
  private final GoogleAdsCampaignMutationService googleCampaignMutationService;
  private final MetaAdsCampaignMutationService metaCampaignMutationService;
  private final AdActivityLogService activityLogService;
  private final ObjectMapper objectMapper;

  @Transactional(readOnly = true)
  public List<AdOptimizationPolicyResponse> list(Long companyId) {
    return repository.findByCompanyIdOrderByUpdatedAtDesc(companyId).stream()
        .map(this::response)
        .toList();
  }

  @Transactional
  public AdOptimizationPolicyResponse create(
      Long companyId, Long actorUserId, AdOptimizationPolicyRequest request) {
    AdOptimizationPolicy policy = new AdOptimizationPolicy();
    policy.setCompanyId(companyId);
    policy.setCreatedByUserId(actorUserId);
    apply(policy, request);
    validateScope(policy);
    validateConfig(policy);
    return response(repository.save(policy));
  }

  @Transactional
  public AdOptimizationPolicyResponse update(
      Long companyId, Long policyId, AdOptimizationPolicyRequest request) {
    AdOptimizationPolicy policy =
        repository
            .findByIdAndCompanyId(policyId, companyId)
            .orElseThrow(() -> new NotFoundException("Ad optimization policy not found"));
    apply(policy, request);
    validateScope(policy);
    validateConfig(policy);
    policy.setStatus(policy.isEnabled() ? "ACTIVE" : "DISABLED");
    return response(repository.save(policy));
  }

  @Transactional
  public AdOptimizationPolicyResponse setEnabled(Long companyId, Long policyId, boolean enabled) {
    AdOptimizationPolicy policy =
        repository
            .findByIdAndCompanyId(policyId, companyId)
            .orElseThrow(() -> new NotFoundException("Ad optimization policy not found"));
    policy.setEnabled(enabled);
    policy.setStatus(enabled ? "ACTIVE" : "DISABLED");
    return response(repository.save(policy));
  }

  @Transactional
  public AdOptimizationPolicyEvaluation evaluate(Long companyId, Long policyId, Long actorUserId) {
    AdOptimizationPolicy policy =
        repository
            .findByIdAndCompanyId(policyId, companyId)
            .orElseThrow(() -> new NotFoundException("Ad optimization policy not found"));
    return evaluate(policy, actorUserId, false);
  }

  @Transactional
  public AdOptimizationPolicyEvaluation approve(Long companyId, Long policyId, Long actorUserId) {
    AdOptimizationPolicy policy =
        repository
            .findByIdAndCompanyId(policyId, companyId)
            .orElseThrow(() -> new NotFoundException("Ad optimization policy not found"));
    if (!"PENDING_APPROVAL".equals(policy.getStatus())) {
      throw new BadRequestException("Optimization policy has no pending approval");
    }
    JsonNode config = json(policy.getConfigJson());
    String action = upper(config, "action", "NOTIFY_ONLY");
    if (!"PAUSE_CAMPAIGN".equals(action) && !"ENABLE_CAMPAIGN".equals(action)) {
      policy.setStatus("APPROVED_RECOMMENDATION");
      policy.setLastResultJson(
          jsonString(
              java.util.Map.of(
                  "approved",
                  true,
                  "action",
                  action,
                  "message",
                  "Recommendation approved; apply the suggested change from the relevant management surface.")));
      repository.save(policy);
      return new AdOptimizationPolicyEvaluation(
          policy.getId().toString(),
          true,
          "APPROVED_RECOMMENDATION",
          upper(config, "metric", null),
          null,
          decimal(config, "threshold"),
          action,
          false,
          false,
          "Recommendation approved for manual application.",
          Instant.now());
    }
    executeStatusAction(policy, action, actorUserId);
    policy.setStatus("ACTIVE");
    policy.setLastTriggeredAt(Instant.now());
    repository.save(policy);
    return new AdOptimizationPolicyEvaluation(
        policy.getId().toString(),
        true,
        "EXECUTED",
        upper(config, "metric", null),
        null,
        decimal(config, "threshold"),
        action,
        true,
        false,
        "Approved provider status action executed.",
        Instant.now());
  }

  @Transactional
  public int evaluateEnabledAutomatically() {
    int evaluated = 0;
    for (AdOptimizationPolicy policy : repository.findByEnabledTrueOrderByIdAsc()) {
      try {
        evaluate(policy, policy.getCreatedByUserId(), true);
      } catch (RuntimeException ignored) {
        // Each policy is isolated; the persisted result is updated on successful evaluations.
      }
      evaluated++;
    }
    return evaluated;
  }

  private AdOptimizationPolicyEvaluation evaluate(
      AdOptimizationPolicy policy, Long actorUserId, boolean scheduled) {
    if (!policy.isEnabled()) {
      return result(
          policy, false, "DISABLED", null, null, null, false, false, "Policy is disabled.");
    }
    JsonNode config = json(policy.getConfigJson());
    if ("LIFECYCLE_RETARGETING".equals(policy.getPolicyType())) {
      return evaluateLifecycle(policy, config);
    }

    Scope scope = resolveScope(policy);
    int lookbackDays = integer(config, "lookbackDays", 7, 1, 90);
    LocalDate to = LocalDate.now();
    LocalDate from = to.minusDays(lookbackDays - 1L);
    AdAnalyticsResponse analytics =
        analyticsService.analytics(
            policy.getCompanyId(), scope.account().getId(), from, to, AdInsightLevel.CAMPAIGN);

    AdAnalyticsResponse.Summary summary = analytics.summary();
    AdAnalyticsResponse.EntityRow row = null;
    if (scope.campaign() != null) {
      row =
          analytics.entities().stream()
              .filter(value -> scope.campaign().getId().toString().equals(value.entityId()))
              .findFirst()
              .orElse(null);
      if (row == null) {
        return persistResult(
            policy,
            result(
                policy,
                false,
                "NO_ANALYTICS",
                upper(config, "metric", null),
                null,
                decimal(config, "threshold"),
                false,
                false,
                "No synchronized campaign analytics are available for this range."));
      }
    }

    BigDecimal conversions = row == null ? summary.conversions() : row.conversions();
    BigDecimal minimumConversions =
        decimal(config, "minimumConversions") == null
            ? BigDecimal.ZERO
            : decimal(config, "minimumConversions");
    if (conversions.compareTo(minimumConversions) < 0) {
      return persistResult(
          policy,
          result(
              policy,
              false,
              "INSUFFICIENT_DATA",
              upper(config, "metric", null),
              conversions,
              minimumConversions,
              false,
              false,
              "Minimum conversion sample has not been reached."));
    }

    String metric = upper(config, "metric", "SPEND");
    BigDecimal observed = metricValue(metric, summary, row);
    BigDecimal threshold = decimal(config, "threshold");
    if (threshold == null) {
      throw new BadRequestException("Automation rule threshold is required");
    }
    String operator = upper(config, "operator", "GT");
    boolean triggered = compare(observed, threshold, operator);
    String action = upper(config, "action", "NOTIFY_ONLY");
    Instant now = Instant.now();

    if (!triggered) {
      policy.setStatus("ACTIVE");
      return persistResult(
          policy,
          result(
              policy,
              false,
              "NOT_TRIGGERED",
              metric,
              observed,
              threshold,
              false,
              false,
              "Rule condition is not currently met."));
    }

    int cooldownHours = integer(config, "cooldownHours", 24, 0, 720);
    if (policy.getLastTriggeredAt() != null
        && Duration.between(policy.getLastTriggeredAt(), now).toHours() < cooldownHours) {
      return persistResult(
          policy,
          result(
              policy,
              true,
              "COOLDOWN",
              metric,
              observed,
              threshold,
              false,
              false,
              "Rule matched, but the configured cooldown is still active."));
    }

    boolean executed = false;
    boolean approvalRequired = false;
    String state;
    String message;
    if ("AUTOMATIC".equals(policy.getMode())) {
      if (!"PAUSE_CAMPAIGN".equals(action) && !"ENABLE_CAMPAIGN".equals(action)) {
        state = "RECOMMENDED";
        message =
            "Automatic mode is restricted to reversible campaign status actions; this action remains advisory.";
      } else {
        executeStatusAction(policy, action, actorUserId);
        executed = true;
        state = "EXECUTED";
        message = "Automatic campaign status action executed within the rule guardrails.";
      }
    } else if ("APPROVAL_REQUIRED".equals(policy.getMode())) {
      policy.setStatus("PENDING_APPROVAL");
      approvalRequired = true;
      state = "PENDING_APPROVAL";
      message = "Rule matched and is waiting for explicit approval.";
    } else {
      state = "RECOMMENDED";
      message = "Rule matched and produced a recommendation only.";
    }

    policy.setLastTriggeredAt(now);
    audit(policy, scope, actorUserId, action, observed, threshold, state, scheduled);
    return persistResult(
        policy,
        result(
            policy, true, state, metric, observed, threshold, executed, approvalRequired, message));
  }

  private AdOptimizationPolicyEvaluation evaluateLifecycle(
      AdOptimizationPolicy policy, JsonNode config) {
    Scope scope = resolveScope(policy);
    int lookbackDays = integer(config, "lookbackDays", 30, 1, 365);
    LocalDate to = LocalDate.now();
    LocalDate from = to.minusDays(lookbackDays - 1L);
    AdAnalyticsResponse analytics =
        analyticsService.analytics(
            policy.getCompanyId(), scope.account().getId(), from, to, AdInsightLevel.CAMPAIGN);
    AdAnalyticsResponse.EntityRow row =
        scope.campaign() == null
            ? null
            : analytics.entities().stream()
                .filter(value -> scope.campaign().getId().toString().equals(value.entityId()))
                .findFirst()
                .orElse(null);

    String segmentType = upper(config, "segmentType", "CLICKED_NOT_CONVERTED");
    BigDecimal candidates;
    String message;
    if ("CLICKED_NOT_CONVERTED".equals(segmentType)) {
      long clicks = row == null ? analytics.summary().clicks() : row.clicks();
      BigDecimal conversions = row == null ? analytics.summary().conversions() : row.conversions();
      candidates = BigDecimal.valueOf(clicks).subtract(conversions).max(BigDecimal.ZERO);
      message =
          "Aggregate click-minus-conversion evidence indicates retargeting demand. Use the first-party audience builder to upload consented identities; UnifyDesk does not infer identities from aggregate ad metrics.";
    } else if ("LEAD_NOT_PURCHASED".equals(segmentType)) {
      BigDecimal leads = row == null ? analytics.summary().leads() : row.leads();
      BigDecimal purchases = row == null ? analytics.summary().purchases() : row.purchases();
      candidates = leads.subtract(purchases).max(BigDecimal.ZERO);
      message =
          "Aggregate lead-minus-purchase evidence indicates a follow-up segment. Provider audience refresh still requires consented first-party identifiers.";
    } else {
      candidates = BigDecimal.ZERO;
      message =
          "This lifecycle segment requires person-level CRM or messaging events. The policy is persisted and scheduled, but UnifyDesk will not fabricate membership from aggregate provider analytics.";
    }

    BigDecimal threshold =
        decimal(config, "minimumCandidateCount") == null
            ? BigDecimal.ONE
            : decimal(config, "minimumCandidateCount");
    boolean triggered = candidates.compareTo(threshold) >= 0;
    String state = triggered ? "AUDIENCE_REFRESH_RECOMMENDED" : "WAITING_FOR_SIGNAL";
    if (!"CLICKED_NOT_CONVERTED".equals(segmentType) && !"LEAD_NOT_PURCHASED".equals(segmentType)) {
      state = "NEEDS_FIRST_PARTY_DATA";
      triggered = false;
    }
    policy.setStatus("ACTIVE");
    return persistResult(
        policy,
        result(
            policy, triggered, state, segmentType, candidates, threshold, false, false, message));
  }

  private void executeStatusAction(AdOptimizationPolicy policy, String action, Long actorUserId) {
    if (policy.getCampaignId() == null) {
      throw new BadRequestException(
          "Automatic campaign status actions require a campaign-scoped rule");
    }
    AdCampaign campaign =
        campaignRepository
            .findByIdAndCompanyId(policy.getCampaignId(), policy.getCompanyId())
            .orElseThrow(() -> new NotFoundException("Advertising campaign not found"));
    String status = "PAUSE_CAMPAIGN".equals(action) ? "PAUSED" : "ACTIVE";
    if (campaign.getProvider() == AdProvider.GOOGLE_ADS) {
      googleCampaignMutationService.setStatus(
          policy.getCompanyId(), campaign.getId(), status, actorUserId);
    } else {
      metaCampaignMutationService.setStatus(policy.getCompanyId(), campaign.getId(), status);
    }
  }

  private Scope resolveScope(AdOptimizationPolicy policy) {
    AdCampaign campaign = null;
    AdAccount account;
    if (policy.getCampaignId() != null) {
      campaign =
          campaignRepository
              .findByIdAndCompanyId(policy.getCampaignId(), policy.getCompanyId())
              .orElseThrow(() -> new NotFoundException("Advertising campaign not found"));
      account =
          accountRepository
              .findByIdAndCompanyId(campaign.getAdAccountId(), policy.getCompanyId())
              .orElseThrow(() -> new NotFoundException("Ad account not found"));
    } else if (policy.getAdAccountId() != null) {
      account =
          accountRepository
              .findByIdAndCompanyId(policy.getAdAccountId(), policy.getCompanyId())
              .orElseThrow(() -> new NotFoundException("Ad account not found"));
    } else {
      throw new BadRequestException("Optimization policy requires an ad account or campaign scope");
    }
    if (!account.isManaged() || !account.isAccessible()) {
      throw new BadRequestException(
          "Optimization policies require a managed, accessible ad account");
    }
    return new Scope(account, campaign);
  }

  private void validateScope(AdOptimizationPolicy policy) {
    Scope scope = resolveScope(policy);
    if (policy.getProvider() != null && policy.getProvider() != scope.account().getProvider()) {
      throw new BadRequestException(
          "Optimization policy provider does not match its account/campaign scope");
    }
    policy.setProvider(scope.account().getProvider());
    policy.setAdAccountId(scope.account().getId());
  }

  private void validateConfig(AdOptimizationPolicy policy) {
    JsonNode config = json(policy.getConfigJson());
    if ("AUTOMATION_RULE".equals(policy.getPolicyType())) {
      String metric = upper(config, "metric", null);
      if (metric == null
          || !List.of("SPEND", "CPA", "ROAS", "CONVERSIONS", "CTR").contains(metric)) {
        throw new BadRequestException(
            "Automation metric must be SPEND, CPA, ROAS, CONVERSIONS, or CTR");
      }
      if (decimal(config, "threshold") == null) {
        throw new BadRequestException("Automation rule threshold is required");
      }
      String operator = upper(config, "operator", "GT");
      if (!List.of("GT", "GTE", "LT", "LTE", "EQ").contains(operator)) {
        throw new BadRequestException("Unsupported automation rule operator");
      }
      String action = upper(config, "action", "NOTIFY_ONLY");
      if (!RULE_ACTIONS.contains(action)) {
        throw new BadRequestException("Unsupported automation rule action");
      }
      if ("AUTOMATIC".equals(policy.getMode())
          && !"PAUSE_CAMPAIGN".equals(action)
          && !"ENABLE_CAMPAIGN".equals(action)) {
        throw new BadRequestException(
            "Automatic mode is limited to reversible PAUSE_CAMPAIGN or ENABLE_CAMPAIGN actions");
      }
      if (("PAUSE_CAMPAIGN".equals(action) || "ENABLE_CAMPAIGN".equals(action))
          && policy.getCampaignId() == null) {
        throw new BadRequestException("Campaign status rules must be scoped to one campaign");
      }
      integer(config, "lookbackDays", 7, 1, 90);
      integer(config, "cooldownHours", 24, 0, 720);
      BigDecimal movement = decimal(config, "maxDailyBudgetMovementPercent");
      if (movement != null
          && (movement.signum() < 0 || movement.compareTo(new BigDecimal("50")) > 0)) {
        throw new BadRequestException(
            "Daily budget movement guardrail must be between 0 and 50 percent");
      }
    } else {
      String segment = upper(config, "segmentType", null);
      if (segment == null
          || !List.of(
                  "CLICKED_NOT_CONVERTED",
                  "LEAD_NOT_PURCHASED",
                  "INACTIVE_CUSTOMER",
                  "HIGH_VALUE_CUSTOMER",
                  "MESSAGING_RESPONDER")
              .contains(segment)) {
        throw new BadRequestException("Unsupported lifecycle retargeting segment");
      }
      integer(config, "lookbackDays", 30, 1, 365);
    }
  }

  private void apply(AdOptimizationPolicy policy, AdOptimizationPolicyRequest request) {
    String type = request.policyType().trim().toUpperCase(Locale.ROOT);
    String mode = request.mode().trim().toUpperCase(Locale.ROOT);
    if (!POLICY_TYPES.contains(type)) {
      throw new BadRequestException("Unsupported optimization policy type");
    }
    if (!MODES.contains(mode)) {
      throw new BadRequestException("Unsupported optimization policy mode");
    }
    json(request.configJson());
    policy.setName(request.name().trim());
    policy.setPolicyType(type);
    policy.setMode(mode);
    policy.setProvider(request.provider());
    policy.setAdAccountId(request.adAccountId());
    policy.setCampaignId(request.campaignId());
    policy.setConfigJson(request.configJson().trim());
    policy.setEnabled(request.enabled() == null || request.enabled());
    policy.setStatus(policy.isEnabled() ? "ACTIVE" : "DISABLED");
  }

  private BigDecimal metricValue(
      String metric, AdAnalyticsResponse.Summary summary, AdAnalyticsResponse.EntityRow row) {
    return switch (metric) {
      case "SPEND" -> row == null ? summary.spend() : row.spend();
      case "CPA" -> nz(row == null ? summary.cpa() : row.cpa());
      case "ROAS" -> nz(row == null ? summary.roas() : row.roas());
      case "CONVERSIONS" -> row == null ? summary.conversions() : row.conversions();
      case "CTR" -> nz(row == null ? summary.ctr() : row.ctr());
      default -> throw new BadRequestException("Unsupported automation metric");
    };
  }

  private boolean compare(BigDecimal observed, BigDecimal threshold, String operator) {
    int comparison = nz(observed).compareTo(threshold);
    return switch (operator) {
      case "GT" -> comparison > 0;
      case "GTE" -> comparison >= 0;
      case "LT" -> comparison < 0;
      case "LTE" -> comparison <= 0;
      case "EQ" -> comparison == 0;
      default -> false;
    };
  }

  private void audit(
      AdOptimizationPolicy policy,
      Scope scope,
      Long actorUserId,
      String action,
      BigDecimal observed,
      BigDecimal threshold,
      String state,
      boolean scheduled) {
    activityLogService.record(
        policy.getCompanyId(),
        scope.account().getId(),
        scope.account().getProvider(),
        actorUserId,
        "AD_AUTOMATION_RULE_TRIGGERED",
        "OPTIMIZATION_POLICY",
        policy.getId().toString(),
        null,
        null,
        java.util.Map.of(
            "policyName", policy.getName(),
            "mode", policy.getMode(),
            "action", action,
            "observed", observed,
            "threshold", threshold,
            "state", state,
            "scheduledEvaluation", scheduled));
  }

  private AdOptimizationPolicyEvaluation persistResult(
      AdOptimizationPolicy policy, AdOptimizationPolicyEvaluation evaluation) {
    policy.setLastEvaluatedAt(evaluation.evaluatedAt());
    policy.setLastResultJson(jsonString(evaluation));
    if (!"PENDING_APPROVAL".equals(policy.getStatus())) {
      policy.setStatus(
          policy.isEnabled()
              ? ("EXECUTED".equals(evaluation.state()) ? "ACTIVE" : policy.getStatus())
              : "DISABLED");
    }
    repository.save(policy);
    return evaluation;
  }

  private AdOptimizationPolicyEvaluation result(
      AdOptimizationPolicy policy,
      boolean triggered,
      String state,
      String metric,
      BigDecimal observed,
      BigDecimal threshold,
      boolean executed,
      boolean approvalRequired,
      String message) {
    return new AdOptimizationPolicyEvaluation(
        policy.getId().toString(),
        triggered,
        state,
        metric,
        observed,
        threshold,
        json(policy.getConfigJson()).path("action").asText("NOTIFY_ONLY"),
        executed,
        approvalRequired,
        message,
        Instant.now());
  }

  private AdOptimizationPolicyResponse response(AdOptimizationPolicy policy) {
    return new AdOptimizationPolicyResponse(
        policy.getId().toString(),
        policy.getName(),
        policy.getPolicyType(),
        policy.isEnabled(),
        policy.getMode(),
        policy.getProvider(),
        policy.getAdAccountId() == null ? null : policy.getAdAccountId().toString(),
        policy.getCampaignId() == null ? null : policy.getCampaignId().toString(),
        policy.getConfigJson(),
        policy.getStatus(),
        policy.getLastEvaluatedAt(),
        policy.getLastTriggeredAt(),
        policy.getLastResultJson(),
        policy.getCreatedAt(),
        policy.getUpdatedAt());
  }

  private JsonNode json(String value) {
    try {
      JsonNode node = objectMapper.readTree(value);
      if (node == null || !node.isObject()) {
        throw new BadRequestException("Optimization policy configuration must be a JSON object");
      }
      return node;
    } catch (JsonProcessingException exception) {
      throw new BadRequestException("Optimization policy configuration must be valid JSON");
    }
  }

  private String jsonString(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize optimization policy result", exception);
    }
  }

  private String upper(JsonNode node, String field, String fallback) {
    String value = node.path(field).asText(null);
    return value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
  }

  private BigDecimal decimal(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) return null;
    try {
      return new BigDecimal(value.asText());
    } catch (NumberFormatException exception) {
      throw new BadRequestException(field + " must be numeric");
    }
  }

  private int integer(JsonNode node, String field, int fallback, int min, int max) {
    int value = node.has(field) ? node.path(field).asInt(fallback) : fallback;
    if (value < min || value > max) {
      throw new BadRequestException(field + " must be between " + min + " and " + max);
    }
    return value;
  }

  private BigDecimal nz(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }

  private record Scope(AdAccount account, AdCampaign campaign) {}
}
