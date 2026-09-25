package com.messaging.ads.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.GoogleAiMaxCombinationReportResponse;
import com.messaging.ads.repository.AdCampaignRepository;
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
public class GoogleAdsAiMaxReportingService {

  private static final int MAX_ROWS = 1000;
  private static final long MAX_REPORT_DAYS = 90;

  private final AdCampaignRepository campaignRepository;
  private final GoogleAdsAccountContextService contextService;
  private final GoogleAdsApiClient apiClient;

  public GoogleAiMaxCombinationReportResponse report(
      Long companyId, Long campaignId, LocalDate from, LocalDate to) {
    validateRange(from, to);
    AdCampaign campaign =
        campaignRepository
            .findByIdAndCompanyId(campaignId, companyId)
            .orElseThrow(() -> new NotFoundException("Advertising campaign not found"));
    if (campaign.getProvider() != AdProvider.GOOGLE_ADS
        || !campaign.isAccessible()
        || !"SEARCH".equalsIgnoreCase(campaign.getObjective())) {
      throw new BadRequestException(
          "AI Max reporting is available only for accessible Google Search campaigns");
    }

    var context = contextService.requireManaged(companyId, campaign.getAdAccountId());
    String externalCampaignId = numeric(campaign.getExternalCampaignId());

    String query =
        "SELECT ai_max_search_term_ad_combination_view.search_term, "
            + "ai_max_search_term_ad_combination_view.headline, "
            + "ai_max_search_term_ad_combination_view.landing_page, "
            + "ad_group.id, ad_group.name, customer.currency_code, "
            + "metrics.impressions, metrics.clicks, metrics.cost_micros, "
            + "metrics.conversions, metrics.conversions_value "
            + "FROM ai_max_search_term_ad_combination_view "
            + "WHERE campaign.id = "
            + externalCampaignId
            + " AND segments.date BETWEEN '"
            + from
            + "' AND '"
            + to
            + "' ORDER BY metrics.cost_micros DESC LIMIT "
            + MAX_ROWS;

    List<GoogleAiMaxCombinationReportResponse.Row> rows = new ArrayList<>();
    String currency = context.account().getCurrency();
    for (JsonNode row :
        apiClient.searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            query)) {
      JsonNode view = row.path("aiMaxSearchTermAdCombinationView");
      JsonNode adGroup = row.path("adGroup");
      JsonNode metrics = row.path("metrics");
      BigDecimal spend = GoogleAdsMoney.microsToMajor(longValue(metrics, "costMicros"));
      BigDecimal conversions = decimal(metrics, "conversions");
      BigDecimal value = decimal(metrics, "conversionsValue");
      rows.add(
          new GoogleAiMaxCombinationReportResponse.Row(
              text(view, "searchTerm"),
              text(view, "headline"),
              text(view, "landingPage"),
              text(adGroup, "id"),
              text(adGroup, "name"),
              longValue(metrics, "impressions"),
              longValue(metrics, "clicks"),
              spend,
              conversions,
              value,
              divide(spend, conversions),
              divide(value, spend)));
      String rowCurrency = text(row.path("customer"), "currencyCode");
      if (rowCurrency != null) currency = rowCurrency;
    }

    rows.sort(
        Comparator.comparing(
                GoogleAiMaxCombinationReportResponse.Row::spend,
                Comparator.nullsFirst(BigDecimal::compareTo))
            .reversed());

    return new GoogleAiMaxCombinationReportResponse(
        campaign.getId().toString(), from, to, currency, List.copyOf(rows));
  }

  private void validateRange(LocalDate from, LocalDate to) {
    if (from == null || to == null || to.isBefore(from)) {
      throw new BadRequestException("A valid AI Max report date range is required");
    }
    if (to.isAfter(LocalDate.now())) {
      throw new BadRequestException("AI Max report end date cannot be in the future");
    }
    if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_REPORT_DAYS) {
      throw new BadRequestException("AI Max reporting is limited to 90 days per request");
    }
  }

  private String numeric(String value) {
    if (value == null || !value.trim().matches("\\d+")) {
      throw new BadRequestException("Invalid Google Ads campaign ID");
    }
    return value.trim();
  }

  private String text(JsonNode node, String field) {
    if (node == null || node.path(field).isMissingNode() || node.path(field).isNull()) return null;
    String value = node.path(field).asText(null);
    return value == null || value.isBlank() ? null : value.trim();
  }

  private long longValue(JsonNode node, String field) {
    String value = text(node, field);
    if (value == null) return 0L;
    try {
      return new BigDecimal(value).longValue();
    } catch (RuntimeException ignored) {
      return 0L;
    }
  }

  private BigDecimal decimal(JsonNode node, String field) {
    String value = text(node, field);
    if (value == null) return BigDecimal.ZERO;
    try {
      return new BigDecimal(value);
    } catch (RuntimeException ignored) {
      return BigDecimal.ZERO;
    }
  }

  private BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
    if (numerator == null || denominator == null || denominator.signum() == 0) return null;
    return numerator.divide(denominator, 8, RoundingMode.HALF_UP);
  }
}
