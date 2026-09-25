package com.messaging.ads.google;

import com.messaging.ads.dto.AdAudienceResponse;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.CreateGoogleAudienceRequest;
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
public class GoogleAdsAudienceMutationService {

  private static final Set<String> UPLOAD_KEY_TYPES =
      Set.of("CONTACT_INFO", "CRM_ID", "MOBILE_ADVERTISING_ID");

  private final GoogleAdsAccountContextService contextService;
  private final GoogleAdsApiClient apiClient;
  private final GoogleAdsAudienceSyncService syncService;
  private final GoogleAdsAudiencePersistenceService persistenceService;
  private final AdActivityLogService activityLogService;

  public AdAudienceResponse create(
      Long companyId,
      Long accountId,
      CreateGoogleAudienceRequest request,
      Long actorUserId) {
    var context = contextService.requireManaged(companyId, accountId);
    if (context.managerAccount()) {
      throw new BadRequestException(
          "Google Ads manager accounts cannot directly own audience lists");
    }

    String uploadKeyType =
        request.uploadKeyType() == null || request.uploadKeyType().isBlank()
            ? "CONTACT_INFO"
            : request.uploadKeyType().trim().toUpperCase();
    if (!UPLOAD_KEY_TYPES.contains(uploadKeyType)) {
      throw new BadRequestException(
          "Google Customer Match upload key type must be CONTACT_INFO, CRM_ID, or MOBILE_ADVERTISING_ID");
    }

    Map<String, Object> create = new LinkedHashMap<>();
    create.put("name", request.name().trim());
    if (request.description() != null && !request.description().isBlank()) {
      create.put("description", request.description().trim());
    }
    create.put(
        "membershipLifeSpan",
        request.membershipLifeSpan() == null ? 30 : request.membershipLifeSpan());
    create.put(
        "crmBasedUserList",
        Map.of(
            "uploadKeyType", uploadKeyType,
            "dataSourceType", "FIRST_PARTY"));

    var response =
        apiClient.mutate(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            "userLists",
            List.of(Map.of("create", create)));

    String externalId = suffix(apiClient.firstMutatedResourceName(response));
    var row = syncService.audience(context, externalId);
    AdAudienceResponse after =
        AdAudienceResponse.from(
            persistenceService.persistOne(companyId, accountId, row));

    activityLogService.record(
        companyId,
        accountId,
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_AUDIENCE_CREATED",
        "AUDIENCE",
        externalId,
        null,
        after,
        Map.of("uploadKeyType", uploadKeyType));
    return after;
  }

  private String suffix(String resourceName) {
    if (resourceName == null || resourceName.isBlank()) {
      throw new BadRequestException("Google Ads did not return the created audience");
    }
    int slash = resourceName.lastIndexOf('/');
    if (slash < 0 || slash == resourceName.length() - 1) {
      throw new BadRequestException("Invalid Google Ads audience resource name");
    }
    return resourceName.substring(slash + 1);
  }
}
