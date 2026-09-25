package com.messaging.ads.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.messaging.ads.enums.AdInsightGranularity;
import com.messaging.ads.enums.AdInsightLevel;
import com.messaging.ads.google.dto.GoogleInsightsSyncResponse;
import com.messaging.common.exception.BadRequestException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GoogleAdsInsightSyncService {

  private static final long MAX_SYNC_DAYS = 90;

  private final GoogleAdsAccountContextService contextService;
  private final GoogleAdsApiClient apiClient;
  private final GoogleAdsInsightPersistenceService persistenceService;

  public GoogleInsightsSyncResponse sync(
      Long companyId, Long accountId, LocalDate from, LocalDate to) {
    validateRange(from, to);
    var context = contextService.requireManaged(companyId, accountId);

    int accountRows = syncLevel(companyId, context, AdInsightLevel.ACCOUNT, from, to);
    int campaignRows = syncLevel(companyId, context, AdInsightLevel.CAMPAIGN, from, to);
    int adGroupRows = syncLevel(companyId, context, AdInsightLevel.AD_SET, from, to);
    int adRows = syncLevel(companyId, context, AdInsightLevel.AD, from, to);

    return new GoogleInsightsSyncResponse(
        accountRows, campaignRows, adGroupRows, adRows);
  }

  private int syncLevel(
      Long companyId,
      GoogleAdsAccountContextService.Context context,
      AdInsightLevel level,
      LocalDate from,
      LocalDate to) {
    String customerId = context.account().getExternalAccountId();

    List<JsonNode> daily =
        apiClient.searchRows(
            context.accessToken(),
            customerId,
            context.loginCustomerId(),
            query(level, from, to, true));
    persistenceService.persist(
        companyId,
        context.account().getId(),
        level,
        AdInsightGranularity.DAILY,
        from,
        to,
        daily);

    List<JsonNode> range =
        apiClient.searchRows(
            context.accessToken(),
            customerId,
            context.loginCustomerId(),
            query(level, from, to, false));
    return persistenceService.persist(
        companyId,
        context.account().getId(),
        level,
        AdInsightGranularity.RANGE,
        from,
        to,
        range);
  }

  private String query(
      AdInsightLevel level,
      LocalDate from,
      LocalDate to,
      boolean daily) {
    StringBuilder select = new StringBuilder("SELECT ");
    if (daily) select.append("segments.date, ");
    select.append("customer.currency_code, ");
    switch (level) {
      case ACCOUNT -> select.append("customer.id, customer.descriptive_name, ");
      case CAMPAIGN ->
          select.append("campaign.id, campaign.name, ");
      case AD_SET ->
          select.append(
              "campaign.id, ad_group.id, ad_group.name, ");
      case AD ->
          select.append(
              "campaign.id, ad_group.id, ad_group_ad.resource_name, "
                  + "ad_group_ad.ad.id, ad_group_ad.ad.name, ");
    }
    select.append(
        "metrics.cost_micros, metrics.impressions, metrics.clicks, "
            + "metrics.ctr, metrics.average_cpc, metrics.average_cpm, "
            + "metrics.conversions, metrics.conversions_value, "
            + "metrics.engagements, metrics.video_trueview_views ");

    return select
        + "FROM "
        + resource(level)
        + " WHERE segments.date BETWEEN '"
        + from
        + "' AND '"
        + to
        + "'";
  }

  private String resource(AdInsightLevel level) {
    return switch (level) {
      case ACCOUNT -> "customer";
      case CAMPAIGN -> "campaign";
      case AD_SET -> "ad_group";
      case AD -> "ad_group_ad";
    };
  }

  private void validateRange(LocalDate from, LocalDate to) {
    if (from == null || to == null) {
      throw new BadRequestException("Analytics start and end dates are required");
    }
    if (to.isBefore(from)) {
      throw new BadRequestException(
          "Analytics end date must be on or after the start date");
    }
    if (to.isAfter(LocalDate.now())) {
      throw new BadRequestException("Analytics end date cannot be in the future");
    }
    long days = ChronoUnit.DAYS.between(from, to) + 1;
    if (days > MAX_SYNC_DAYS) {
      throw new BadRequestException(
          "Google Ads analytics sync is limited to 90 days per request");
    }
  }
}
