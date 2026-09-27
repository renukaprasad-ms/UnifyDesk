package com.messaging.ads.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.CreateGoogleExperimentRequest;
import com.messaging.ads.google.dto.GoogleExperimentActionResponse;
import com.messaging.ads.google.dto.GoogleExperimentResponse;
import com.messaging.ads.google.dto.GoogleExperimentResponse.Arm;
import com.messaging.ads.google.dto.GoogleExperimentResponse.Metrics;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.ads.service.AdActivityLogService;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GoogleAdsExperimentService {

  private static final String SEARCH_CUSTOM = "SEARCH_CUSTOM";

  private final GoogleAdsAccountContextService contextService;
  private final GoogleAdsApiClient apiClient;
  private final AdCampaignRepository campaignRepository;
  private final AdActivityLogService activityLogService;

  public List<GoogleExperimentResponse> list(Long companyId, Long accountId) {
    var context = contextService.requireManaged(companyId, accountId);
    return list(context);
  }

  public GoogleExperimentResponse createSearchCustom(
      Long companyId,
      Long accountId,
      CreateGoogleExperimentRequest request,
      Long actorUserId) {
    var context = contextService.requireManaged(companyId, accountId);
    if (context.managerAccount()) {
      throw new BadRequestException(
          "Google Ads manager accounts cannot directly own experiments");
    }

    AdCampaign base = requireSearchCampaign(companyId, accountId, request.baseCampaignId());
    int treatmentSplit =
        request.treatmentTrafficPercent() == null ? 50 : request.treatmentTrafficPercent();
    if (treatmentSplit < 1 || treatmentSplit > 99) {
      throw new BadRequestException(
          "Google experiment treatment traffic must be between 1 and 99 percent");
    }
    validateDates(request.startDate(), request.endDate());

    String strategy = normalizeTreatmentStrategy(request.treatmentBidStrategy());
    if (strategy.equals(normalizeStoredStrategy(base.getBidStrategy()))) {
      throw new BadRequestException(
          "Choose a treatment bidding strategy that differs from the base campaign");
    }

    String suffix =
        request.suffix() == null || request.suffix().isBlank()
            ? "[UnifyDesk experiment]"
            : request.suffix().trim();

    String customerId = context.account().getExternalAccountId();
    Map<String, Object> experimentCreate = new LinkedHashMap<>();
    experimentCreate.put("name", request.name().trim());
    experimentCreate.put("type", SEARCH_CUSTOM);
    experimentCreate.put("suffix", suffix);
    experimentCreate.put("status", "SETUP");
    if (request.description() != null && !request.description().isBlank()) {
      experimentCreate.put("description", request.description().trim());
    }
    if (request.startDate() != null) {
      experimentCreate.put("startDate", request.startDate().toString());
    }
    if (request.endDate() != null) {
      experimentCreate.put("endDate", request.endDate().toString());
    }

    JsonNode experimentMutation =
        apiClient.mutate(
            context.accessToken(),
            customerId,
            context.loginCustomerId(),
            "experiments",
            List.of(Map.of("create", experimentCreate)));
    String experimentResource = apiClient.firstMutatedResourceName(experimentMutation);
    if (experimentResource == null) {
      throw new BadRequestException(
          "Google Ads created the experiment without returning its resource name");
    }

    String experimentId = idFromResource(experimentResource, "experiment");
    try {
      String baseCampaignResource =
          apiClient.customerResourceName(
              customerId, "campaigns", base.getExternalCampaignId());

      Map<String, Object> controlArm = new LinkedHashMap<>();
      controlArm.put("experiment", experimentResource);
      controlArm.put("name", "Control");
      controlArm.put("control", true);
      controlArm.put("trafficSplit", 100 - treatmentSplit);
      controlArm.put("campaigns", List.of(baseCampaignResource));

      Map<String, Object> treatmentArm = new LinkedHashMap<>();
      treatmentArm.put("experiment", experimentResource);
      treatmentArm.put("name", "Treatment");
      treatmentArm.put("control", false);
      treatmentArm.put("trafficSplit", treatmentSplit);

      JsonNode armsMutation =
          apiClient.mutateReturningMutableResource(
              context.accessToken(),
              customerId,
              context.loginCustomerId(),
              "experimentArms",
              List.of(
                  Map.of("create", controlArm),
                  Map.of("create", treatmentArm)));

      String inDesignCampaign = treatmentDraftCampaign(armsMutation);
      if (inDesignCampaign == null) {
        throw new BadRequestException(
            "Google Ads did not return the treatment draft campaign");
      }

      applyTreatmentBidStrategy(context, customerId, inDesignCampaign, strategy);

      GoogleExperimentResponse experiment =
          requireExperiment(context, experimentId);
      activityLogService.record(
          companyId,
          accountId,
          AdProvider.GOOGLE_ADS,
          actorUserId,
          "GOOGLE_EXPERIMENT_CREATED",
          "EXPERIMENT",
          experimentId,
          null,
          experiment,
          Map.of(
              "type", SEARCH_CUSTOM,
              "baseCampaignId", base.getId(),
              "treatmentTrafficPercent", treatmentSplit,
              "treatmentBidStrategy", strategy));
      return experiment;
    } catch (RuntimeException exception) {
      cleanupSetupExperiment(context, customerId, experimentResource);
      throw exception;
    }
  }

  public GoogleExperimentActionResponse schedule(
      Long companyId, Long accountId, String experimentId, Long actorUserId) {
    return action(
        companyId,
        accountId,
        experimentId,
        "SCHEDULE",
        "scheduleExperiment",
        "resourceName",
        actorUserId);
  }

  public GoogleExperimentActionResponse promote(
      Long companyId, Long accountId, String experimentId, Long actorUserId) {
    return action(
        companyId,
        accountId,
        experimentId,
        "PROMOTE",
        "promoteExperiment",
        "resourceName",
        actorUserId);
  }

  public GoogleExperimentActionResponse end(
      Long companyId, Long accountId, String experimentId, Long actorUserId) {
    return action(
        companyId,
        accountId,
        experimentId,
        "END",
        "endExperiment",
        "experiment",
        actorUserId);
  }

  private GoogleExperimentActionResponse action(
      Long companyId,
      Long accountId,
      String rawExperimentId,
      String action,
      String providerAction,
      String bodyField,
      Long actorUserId) {
    var context = contextService.requireManaged(companyId, accountId);
    String experimentId = numeric(rawExperimentId, "experiment");
    GoogleExperimentResponse before = requireExperiment(context, experimentId);
    String customerId = context.account().getExternalAccountId();
    String resourceName =
        apiClient.customerResourceName(customerId, "experiments", experimentId);

    JsonNode result =
        apiClient.customerAction(
            context.accessToken(),
            customerId,
            context.loginCustomerId(),
            "experiments/" + experimentId + ":" + providerAction,
            Map.of(bodyField, resourceName));

    String operationName = text(result, "name");
    GoogleExperimentResponse after = requireExperiment(context, experimentId);
    activityLogService.record(
        companyId,
        accountId,
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_EXPERIMENT_" + action,
        "EXPERIMENT",
        experimentId,
        before,
        after,
        Map.of("providerAction", providerAction));
    return new GoogleExperimentActionResponse(
        experimentId, action, operationName, after);
  }

  private List<GoogleExperimentResponse> list(
      GoogleAdsAccountContextService.Context context) {
    String customerId = context.account().getExternalAccountId();
    List<JsonNode> experimentRows =
        apiClient.searchRows(
            context.accessToken(),
            customerId,
            context.loginCustomerId(),
            experimentQuery());
    List<JsonNode> armRows =
        apiClient.searchRows(
            context.accessToken(),
            customerId,
            context.loginCustomerId(),
            armQuery(),
            true);

    Map<String, List<Arm>> armsByExperiment = new HashMap<>();
    for (JsonNode row : armRows) {
      JsonNode arm = row.path("experimentArm");
      String experiment = text(arm, "experiment");
      if (experiment == null) continue;
      armsByExperiment
          .computeIfAbsent(experiment, ignored -> new ArrayList<>())
          .add(
              new Arm(
                  text(arm, "resourceName"),
                  text(arm, "name"),
                  arm.path("control").asBoolean(false),
                  arm.path("trafficSplit").asLong(0L),
                  strings(arm.path("campaigns")),
                  strings(arm.path("inDesignCampaigns"))));
    }

    List<GoogleExperimentResponse> result = new ArrayList<>();
    for (JsonNode row : experimentRows) {
      JsonNode experiment = row.path("experiment");
      String resourceName = text(experiment, "resourceName");
      if (resourceName == null) continue;
      result.add(
          new GoogleExperimentResponse(
              text(experiment, "experimentId"),
              resourceName,
              text(experiment, "name"),
              text(experiment, "description"),
              text(experiment, "type"),
              text(experiment, "status"),
              text(experiment, "suffix"),
              text(experiment, "startDate"),
              text(experiment, "endDate"),
              text(experiment, "promoteStatus"),
              text(experiment, "longRunningOperation"),
              armsByExperiment.getOrDefault(resourceName, List.of()),
              metrics(row.path("metrics"))));
    }
    return List.copyOf(result);
  }

  private GoogleExperimentResponse requireExperiment(
      GoogleAdsAccountContextService.Context context, String experimentId) {
    return list(context).stream()
        .filter(value -> experimentId.equals(value.id()))
        .findFirst()
        .orElseThrow(() -> new NotFoundException("Google Ads experiment not found"));
  }

  private AdCampaign requireSearchCampaign(
      Long companyId, Long accountId, Long campaignId) {
    AdCampaign campaign =
        campaignRepository
            .findByIdAndCompanyId(campaignId, companyId)
            .orElseThrow(() -> new NotFoundException("Advertising campaign not found"));
    if (campaign.getProvider() != AdProvider.GOOGLE_ADS
        || !accountId.equals(campaign.getAdAccountId())
        || !campaign.isAccessible()) {
      throw new BadRequestException(
          "Base campaign is not available for this Google Ads account");
    }
    if (!"SEARCH".equalsIgnoreCase(campaign.getObjective())) {
      throw new BadRequestException(
          "This E1 workflow currently creates SEARCH_CUSTOM experiments from Search campaigns");
    }
    return campaign;
  }

  private void applyTreatmentBidStrategy(
      GoogleAdsAccountContextService.Context context,
      String customerId,
      String inDesignCampaign,
      String strategy) {
    Map<String, Object> update = new LinkedHashMap<>();
    update.put("resourceName", inDesignCampaign);
    String mask;
    switch (strategy) {
      case "MANUAL_CPC" -> {
        update.put("manualCpc", Map.of("enhancedCpcEnabled", false));
        mask = "manual_cpc";
      }
      case "MAXIMIZE_CLICKS" -> {
        update.put("targetSpend", Map.of());
        mask = "target_spend";
      }
      case "MAXIMIZE_CONVERSIONS" -> {
        update.put("maximizeConversions", Map.of());
        mask = "maximize_conversions";
      }
      case "MAXIMIZE_CONVERSION_VALUE" -> {
        update.put("maximizeConversionValue", Map.of());
        mask = "maximize_conversion_value";
      }
      default -> throw new BadRequestException(
          "Unsupported Google experiment treatment strategy: " + strategy);
    }

    apiClient.mutate(
        context.accessToken(),
        customerId,
        context.loginCustomerId(),
        "campaigns",
        List.of(Map.of("update", update, "updateMask", mask)));
  }

  private String treatmentDraftCampaign(JsonNode response) {
    if (response == null) return null;
    JsonNode results = response.path("results");
    if (!results.isArray()) return null;
    for (JsonNode result : results) {
      JsonNode arm = result.path("experimentArm");
      if (arm.isMissingNode() || arm.path("control").asBoolean(false)) continue;
      JsonNode drafts = arm.path("inDesignCampaigns");
      if (drafts.isArray() && !drafts.isEmpty()) {
        String resource = drafts.get(0).asText(null);
        if (resource != null && !resource.isBlank()) return resource;
      }
    }
    return null;
  }

  private void cleanupSetupExperiment(
      GoogleAdsAccountContextService.Context context,
      String customerId,
      String experimentResource) {
    try {
      apiClient.mutate(
          context.accessToken(),
          customerId,
          context.loginCustomerId(),
          "experiments",
          List.of(Map.of("remove", experimentResource)));
    } catch (RuntimeException ignored) {
      // The experiment is still SETUP/non-serving; provider sync will expose it for manual cleanup.
    }
  }

  private String experimentQuery() {
    return "SELECT experiment.resource_name, experiment.experiment_id, experiment.name, "
        + "experiment.description, experiment.type, experiment.status, experiment.suffix, "
        + "experiment.start_date, experiment.end_date, experiment.promote_status, "
        + "experiment.long_running_operation, "
        + "metrics.impressions, metrics.control_impressions, "
        + "metrics.clicks, metrics.control_clicks, "
        + "metrics.cost_micros, metrics.control_cost_micros, "
        + "metrics.conversions, metrics.control_conversions, "
        + "metrics.conversions_value, metrics.control_conversion_value, "
        + "metrics.clicks_point_estimate, metrics.clicks_margin_of_error, "
        + "metrics.clicks_p_value, "
        + "metrics.conversions_absolute_change_point_estimate, "
        + "metrics.conversions_absolute_change_margin_of_error, "
        + "metrics.conversions_absolute_change_p_value "
        + "FROM experiment WHERE experiment.status != 'REMOVED' "
        + "ORDER BY experiment.experiment_id DESC";
  }

  private String armQuery() {
    return "SELECT experiment_arm.resource_name, experiment_arm.experiment, "
        + "experiment_arm.name, experiment_arm.control, experiment_arm.traffic_split, "
        + "experiment_arm.campaigns, experiment_arm.in_design_campaigns "
        + "FROM experiment_arm";
  }

  private Metrics metrics(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return new Metrics(
          0L, 0L, 0L, 0L, 0L, 0L,
          BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
          null, null, null, null, null, null);
    }
    return new Metrics(
        longValue(node, "impressions"),
        longValue(node, "controlImpressions"),
        longValue(node, "clicks"),
        longValue(node, "controlClicks"),
        longValue(node, "costMicros"),
        longValue(node, "controlCostMicros"),
        decimal(node, "conversions"),
        decimal(node, "controlConversions"),
        decimal(node, "conversionsValue"),
        decimal(node, "controlConversionValue"),
        decimalOrNull(node, "clicksPointEstimate"),
        decimalOrNull(node, "clicksMarginOfError"),
        decimalOrNull(node, "clicksPValue"),
        decimalOrNull(node, "conversionsAbsoluteChangePointEstimate"),
        decimalOrNull(node, "conversionsAbsoluteChangeMarginOfError"),
        decimalOrNull(node, "conversionsAbsoluteChangePValue"));
  }

  private void validateDates(LocalDate startDate, LocalDate endDate) {
    if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
      throw new BadRequestException(
          "Google experiment end date must be on or after the start date");
    }
  }

  private String normalizeTreatmentStrategy(String value) {
    if (value == null || value.isBlank()) {
      throw new BadRequestException("Choose a treatment bidding strategy");
    }
    String strategy = value.trim().toUpperCase(Locale.ROOT);
    if (!List.of(
            "MANUAL_CPC",
            "MAXIMIZE_CLICKS",
            "MAXIMIZE_CONVERSIONS",
            "MAXIMIZE_CONVERSION_VALUE")
        .contains(strategy)) {
      throw new BadRequestException(
          "Unsupported Google experiment treatment bidding strategy: " + strategy);
    }
    return strategy;
  }

  private String normalizeStoredStrategy(String value) {
    if (value == null || value.isBlank()) return "";
    String normalized = value.trim().toUpperCase(Locale.ROOT);
    return "TARGET_SPEND".equals(normalized) ? "MAXIMIZE_CLICKS" : normalized;
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

  private String numeric(String value, String label) {
    if (value == null || !value.trim().matches("\\d+")) {
      throw new BadRequestException("Invalid Google " + label + " ID");
    }
    return value.trim();
  }

  private String text(JsonNode node, String field) {
    if (node == null || node.isNull()) return null;
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) return null;
    String result = value.asText(null);
    return result == null || result.isBlank() ? null : result.trim();
  }

  private List<String> strings(JsonNode node) {
    if (node == null || !node.isArray()) return List.of();
    List<String> values = new ArrayList<>();
    for (JsonNode item : node) {
      if (item.isTextual() && !item.asText().isBlank()) {
        values.add(item.asText().trim());
      }
    }
    return List.copyOf(values);
  }

  private long longValue(JsonNode node, String field) {
    String value = text(node, field);
    if (value == null) return 0L;
    try {
      return new BigDecimal(value).longValue();
    } catch (NumberFormatException ignored) {
      return 0L;
    }
  }

  private BigDecimal decimal(JsonNode node, String field) {
    BigDecimal value = decimalOrNull(node, field);
    return value == null ? BigDecimal.ZERO : value;
  }

  private BigDecimal decimalOrNull(JsonNode node, String field) {
    String value = text(node, field);
    if (value == null) return null;
    try {
      return new BigDecimal(value);
    } catch (NumberFormatException ignored) {
      return null;
    }
  }
}
