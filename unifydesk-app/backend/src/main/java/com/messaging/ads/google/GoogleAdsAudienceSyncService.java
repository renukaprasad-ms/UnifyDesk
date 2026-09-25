package com.messaging.ads.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.messaging.ads.google.dto.GoogleAudienceSyncResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GoogleAdsAudienceSyncService {

  private final GoogleAdsAccountContextService contextService;
  private final GoogleAdsApiClient apiClient;
  private final GoogleAdsAudiencePersistenceService persistenceService;

  public GoogleAudienceSyncResponse sync(Long companyId, Long accountId) {
    var context = contextService.requireManaged(companyId, accountId);
    List<JsonNode> rows =
        apiClient.searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            query(null));
    return persistenceService.persist(companyId, accountId, rows);
  }

  public JsonNode audience(
      GoogleAdsAccountContextService.Context context, String externalId) {
    List<JsonNode> rows =
        apiClient.searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            query(numericId(externalId)));
    if (rows.isEmpty()) {
      throw new com.messaging.common.exception.NotFoundException(
          "Google Ads audience not found");
    }
    return rows.get(0);
  }

  private String query(String audienceId) {
    String query =
        "SELECT user_list.id, user_list.resource_name, user_list.name, "
            + "user_list.description, user_list.type, user_list.membership_status, "
            + "user_list.membership_life_span, user_list.size_for_search, "
            + "user_list.size_for_display, user_list.eligible_for_search, "
            + "user_list.eligible_for_display, user_list.account_user_list_status, "
            + "user_list.read_only, user_list.access_reason, "
            + "user_list.match_rate_percentage, user_list.crm_based_user_list.upload_key_type, "
            + "user_list.rule_based_user_list.prepopulation_status, "
            + "user_list.lookalike_user_list.expansion_level "
            + "FROM user_list";
    if (audienceId != null) {
      return query + " WHERE user_list.id = " + audienceId + " LIMIT 1";
    }
    return query;
  }

  private String numericId(String value) {
    if (value == null || !value.matches("\\d+")) {
      throw new com.messaging.common.exception.BadRequestException(
          "Invalid Google Ads audience ID");
    }
    return value;
  }
}
