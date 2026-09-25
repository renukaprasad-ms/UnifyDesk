package com.messaging.ads.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.entity.AdSet;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.GoogleSearchTermReportResponse;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.ads.repository.AdSetRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GoogleAdsSearchTermService {

  private static final int MAX_ROWS = 1000;
  private static final long MAX_REPORT_DAYS = 90;

  private final AdCampaignRepository campaignRepository;
  private final AdSetRepository adSetRepository;
  private final GoogleAdsAccountContextService contextService;
  private final GoogleAdsApiClient apiClient;

  public GoogleSearchTermReportResponse report(
      Long companyId, Long campaignId, LocalDate from, LocalDate to) {
    validateRange(from, to);
    AdCampaign campaign = requireSearchCampaign(companyId, campaignId);
    var context = contextService.requireManaged(companyId, campaign.getAdAccountId());

    String externalCampaignId = numeric(campaign.getExternalCampaignId(), "campaign");
    String query =
        "SELECT search_term_view.search_term, search_term_view.status, "
            + "segments.keyword.info.text, segments.keyword.info.match_type, "
            + "segments.search_term_match_type, segments.search_term_match_source, "
            + "campaign.id, campaign.name, ad_group.id, ad_group.name, "
            + "customer.currency_code, metrics.impressions, metrics.clicks, "
            + "metrics.ctr, metrics.cost_micros, metrics.conversions, "
            + "metrics.conversions_value "
            + "FROM search_term_view "
            + "WHERE campaign.id = "
            + externalCampaignId
            + " AND segments.date BETWEEN '"
            + from
            + "' AND '"
            + to
            + "' "
            + "ORDER BY metrics.cost_micros DESC "
            + "LIMIT "
            + MAX_ROWS;

    List<GoogleSearchTermReportResponse.Row> rows = new ArrayList<>();
    String currency = context.account().getCurrency();

    for (JsonNode row :
        apiClient.searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            query)) {
      JsonNode view = row.path("searchTermView");
      JsonNode adGroup = row.path("adGroup");
      JsonNode metrics = row.path("metrics");
      String searchTerm = text(view, "searchTerm");
      String externalAdGroupId = text(adGroup, "id");
      if (searchTerm == null || externalAdGroupId == null) continue;

      AdSet localAdGroup =
          adSetRepository
              .findByCompanyIdAndAdAccountIdAndProviderAndExternalAdSetId(
                  companyId,
                  campaign.getAdAccountId(),
                  AdProvider.GOOGLE_ADS,
                  externalAdGroupId)
              .orElse(null);

      long impressions = longValue(metrics, "impressions");
      long clicks = longValue(metrics, "clicks");
      BigDecimal spend =
          GoogleAdsMoney.microsToMajor(longValue(metrics, "costMicros"));
      BigDecimal conversions = decimal(metrics, "conversions");
      BigDecimal conversionValue = decimal(metrics, "conversionsValue");
      BigDecimal ctr = nullableDecimal(metrics, "ctr");
      if (ctr == null && impressions > 0) {
        ctr =
            BigDecimal.valueOf(clicks)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(impressions), 8, RoundingMode.HALF_UP);
      }

      JsonNode segments = row.path("segments");
      JsonNode keyword = segments.path("keyword").path("info");
      rows.add(
          new GoogleSearchTermReportResponse.Row(
              searchTerm,
              text(view, "status"),
              text(keyword, "text"),
              text(keyword, "matchType"),
              text(segments, "searchTermMatchType"),
              text(segments, "searchTermMatchSource"),
              externalAdGroupId,
              localAdGroup == null ? null : localAdGroup.getId().toString(),
              first(text(adGroup, "name"), localAdGroup == null ? null : localAdGroup.getName()),
              impressions,
              clicks,
              ctr,
              spend,
              conversions,
              conversionValue,
              divide(spend, conversions),
              divide(conversionValue, spend)));

      String rowCurrency = text(row.path("customer"), "currencyCode");
      if (rowCurrency != null) currency = rowCurrency;
    }

    rows.sort(
        Comparator.comparing(
                GoogleSearchTermReportResponse.Row::spend,
                Comparator.nullsFirst(BigDecimal::compareTo))
            .reversed());

    return new GoogleSearchTermReportResponse(
        campaign.getId().toString(),
        from,
        to,
        currency,
        List.copyOf(rows));
  }

  private AdCampaign requireSearchCampaign(Long companyId, Long campaignId) {
    AdCampaign campaign =
        campaignRepository
            .findByIdAndCompanyId(campaignId, companyId)
            .orElseThrow(() -> new NotFoundException("Advertising campaign not found"));
    if (campaign.getProvider() != AdProvider.GOOGLE_ADS
        || !campaign.isAccessible()
        || !"SEARCH".equalsIgnoreCase(campaign.getObjective())) {
      throw new BadRequestException(
          "Google search-term reporting is available only for accessible Search campaigns");
    }
    return campaign;
  }

  private void validateRange(LocalDate from, LocalDate to) {
    if (from == null || to == null || to.isBefore(from)) {
      throw new BadRequestException("A valid search-term report date range is required");
    }
    if (to.isAfter(LocalDate.now())) {
      throw new BadRequestException("Search-term report end date cannot be in the future");
    }
    if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_REPORT_DAYS) {
      throw new BadRequestException(
          "Google search-term reporting is limited to 90 days per request");
    }
  }

  private String numeric(String value, String label) {
    if (value == null || !value.trim().matches("\\d+")) {
      throw new BadRequestException("Invalid Google Ads " + label + " ID");
    }
    return value.trim();
  }

  private String text(JsonNode node, String field) {
    if (node == null || node.path(field).isMissingNode() || node.path(field).isNull()) return null;
    String value = node.path(field).asText(null);
    return value == null || value.isBlank() ? null : value.trim();
  }

  private String first(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
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
    BigDecimal value = nullableDecimal(node, field);
    return value == null ? BigDecimal.ZERO : value;
  }

  private BigDecimal nullableDecimal(JsonNode node, String field) {
    String value = text(node, field);
    if (value == null) return null;
    try {
      return new BigDecimal(value);
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
    if (numerator == null || denominator == null || denominator.signum() == 0) return null;
    return numerator.divide(denominator, 8, RoundingMode.HALF_UP);
  }
}
