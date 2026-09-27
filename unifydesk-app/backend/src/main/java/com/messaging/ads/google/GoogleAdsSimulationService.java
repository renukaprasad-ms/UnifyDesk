package com.messaging.ads.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.GoogleCampaignSimulationResponse;
import com.messaging.ads.google.dto.GoogleCampaignSimulationResponse.Point;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GoogleAdsSimulationService {

  private final GoogleAdsAccountContextService contextService;
  private final GoogleAdsApiClient apiClient;
  private final AdCampaignRepository campaignRepository;

  public List<GoogleCampaignSimulationResponse> campaignSimulations(
      Long companyId, Long campaignId) {
    AdCampaign campaign =
        campaignRepository
            .findByIdAndCompanyId(campaignId, companyId)
            .orElseThrow(() -> new NotFoundException("Advertising campaign not found"));
    if (campaign.getProvider() != AdProvider.GOOGLE_ADS || !campaign.isAccessible()) {
      throw new BadRequestException("Google campaign is not available for simulations");
    }

    var context = contextService.requireManaged(companyId, campaign.getAdAccountId());
    String externalCampaignId = numeric(campaign.getExternalCampaignId());
    String query =
        "SELECT campaign_simulation.resource_name, campaign_simulation.campaign_id, "
            + "campaign_simulation.type, campaign_simulation.modification_method, "
            + "campaign_simulation.start_date, campaign_simulation.end_date, "
            + "campaign_simulation.budget_point_list.points, "
            + "campaign_simulation.cpc_bid_point_list.points, "
            + "campaign_simulation.target_cpa_point_list.points, "
            + "campaign_simulation.target_roas_point_list.points, "
            + "campaign_simulation.target_impression_share_point_list.points "
            + "FROM campaign_simulation "
            + "WHERE campaign_simulation.campaign_id = "
            + externalCampaignId;

    List<GoogleCampaignSimulationResponse> result = new ArrayList<>();
    for (JsonNode row :
        apiClient.searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            query)) {
      JsonNode simulation = row.path("campaignSimulation");
      String type = text(simulation, "type");
      result.add(
          new GoogleCampaignSimulationResponse(
              text(simulation, "resourceName"),
              text(simulation, "campaignId"),
              type,
              text(simulation, "modificationMethod"),
              text(simulation, "startDate"),
              text(simulation, "endDate"),
              points(simulation, type)));
    }
    return List.copyOf(result);
  }

  private List<Point> points(JsonNode simulation, String type) {
    String field =
        switch (type == null ? "" : type) {
          case "BUDGET" -> "budgetPointList";
          case "CPC_BID" -> "cpcBidPointList";
          case "TARGET_CPA" -> "targetCpaPointList";
          case "TARGET_ROAS" -> "targetRoasPointList";
          case "TARGET_IMPRESSION_SHARE" -> "targetImpressionSharePointList";
          default -> null;
        };
    if (field == null) return List.of();

    JsonNode values = simulation.path(field).path("points");
    if (!values.isArray()) return List.of();
    List<Point> result = new ArrayList<>();
    for (JsonNode point : values) {
      Long keyMicros =
          firstLong(
              point,
              "budgetAmountMicros",
              "cpcBidMicros",
              "targetCpaMicros",
              "targetImpressionShareMicros");
      BigDecimal keyRatio =
          firstDecimal(point, "cpcBidScalingModifier", "targetCpaScalingModifier", "targetRoas");
      result.add(
          new Point(
              keyMicros,
              keyRatio,
              longValue(point, "impressions"),
              longValue(point, "clicks"),
              longValue(point, "costMicros"),
              firstDecimal(point, "biddableConversions", "conversions"),
              firstDecimal(point, "biddableConversionsValue", "conversionValue"),
              firstLong(point, "requiredBudgetAmountMicros"),
              firstLong(point, "requiredCpcBidCeilingMicros")));
    }
    return List.copyOf(result);
  }

  private String numeric(String value) {
    if (value == null || !value.matches("\\d+")) {
      throw new BadRequestException("Invalid Google campaign ID");
    }
    return value;
  }

  private String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) return null;
    String result = value.asText(null);
    return result == null || result.isBlank() ? null : result.trim();
  }

  private long longValue(JsonNode node, String field) {
    Long value = firstLong(node, field);
    return value == null ? 0L : value;
  }

  private Long firstLong(JsonNode node, String... fields) {
    for (String field : fields) {
      String value = text(node, field);
      if (value == null) continue;
      try {
        return new BigDecimal(value).longValue();
      } catch (NumberFormatException ignored) {
      }
    }
    return null;
  }

  private BigDecimal firstDecimal(JsonNode node, String... fields) {
    for (String field : fields) {
      String value = text(node, field);
      if (value == null) continue;
      try {
        return new BigDecimal(value);
      } catch (NumberFormatException ignored) {
      }
    }
    return null;
  }
}
