package com.messaging.ads.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.messaging.ads.google.dto.GoogleProviderReportResponse;
import com.messaging.ads.google.dto.GoogleProviderReportResponse.AuctionInsightRow;
import com.messaging.ads.google.dto.GoogleProviderReportResponse.ChangeEventRow;
import com.messaging.ads.google.dto.GoogleProviderReportResponse.ConversionAttributionRow;
import com.messaging.ads.google.dto.GoogleProviderReportResponse.LandingPageRow;
import com.messaging.ads.google.dto.GoogleProviderReportResponse.PlacementRow;
import com.messaging.common.exception.BadRequestException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GoogleAdsProviderReportingService {

  private static final long MAX_REPORT_DAYS = 90;

  private final GoogleAdsAccountContextService contextService;
  private final GoogleAdsApiClient apiClient;

  public GoogleProviderReportResponse report(
      Long companyId,
      Long accountId,
      LocalDate from,
      LocalDate to,
      String campaignExternalId) {
    validateRange(from, to);
    var context = contextService.requireManaged(companyId, accountId);
    String customerId = context.account().getExternalAccountId();
    String campaignFilter = campaignFilter(campaignExternalId);

    List<LandingPageRow> landingPages =
        landingPages(context, customerId, from, to, campaignFilter);
    List<PlacementRow> placements =
        placements(context, customerId, from, to, campaignFilter);
    List<AuctionInsightRow> auction =
        auctionInsights(context, customerId, from, to, campaignFilter);
    List<ConversionAttributionRow> conversions =
        conversionAttribution(context, customerId, from, to, campaignFilter);
    List<ChangeEventRow> changes =
        changeEvents(context, customerId, from, to, campaignExternalId);

    return new GoogleProviderReportResponse(
        landingPages, placements, auction, conversions, changes);
  }

  private List<LandingPageRow> landingPages(
      GoogleAdsAccountContextService.Context context,
      String customerId,
      LocalDate from,
      LocalDate to,
      String campaignFilter) {
    String query =
        "SELECT campaign.id, campaign.name, ad_group.id, ad_group.name, "
            + "landing_page_view.unexpanded_final_url, "
            + "metrics.impressions, metrics.clicks, metrics.cost_micros, "
            + "metrics.conversions, metrics.conversions_value "
            + "FROM landing_page_view "
            + "WHERE segments.date BETWEEN '"
            + from
            + "' AND '"
            + to
            + "'"
            + campaignFilter
            + " ORDER BY metrics.cost_micros DESC LIMIT 1000";

    return apiClient
        .searchRows(
            context.accessToken(), customerId, context.loginCustomerId(), query)
        .stream()
        .map(
            row ->
                new LandingPageRow(
                    text(row.path("campaign"), "id"),
                    text(row.path("campaign"), "name"),
                    text(row.path("adGroup"), "id"),
                    text(row.path("adGroup"), "name"),
                    text(row.path("landingPageView"), "unexpandedFinalUrl"),
                    longValue(row.path("metrics"), "impressions"),
                    longValue(row.path("metrics"), "clicks"),
                    longValue(row.path("metrics"), "costMicros"),
                    decimal(row.path("metrics"), "conversions"),
                    decimal(row.path("metrics"), "conversionsValue")))
        .filter(row -> row.url() != null)
        .sorted(Comparator.comparingLong(LandingPageRow::costMicros).reversed())
        .toList();
  }

  private List<PlacementRow> placements(
      GoogleAdsAccountContextService.Context context,
      String customerId,
      LocalDate from,
      LocalDate to,
      String campaignFilter) {
    String query =
        "SELECT campaign.id, campaign.name, ad_group.id, ad_group.name, "
            + "group_placement_view.placement, group_placement_view.display_name, "
            + "group_placement_view.placement_type, group_placement_view.target_url, "
            + "segments.ad_network_type, "
            + "metrics.impressions, metrics.clicks, metrics.cost_micros, "
            + "metrics.conversions, metrics.conversions_value "
            + "FROM group_placement_view "
            + "WHERE segments.date BETWEEN '"
            + from
            + "' AND '"
            + to
            + "'"
            + campaignFilter
            + " ORDER BY metrics.cost_micros DESC LIMIT 1000";

    return apiClient
        .searchRows(
            context.accessToken(), customerId, context.loginCustomerId(), query)
        .stream()
        .map(
            row ->
                new PlacementRow(
                    text(row.path("campaign"), "id"),
                    text(row.path("campaign"), "name"),
                    text(row.path("adGroup"), "id"),
                    text(row.path("adGroup"), "name"),
                    text(row.path("groupPlacementView"), "placement"),
                    text(row.path("groupPlacementView"), "displayName"),
                    text(row.path("groupPlacementView"), "placementType"),
                    text(row.path("groupPlacementView"), "targetUrl"),
                    text(row.path("segments"), "adNetworkType"),
                    longValue(row.path("metrics"), "impressions"),
                    longValue(row.path("metrics"), "clicks"),
                    longValue(row.path("metrics"), "costMicros"),
                    decimal(row.path("metrics"), "conversions"),
                    decimal(row.path("metrics"), "conversionsValue")))
        .filter(row -> row.placement() != null)
        .sorted(Comparator.comparingLong(PlacementRow::costMicros).reversed())
        .toList();
  }

  private List<AuctionInsightRow> auctionInsights(
      GoogleAdsAccountContextService.Context context,
      String customerId,
      LocalDate from,
      LocalDate to,
      String campaignFilter) {
    String query =
        "SELECT campaign.id, campaign.name, segments.auction_insight_domain, "
            + "metrics.auction_insight_search_impression_share, "
            + "metrics.auction_insight_search_overlap_rate, "
            + "metrics.auction_insight_search_position_above_rate, "
            + "metrics.auction_insight_search_top_impression_percentage, "
            + "metrics.auction_insight_search_absolute_top_impression_percentage, "
            + "metrics.auction_insight_search_outranking_share "
            + "FROM campaign "
            + "WHERE segments.date BETWEEN '"
            + from
            + "' AND '"
            + to
            + "'"
            + campaignFilter
            + " ORDER BY metrics.auction_insight_search_impression_share DESC LIMIT 500";

    return apiClient
        .searchRows(
            context.accessToken(), customerId, context.loginCustomerId(), query)
        .stream()
        .map(
            row ->
                new AuctionInsightRow(
                    text(row.path("campaign"), "id"),
                    text(row.path("campaign"), "name"),
                    text(row.path("segments"), "auctionInsightDomain"),
                    decimalOrNull(
                        row.path("metrics"), "auctionInsightSearchImpressionShare"),
                    decimalOrNull(
                        row.path("metrics"), "auctionInsightSearchOverlapRate"),
                    decimalOrNull(
                        row.path("metrics"), "auctionInsightSearchPositionAboveRate"),
                    decimalOrNull(
                        row.path("metrics"), "auctionInsightSearchTopImpressionPercentage"),
                    decimalOrNull(
                        row.path("metrics"), "auctionInsightSearchAbsoluteTopImpressionPercentage"),
                    decimalOrNull(
                        row.path("metrics"), "auctionInsightSearchOutrankingShare")))
        .filter(row -> row.domain() != null)
        .toList();
  }

  private List<ConversionAttributionRow> conversionAttribution(
      GoogleAdsAccountContextService.Context context,
      String customerId,
      LocalDate from,
      LocalDate to,
      String campaignFilter) {
    String query =
        "SELECT campaign.id, campaign.name, "
            + "segments.conversion_action_name, segments.conversion_action_category, "
            + "segments.external_conversion_source, "
            + "metrics.conversions, metrics.conversions_value, "
            + "metrics.all_conversions, metrics.all_conversions_value "
            + "FROM campaign "
            + "WHERE segments.date BETWEEN '"
            + from
            + "' AND '"
            + to
            + "'"
            + campaignFilter
            + " ORDER BY metrics.conversions DESC LIMIT 1000";

    return apiClient
        .searchRows(
            context.accessToken(), customerId, context.loginCustomerId(), query)
        .stream()
        .map(
            row ->
                new ConversionAttributionRow(
                    text(row.path("campaign"), "id"),
                    text(row.path("campaign"), "name"),
                    text(row.path("segments"), "conversionActionName"),
                    text(row.path("segments"), "conversionActionCategory"),
                    text(row.path("segments"), "externalConversionSource"),
                    decimal(row.path("metrics"), "conversions"),
                    decimal(row.path("metrics"), "conversionsValue"),
                    decimal(row.path("metrics"), "allConversions"),
                    decimal(row.path("metrics"), "allConversionsValue")))
        .filter(row -> row.conversionActionName() != null)
        .sorted(
            Comparator.comparing(
                    ConversionAttributionRow::conversions,
                    Comparator.nullsFirst(BigDecimal::compareTo))
                .reversed())
        .toList();
  }

  private List<ChangeEventRow> changeEvents(
      GoogleAdsAccountContextService.Context context,
      String customerId,
      LocalDate from,
      LocalDate to,
      String campaignExternalId) {
    LocalDate earliest = LocalDate.now().minusDays(29);
    LocalDate effectiveFrom = from.isBefore(earliest) ? earliest : from;
    LocalDate effectiveTo = to.isAfter(LocalDate.now()) ? LocalDate.now() : to;
    if (effectiveTo.isBefore(effectiveFrom)) return List.of();

    StringBuilder query =
        new StringBuilder(
            "SELECT change_event.change_date_time, change_event.user_email, "
                + "change_event.client_type, change_event.change_resource_type, "
                + "change_event.resource_change_operation, "
                + "change_event.change_resource_name, change_event.changed_fields, "
                + "change_event.campaign, change_event.ad_group "
                + "FROM change_event WHERE change_event.change_date_time >= '")
            .append(effectiveFrom)
            .append("' AND change_event.change_date_time <= '")
            .append(effectiveTo.plusDays(1))
            .append("'");

    if (campaignExternalId != null && !campaignExternalId.isBlank()) {
      String campaignResource =
          apiClient.customerResourceName(
              customerId,
              "campaigns",
              numeric(campaignExternalId, "campaign"));
      query.append(" AND change_event.campaign = '").append(campaignResource).append("'");
    }
    query.append(" ORDER BY change_event.change_date_time DESC LIMIT 500");

    List<ChangeEventRow> result = new ArrayList<>();
    for (JsonNode row :
        apiClient.searchRows(
            context.accessToken(),
            customerId,
            context.loginCustomerId(),
            query.toString())) {
      JsonNode event = row.path("changeEvent");
      result.add(
          new ChangeEventRow(
              text(event, "changeDateTime"),
              text(event, "userEmail"),
              text(event, "clientType"),
              text(event, "changeResourceType"),
              text(event, "resourceChangeOperation"),
              text(event, "changeResourceName"),
              strings(event.path("changedFields").path("paths")),
              text(event, "campaign"),
              text(event, "adGroup")));
    }
    return List.copyOf(result);
  }

  private String campaignFilter(String campaignExternalId) {
    if (campaignExternalId == null || campaignExternalId.isBlank()) return "";
    return " AND campaign.id = " + numeric(campaignExternalId, "campaign");
  }

  private void validateRange(LocalDate from, LocalDate to) {
    if (from == null || to == null || to.isBefore(from)) {
      throw new BadRequestException("Google provider report requires a valid date range");
    }
    if (to.isAfter(LocalDate.now())) {
      throw new BadRequestException("Google provider report end date cannot be in the future");
    }
    if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_REPORT_DAYS) {
      throw new BadRequestException("Google provider report is limited to 90 days per request");
    }
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

  private List<String> strings(JsonNode node) {
    if (node == null || !node.isArray()) return List.of();
    List<String> values = new ArrayList<>();
    for (JsonNode item : node) {
      if (item.isTextual() && !item.asText().isBlank()) values.add(item.asText().trim());
    }
    return List.copyOf(values);
  }
}
