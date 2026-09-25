package com.messaging.ads.google;

import com.messaging.ads.dto.AdConversionResponse;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.CreateGoogleConversionRequest;
import com.messaging.ads.service.AdActivityLogService;
import com.messaging.common.exception.BadRequestException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GoogleAdsConversionMutationService {

  private static final Set<String> COUNTING_TYPES =
      Set.of("ONE_PER_CLICK", "MANY_PER_CLICK");

  private final GoogleAdsAccountContextService contextService;
  private final GoogleAdsApiClient apiClient;
  private final GoogleAdsConversionSyncService syncService;
  private final GoogleAdsConversionPersistenceService persistenceService;
  private final AdActivityLogService activityLogService;

  public AdConversionResponse create(
      Long companyId,
      Long accountId,
      CreateGoogleConversionRequest request,
      Long actorUserId) {
    var context = contextService.requireManaged(companyId, accountId);
    if (context.managerAccount()) {
      throw new BadRequestException(
          "Google Ads manager accounts cannot directly own conversion actions");
    }

    String type =
        request.type() == null || request.type().isBlank()
            ? "WEBPAGE"
            : request.type().trim().toUpperCase();
    String countingType =
        request.countingType() == null || request.countingType().isBlank()
            ? "ONE_PER_CLICK"
            : request.countingType().trim().toUpperCase();
    if (!COUNTING_TYPES.contains(countingType)) {
      throw new BadRequestException(
          "Google conversion counting type must be ONE_PER_CLICK or MANY_PER_CLICK");
    }

    Map<String, Object> create = new LinkedHashMap<>();
    create.put("name", request.name().trim());
    create.put("status", "ENABLED");
    create.put("type", type);
    create.put("category", request.category().trim().toUpperCase());
    create.put("countingType", countingType);
    if (request.defaultValue() != null) {
      create.put(
          "valueSettings",
          Map.of(
              "defaultValue", request.defaultValue(),
              "alwaysUseDefaultValue", false));
    }

    var response =
        apiClient.mutate(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            "conversionActions",
            List.of(Map.of("create", create)));

    String externalId = suffix(apiClient.firstMutatedResourceName(response));
    var row = syncService.conversion(context, externalId);
    AdConversionResponse after =
        AdConversionResponse.from(
            persistenceService.persistOne(companyId, accountId, row));

    activityLogService.record(
        companyId,
        accountId,
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_CONVERSION_CREATED",
        "CONVERSION",
        externalId,
        null,
        after,
        Map.of("type", type, "countingType", countingType));
    return after;
  }

  private String suffix(String resourceName) {
    if (resourceName == null || resourceName.isBlank()) {
      throw new BadRequestException(
          "Google Ads did not return the created conversion action");
    }
    int slash = resourceName.lastIndexOf('/');
    if (slash < 0 || slash == resourceName.length() - 1) {
      throw new BadRequestException(
          "Invalid Google Ads conversion action resource name");
    }
    return resourceName.substring(slash + 1);
  }
}
