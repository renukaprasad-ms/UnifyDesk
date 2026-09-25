package com.messaging.ads.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.ads.service.AdActivityLogService;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GoogleAdsAiMaxControlService {

  private static final int MAX_URL_EXCLUSIONS = 100;

  private final AdCampaignRepository campaignRepository;
  private final GoogleAdsAccountContextService contextService;
  private final GoogleAdsApiClient apiClient;
  private final AdActivityLogService activityLogService;

  public ValidationResult validateUrlExclusions(List<String> values) {
    List<String> normalized = normalizeUrlExclusions(values);
    return new ValidationResult(normalized.size());
  }

  public ApplyResult applyUrlExclusions(
      Long companyId,
      Long campaignId,
      List<String> values,
      Long actorUserId) {
    List<String> requested = normalizeUrlExclusions(values);
    if (requested.isEmpty()) return new ApplyResult(0, 0);

    AdCampaign campaign =
        campaignRepository
            .findByIdAndCompanyId(campaignId, companyId)
            .orElseThrow(() -> new NotFoundException("Advertising campaign not found"));
    if (campaign.getProvider() != AdProvider.GOOGLE_ADS
        || !"SEARCH".equalsIgnoreCase(campaign.getObjective())
        || !campaign.isAccessible()) {
      throw new BadRequestException(
          "AI Max URL exclusions are available only for accessible Google Search campaigns");
    }

    var context = contextService.requireManaged(companyId, campaign.getAdAccountId());
    String customerId = context.account().getExternalAccountId();
    Set<String> existing = existingUrlExclusions(context, campaign.getExternalCampaignId());

    List<Map<String, Object>> operations = new ArrayList<>();
    for (String value : requested) {
      if (existing.contains(value.toLowerCase())) continue;
      Map<String, Object> webpage =
          Map.of(
              "conditions",
              List.of(
                  Map.of(
                      "operand", "URL",
                      "argument", value)));
      operations.add(
          Map.of(
              "create",
              Map.of(
                  "campaign",
                  apiClient.customerResourceName(
                      customerId, "campaigns", campaign.getExternalCampaignId()),
                  "negative",
                  true,
                  "webpage",
                  webpage)));
    }

    if (!operations.isEmpty()) {
      apiClient.mutate(
          context.accessToken(),
          customerId,
          context.loginCustomerId(),
          "campaignCriteria",
          operations);
    }

    activityLogService.record(
        companyId,
        campaign.getAdAccountId(),
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_AI_MAX_URL_EXCLUSIONS_APPLIED",
        "CAMPAIGN",
        campaign.getExternalCampaignId(),
        null,
        null,
        Map.of(
            "requestedCount", requested.size(),
            "createdCount", operations.size(),
            "reusedCount", requested.size() - operations.size()));

    return new ApplyResult(
        operations.size(), requested.size() - operations.size());
  }

  private Set<String> existingUrlExclusions(
      GoogleAdsAccountContextService.Context context,
      String externalCampaignId) {
    String query =
        "SELECT campaign_criterion.webpage.conditions, campaign_criterion.negative "
            + "FROM campaign_criterion WHERE campaign.id = "
            + numeric(externalCampaignId)
            + " AND campaign_criterion.type = 'WEBPAGE' "
            + "AND campaign_criterion.negative = TRUE "
            + "AND campaign_criterion.status != 'REMOVED'";

    Set<String> result = new LinkedHashSet<>();
    for (JsonNode row :
        apiClient.searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            query)) {
      JsonNode conditions = row.path("campaignCriterion").path("webpage").path("conditions");
      if (!conditions.isArray()) continue;
      for (JsonNode condition : conditions) {
        if (!"URL".equalsIgnoreCase(condition.path("operand").asText())) continue;
        String argument = condition.path("argument").asText(null);
        if (argument != null && !argument.isBlank()) {
          result.add(argument.trim().toLowerCase());
        }
      }
    }
    return result;
  }

  private List<String> normalizeUrlExclusions(List<String> values) {
    if (values == null || values.isEmpty()) return List.of();
    if (values.size() > MAX_URL_EXCLUSIONS) {
      throw new BadRequestException(
          "Google AI Max URL exclusions are limited to 100 values per campaign configuration");
    }

    Set<String> seen = new LinkedHashSet<>();
    List<String> result = new ArrayList<>();
    for (String value : values) {
      if (value == null || value.isBlank()) {
        throw new BadRequestException("Google AI Max URL exclusions cannot be blank");
      }
      String normalized = value.trim();
      validateUrlPattern(normalized);
      if (seen.add(normalized.toLowerCase())) result.add(normalized);
    }
    return List.copyOf(result);
  }

  private void validateUrlPattern(String value) {
    if (value.length() > 2048) {
      throw new BadRequestException("Google AI Max URL exclusion is too long");
    }
    if (value.startsWith("/")) return;
    try {
      URI uri = URI.create(value);
      if (uri.getScheme() == null
          || (!"http".equalsIgnoreCase(uri.getScheme())
              && !"https".equalsIgnoreCase(uri.getScheme()))
          || uri.getHost() == null) {
        throw new BadRequestException(
            "Google AI Max URL exclusions must be an absolute HTTP(S) URL or a path beginning with /");
      }
    } catch (IllegalArgumentException exception) {
      throw new BadRequestException(
          "Google AI Max URL exclusions must be valid URLs or URL paths");
    }
  }

  private String numeric(String value) {
    if (value == null || !value.trim().matches("\\d+")) {
      throw new BadRequestException("Invalid Google Ads campaign ID");
    }
    return value.trim();
  }

  public record ValidationResult(int requestedCount) {}

  public record ApplyResult(int createdCount, int reusedCount) {}
}
