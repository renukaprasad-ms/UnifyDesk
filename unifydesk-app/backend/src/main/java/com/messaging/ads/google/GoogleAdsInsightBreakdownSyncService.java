package com.messaging.ads.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.messaging.ads.enums.AdInsightBreakdownType;
import com.messaging.ads.google.dto.GoogleInsightBreakdownsSyncResponse;
import com.messaging.common.exception.BadRequestException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleAdsInsightBreakdownSyncService {

  private static final long MAX_SYNC_DAYS = 90;

  private final GoogleAdsAccountContextService contextService;
  private final GoogleAdsApiClient apiClient;
  private final GoogleAdsInsightBreakdownPersistenceService persistenceService;

  public GoogleInsightBreakdownsSyncResponse sync(
      Long companyId, Long accountId, LocalDate from, LocalDate to) {
    validateRange(from, to);
    var context = contextService.requireManaged(companyId, accountId);
    List<String> failed = new ArrayList<>();

    int age = syncOne(companyId, context, AdInsightBreakdownType.AGE, from, to, failed);
    int gender = syncOne(companyId, context, AdInsightBreakdownType.GENDER, from, to, failed);
    int country = syncOne(companyId, context, AdInsightBreakdownType.COUNTRY, from, to, failed);
    int region = syncOne(companyId, context, AdInsightBreakdownType.REGION, from, to, failed);
    int publisherPlatform =
        syncOne(
            companyId,
            context,
            AdInsightBreakdownType.PUBLISHER_PLATFORM,
            from,
            to,
            failed);
    int platformPosition =
        syncOne(
            companyId,
            context,
            AdInsightBreakdownType.PLATFORM_POSITION,
            from,
            to,
            failed);
    int impressionDevice =
        syncOne(
            companyId,
            context,
            AdInsightBreakdownType.IMPRESSION_DEVICE,
            from,
            to,
            failed);
    int devicePlatform =
        syncOne(
            companyId,
            context,
            AdInsightBreakdownType.DEVICE_PLATFORM,
            from,
            to,
            failed);
    int conversionDestination =
        syncOne(
            companyId,
            context,
            AdInsightBreakdownType.CONVERSION_DESTINATION,
            from,
            to,
            failed);
    int actionType =
        syncOne(
            companyId,
            context,
            AdInsightBreakdownType.ACTION_TYPE,
            from,
            to,
            failed);
    int actionDevice =
        syncOne(
            companyId,
            context,
            AdInsightBreakdownType.ACTION_DEVICE,
            from,
            to,
            failed);

    return new GoogleInsightBreakdownsSyncResponse(
        age,
        gender,
        country,
        region,
        publisherPlatform,
        platformPosition,
        impressionDevice,
        devicePlatform,
        conversionDestination,
        actionType,
        actionDevice,
        List.copyOf(failed));
  }

  private int syncOne(
      Long companyId,
      GoogleAdsAccountContextService.Context context,
      AdInsightBreakdownType type,
      LocalDate from,
      LocalDate to,
      List<String> failed) {
    try {
      List<JsonNode> rows =
          apiClient.searchRows(
              context.accessToken(),
              context.account().getExternalAccountId(),
              context.loginCustomerId(),
              query(type, from, to));
      return persistenceService.persist(
          companyId,
          context.account().getId(),
          type,
          from,
          to,
          rows);
    } catch (RuntimeException exception) {
      failed.add(type.name());
      log.warn(
          "google_ads_breakdown_sync_failed companyId={} accountId={} breakdown={} error={}",
          companyId,
          context.account().getId(),
          type,
          exception.getMessage());
      return 0;
    }
  }

  private String query(
      AdInsightBreakdownType type, LocalDate from, LocalDate to) {
    String dimension = switch (type) {
      case AGE -> "ad_group_criterion.age_range.type";
      case GENDER -> "ad_group_criterion.gender.type";
      case COUNTRY -> "segments.geo_target_country";
      case REGION -> "segments.geo_target_region";
      case PUBLISHER_PLATFORM -> "segments.ad_network_type";
      case PLATFORM_POSITION -> "segments.slot";
      case IMPRESSION_DEVICE, DEVICE_PLATFORM -> "segments.device";
      case CONVERSION_DESTINATION -> "segments.ad_destination_type";
      case ACTION_TYPE -> "segments.conversion_action_category";
      case ACTION_DEVICE -> "segments.device, segments.conversion_action_category";
    };

    String resource = switch (type) {
      case AGE -> "age_range_view";
      case GENDER -> "gender_view";
      default -> "ad_group";
    };

    return "SELECT "
        + dimension
        + ", metrics.cost_micros, metrics.impressions, metrics.clicks, "
        + "metrics.conversions, metrics.conversions_value "
        + "FROM "
        + resource
        + " WHERE segments.date BETWEEN '"
        + from
        + "' AND '"
        + to
        + "'";
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
    if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_SYNC_DAYS) {
      throw new BadRequestException(
          "Google Ads analytics sync is limited to 90 days per request");
    }
  }
}
