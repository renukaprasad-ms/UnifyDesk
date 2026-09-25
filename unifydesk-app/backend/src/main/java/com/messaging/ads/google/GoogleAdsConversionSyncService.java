package com.messaging.ads.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.messaging.ads.google.dto.GoogleConversionSyncResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GoogleAdsConversionSyncService {

  private final GoogleAdsAccountContextService contextService;
  private final GoogleAdsApiClient apiClient;
  private final GoogleAdsConversionPersistenceService persistenceService;

  public GoogleConversionSyncResponse sync(Long companyId, Long accountId) {
    var context = contextService.requireManaged(companyId, accountId);
    List<JsonNode> rows =
        apiClient.searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            query(null));
    return persistenceService.persist(companyId, accountId, rows);
  }

  public JsonNode conversion(
      GoogleAdsAccountContextService.Context context, String externalId) {
    List<JsonNode> rows =
        apiClient.searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            query(numericId(externalId)));
    if (rows.isEmpty()) {
      throw new com.messaging.common.exception.NotFoundException(
          "Google Ads conversion action not found");
    }
    return rows.get(0);
  }

  private String query(String conversionId) {
    String query =
        "SELECT conversion_action.id, conversion_action.name, "
            + "conversion_action.status, conversion_action.type, "
            + "conversion_action.category, conversion_action.origin, "
            + "conversion_action.counting_type, "
            + "conversion_action.primary_for_goal, "
            + "conversion_action.include_in_conversions_metric, "
            + "conversion_action.value_settings.default_value, "
            + "conversion_action.value_settings.always_use_default_value, "
            + "conversion_action.attribution_model_settings.attribution_model "
            + "FROM conversion_action";
    if (conversionId != null) {
      return query + " WHERE conversion_action.id = " + conversionId + " LIMIT 1";
    }
    return query + " WHERE conversion_action.status != 'REMOVED'";
  }

  private String numericId(String value) {
    if (value == null || !value.matches("\\d+")) {
      throw new com.messaging.common.exception.BadRequestException(
          "Invalid Google Ads conversion action ID");
    }
    return value;
  }
}
