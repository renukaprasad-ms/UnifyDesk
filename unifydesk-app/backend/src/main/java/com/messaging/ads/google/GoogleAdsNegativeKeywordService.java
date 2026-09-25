package com.messaging.ads.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.CreateGoogleNegativeKeywordListRequest;
import com.messaging.ads.google.dto.GoogleNegativeKeywordBatchRequest;
import com.messaging.ads.google.dto.GoogleNegativeKeywordInput;
import com.messaging.ads.google.dto.GoogleNegativeKeywordListResponse;
import com.messaging.ads.google.dto.GoogleNegativeKeywordMutationResponse;
import com.messaging.ads.google.dto.GoogleNegativeKeywordResponse;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.ads.service.AdActivityLogService;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GoogleAdsNegativeKeywordService {

  private static final Set<String> MATCH_TYPES = Set.of("BROAD", "PHRASE", "EXACT");

  private final GoogleAdsAccountContextService contextService;
  private final GoogleAdsApiClient apiClient;
  private final AdCampaignRepository campaignRepository;
  private final AdActivityLogService activityLogService;

  public List<GoogleNegativeKeywordResponse> campaignNegatives(
      Long companyId, Long campaignId) {
    CampaignContext context = campaignContext(companyId, campaignId);
    return campaignNegatives(context.google(), context.campaign());
  }

  public GoogleNegativeKeywordMutationResponse addCampaignNegatives(
      Long companyId,
      Long campaignId,
      GoogleNegativeKeywordBatchRequest request,
      Long actorUserId) {
    CampaignContext context = campaignContext(companyId, campaignId);
    List<GoogleNegativeKeywordInput> requested = normalize(request.keywords());
    List<GoogleNegativeKeywordResponse> existing =
        campaignNegatives(context.google(), context.campaign());
    Set<String> existingKeys = new HashSet<>();
    existing.forEach(value -> existingKeys.add(key(value.text(), value.matchType())));

    String customerId = context.google().account().getExternalAccountId();
    String campaignResource =
        apiClient.customerResourceName(
            customerId, "campaigns", context.campaign().getExternalCampaignId());
    List<Map<String, Object>> operations = new ArrayList<>();
    for (GoogleNegativeKeywordInput keyword : requested) {
      if (existingKeys.contains(key(keyword.text(), keyword.matchType()))) continue;
      operations.add(
          Map.of(
              "create",
              Map.of(
                  "campaign", campaignResource,
                  "negative", true,
                  "keyword",
                      Map.of(
                          "text", keyword.text(),
                          "matchType", keyword.matchType()))));
    }

    if (!operations.isEmpty()) {
      apiClient.mutate(
          context.google().accessToken(),
          customerId,
          context.google().loginCustomerId(),
          "campaignCriteria",
          operations);
    }

    int reused = requested.size() - operations.size();
    activityLogService.record(
        companyId,
        context.campaign().getAdAccountId(),
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_CAMPAIGN_NEGATIVE_KEYWORDS_ADDED",
        "CAMPAIGN",
        context.campaign().getExternalCampaignId(),
        null,
        null,
        Map.of(
            "requestedCount", requested.size(),
            "createdCount", operations.size(),
            "reusedCount", reused));

    return new GoogleNegativeKeywordMutationResponse(
        requested.size(), operations.size(), reused);
  }

  public boolean removeCampaignNegative(
      Long companyId,
      Long campaignId,
      String criterionId,
      Long actorUserId) {
    CampaignContext context = campaignContext(companyId, campaignId);
    GoogleNegativeKeywordResponse keyword =
        campaignNegatives(context.google(), context.campaign()).stream()
            .filter(value -> value.criterionId().equals(criterionId))
            .findFirst()
            .orElseThrow(() -> new NotFoundException("Google campaign negative keyword not found"));

    apiClient.mutate(
        context.google().accessToken(),
        context.google().account().getExternalAccountId(),
        context.google().loginCustomerId(),
        "campaignCriteria",
        List.of(Map.of("remove", keyword.resourceName())));

    activityLogService.record(
        companyId,
        context.campaign().getAdAccountId(),
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_CAMPAIGN_NEGATIVE_KEYWORD_REMOVED",
        "CAMPAIGN",
        context.campaign().getExternalCampaignId(),
        null,
        null,
        Map.of(
            "criterionId", criterionId,
            "text", keyword.text(),
            "matchType", keyword.matchType()));
    return true;
  }

  public List<GoogleNegativeKeywordListResponse> sharedLists(
      Long companyId, Long accountId, Long campaignId) {
    var context = contextService.requireManaged(companyId, accountId);
    Set<String> attached =
        campaignId == null
            ? Set.of()
            : attachedSharedSetIds(
                context, requireCampaignForAccount(companyId, campaignId, accountId));

    String query =
        "SELECT shared_set.resource_name, shared_set.id, shared_set.name, "
            + "shared_set.type, shared_set.status, shared_set.member_count, "
            + "shared_set.reference_count FROM shared_set "
            + "WHERE shared_set.type = 'NEGATIVE_KEYWORDS' "
            + "AND shared_set.status != 'REMOVED'";

    List<GoogleNegativeKeywordListResponse> result = new ArrayList<>();
    for (JsonNode row :
        apiClient.searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            query)) {
      JsonNode shared = row.path("sharedSet");
      String id = shared.path("id").asText(null);
      if (!hasText(id)) continue;
      result.add(
          new GoogleNegativeKeywordListResponse(
              shared.path("resourceName").asText(),
              id,
              shared.path("name").asText(),
              shared.path("status").asText(),
              shared.path("memberCount").asLong(0),
              shared.path("referenceCount").asLong(0),
              attached.contains(id),
              List.of()));
    }
    return List.copyOf(result);
  }

  public GoogleNegativeKeywordListResponse createSharedList(
      Long companyId,
      Long accountId,
      CreateGoogleNegativeKeywordListRequest request,
      Long actorUserId) {
    var context = contextService.requireManaged(companyId, accountId);
    String name = request.name().trim();
    ensureUniqueSharedSetName(context, name);

    JsonNode response =
        apiClient.mutate(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            "sharedSets",
            List.of(
                Map.of(
                    "create",
                    Map.of(
                        "name", name,
                        "type", "NEGATIVE_KEYWORDS"))));
    String resourceName = apiClient.firstMutatedResourceName(response);
    String id = suffix(resourceName);

    List<GoogleNegativeKeywordInput> requested =
        request.keywords() == null ? List.of() : normalize(request.keywords());
    if (!requested.isEmpty()) {
      addSharedMembers(context, resourceName, requested);
    }

    activityLogService.record(
        companyId,
        accountId,
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_NEGATIVE_KEYWORD_LIST_CREATED",
        "SHARED_SET",
        id,
        null,
        null,
        Map.of("name", name, "memberCount", requested.size()));

    return sharedList(companyId, accountId, id, null);
  }

  public GoogleNegativeKeywordListResponse sharedList(
      Long companyId, Long accountId, String sharedSetId, Long campaignId) {
    var context = contextService.requireManaged(companyId, accountId);
    String id = numericId(sharedSetId, "shared set");
    String query =
        "SELECT shared_set.resource_name, shared_set.id, shared_set.name, "
            + "shared_set.type, shared_set.status, shared_set.member_count, "
            + "shared_set.reference_count FROM shared_set "
            + "WHERE shared_set.id = "
            + id
            + " AND shared_set.type = 'NEGATIVE_KEYWORDS' LIMIT 1";
    List<JsonNode> rows =
        apiClient.searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            query);
    if (rows.isEmpty()) {
      throw new NotFoundException("Google negative keyword list not found");
    }

    JsonNode shared = rows.getFirst().path("sharedSet");
    boolean attached =
        campaignId != null
            && attachedSharedSetIds(
                    context, requireCampaignForAccount(companyId, campaignId, accountId))
                .contains(id);
    return new GoogleNegativeKeywordListResponse(
        shared.path("resourceName").asText(),
        id,
        shared.path("name").asText(),
        shared.path("status").asText(),
        shared.path("memberCount").asLong(0),
        shared.path("referenceCount").asLong(0),
        attached,
        sharedMembers(context, id));
  }

  public List<GoogleNegativeKeywordResponse> sharedMembers(
      Long companyId, Long accountId, String sharedSetId) {
    var context = contextService.requireManaged(companyId, accountId);
    return sharedMembers(context, numericId(sharedSetId, "shared set"));
  }

  public GoogleNegativeKeywordMutationResponse addSharedMembers(
      Long companyId,
      Long accountId,
      String sharedSetId,
      GoogleNegativeKeywordBatchRequest request,
      Long actorUserId) {
    var context = contextService.requireManaged(companyId, accountId);
    String id = numericId(sharedSetId, "shared set");
    GoogleNegativeKeywordListResponse list = sharedList(companyId, accountId, id, null);
    List<GoogleNegativeKeywordInput> requested = normalize(request.keywords());
    List<GoogleNegativeKeywordResponse> existing = sharedMembers(context, id);
    Set<String> keys = new HashSet<>();
    existing.forEach(value -> keys.add(key(value.text(), value.matchType())));

    List<GoogleNegativeKeywordInput> missing =
        requested.stream()
            .filter(value -> !keys.contains(key(value.text(), value.matchType())))
            .toList();
    if (!missing.isEmpty()) {
      addSharedMembers(context, list.resourceName(), missing);
    }

    activityLogService.record(
        companyId,
        accountId,
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_NEGATIVE_KEYWORD_LIST_MEMBERS_ADDED",
        "SHARED_SET",
        id,
        null,
        null,
        Map.of(
            "requestedCount", requested.size(),
            "createdCount", missing.size(),
            "reusedCount", requested.size() - missing.size()));

    return new GoogleNegativeKeywordMutationResponse(
        requested.size(), missing.size(), requested.size() - missing.size());
  }

  public boolean removeSharedMember(
      Long companyId,
      Long accountId,
      String sharedSetId,
      String criterionId,
      Long actorUserId) {
    var context = contextService.requireManaged(companyId, accountId);
    String id = numericId(sharedSetId, "shared set");
    String criterion = numericId(criterionId, "shared criterion");
    GoogleNegativeKeywordResponse member =
        sharedMembers(context, id).stream()
            .filter(value -> value.criterionId().equals(criterion))
            .findFirst()
            .orElseThrow(() -> new NotFoundException("Google shared negative keyword not found"));

    apiClient.mutate(
        context.accessToken(),
        context.account().getExternalAccountId(),
        context.loginCustomerId(),
        "sharedCriteria",
        List.of(Map.of("remove", member.resourceName())));

    activityLogService.record(
        companyId,
        accountId,
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_NEGATIVE_KEYWORD_LIST_MEMBER_REMOVED",
        "SHARED_SET",
        id,
        null,
        null,
        Map.of("criterionId", criterion, "text", member.text()));
    return true;
  }

  public GoogleNegativeKeywordListResponse attachSharedList(
      Long companyId,
      Long campaignId,
      String sharedSetId,
      Long actorUserId) {
    CampaignContext context = campaignContext(companyId, campaignId);
    String id = numericId(sharedSetId, "shared set");
    GoogleNegativeKeywordListResponse list =
        sharedList(companyId, context.campaign().getAdAccountId(), id, campaignId);
    if (!list.attachedToCampaign()) {
      String campaignResource =
          apiClient.customerResourceName(
              context.google().account().getExternalAccountId(),
              "campaigns",
              context.campaign().getExternalCampaignId());
      apiClient.mutate(
          context.google().accessToken(),
          context.google().account().getExternalAccountId(),
          context.google().loginCustomerId(),
          "campaignSharedSets",
          List.of(
              Map.of(
                  "create",
                  Map.of(
                      "campaign", campaignResource,
                      "sharedSet", list.resourceName()))));
    }

    activityLogService.record(
        companyId,
        context.campaign().getAdAccountId(),
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_NEGATIVE_KEYWORD_LIST_ATTACHED",
        "CAMPAIGN",
        context.campaign().getExternalCampaignId(),
        null,
        null,
        Map.of("sharedSetId", id, "name", list.name()));
    return sharedList(
        companyId, context.campaign().getAdAccountId(), id, campaignId);
  }

  public GoogleNegativeKeywordListResponse detachSharedList(
      Long companyId,
      Long campaignId,
      String sharedSetId,
      Long actorUserId) {
    CampaignContext context = campaignContext(companyId, campaignId);
    String id = numericId(sharedSetId, "shared set");
    String query =
        "SELECT campaign_shared_set.resource_name, shared_set.id "
            + "FROM campaign_shared_set "
            + "WHERE campaign.id = "
            + numericId(context.campaign().getExternalCampaignId(), "campaign")
            + " AND shared_set.id = "
            + id
            + " AND campaign_shared_set.status != 'REMOVED' LIMIT 1";
    List<JsonNode> rows =
        apiClient.searchRows(
            context.google().accessToken(),
            context.google().account().getExternalAccountId(),
            context.google().loginCustomerId(),
            query);
    if (!rows.isEmpty()) {
      String resourceName =
          rows.getFirst().path("campaignSharedSet").path("resourceName").asText(null);
      if (hasText(resourceName)) {
        apiClient.mutate(
            context.google().accessToken(),
            context.google().account().getExternalAccountId(),
            context.google().loginCustomerId(),
            "campaignSharedSets",
            List.of(Map.of("remove", resourceName)));
      }
    }

    activityLogService.record(
        companyId,
        context.campaign().getAdAccountId(),
        AdProvider.GOOGLE_ADS,
        actorUserId,
        "GOOGLE_NEGATIVE_KEYWORD_LIST_DETACHED",
        "CAMPAIGN",
        context.campaign().getExternalCampaignId(),
        null,
        null,
        Map.of("sharedSetId", id));
    return sharedList(
        companyId, context.campaign().getAdAccountId(), id, campaignId);
  }

  public ValidationResult validateLaunchConfiguration(
      Long companyId, Long accountId, JsonNode override) {
    List<GoogleNegativeKeywordInput> negatives = overrideKeywords(override);
    List<String> sharedSetIds = overrideSharedSetIds(override);
    var context = contextService.requireManaged(companyId, accountId);
    Set<String> available =
        sharedLists(companyId, accountId, null).stream()
            .map(GoogleNegativeKeywordListResponse::id)
            .collect(java.util.stream.Collectors.toSet());
    for (String id : sharedSetIds) {
      if (!available.contains(id)) {
        throw new BadRequestException(
            "Google negative keyword list is unavailable for this account: " + id);
      }
    }
    return new ValidationResult(negatives.size(), sharedSetIds.size());
  }

  public LaunchApplyResult applyLaunchConfiguration(
      Long companyId,
      Long campaignId,
      JsonNode override,
      Long actorUserId) {
    CampaignContext context = campaignContext(companyId, campaignId);
    List<GoogleNegativeKeywordInput> negatives = overrideKeywords(override);
    List<String> sharedSetIds = overrideSharedSetIds(override);

    GoogleNegativeKeywordMutationResponse negativeResult =
        negatives.isEmpty()
            ? new GoogleNegativeKeywordMutationResponse(0, 0, 0)
            : addCampaignNegatives(
                companyId,
                campaignId,
                new GoogleNegativeKeywordBatchRequest(negatives),
                actorUserId);

    int attached = 0;
    int reused = 0;
    Set<String> current = attachedSharedSetIds(context.google(), context.campaign());
    for (String id : sharedSetIds) {
      if (current.contains(id)) {
        reused++;
      } else {
        attachSharedList(companyId, campaignId, id, actorUserId);
        attached++;
      }
    }
    return new LaunchApplyResult(
        negativeResult.createdCount(),
        negativeResult.reusedCount(),
        attached,
        reused);
  }

  private List<GoogleNegativeKeywordResponse> campaignNegatives(
      GoogleAdsAccountContextService.Context context, AdCampaign campaign) {
    String query =
        "SELECT campaign_criterion.resource_name, campaign_criterion.criterion_id, "
            + "campaign_criterion.status, campaign_criterion.negative, "
            + "campaign_criterion.keyword.text, campaign_criterion.keyword.match_type "
            + "FROM campaign_criterion WHERE campaign.id = "
            + numericId(campaign.getExternalCampaignId(), "campaign")
            + " AND campaign_criterion.type = 'KEYWORD' "
            + "AND campaign_criterion.negative = TRUE "
            + "AND campaign_criterion.status != 'REMOVED'";
    List<GoogleNegativeKeywordResponse> result = new ArrayList<>();
    for (JsonNode row :
        apiClient.searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            query)) {
      JsonNode criterion = row.path("campaignCriterion");
      JsonNode keyword = criterion.path("keyword");
      String text = keyword.path("text").asText(null);
      String criterionId = criterion.path("criterionId").asText(null);
      if (!hasText(text) || !hasText(criterionId)) continue;
      result.add(
          new GoogleNegativeKeywordResponse(
              criterion.path("resourceName").asText(),
              criterionId,
              text,
              keyword.path("matchType").asText(),
              criterion.path("status").asText()));
    }
    return List.copyOf(result);
  }

  private List<GoogleNegativeKeywordResponse> sharedMembers(
      GoogleAdsAccountContextService.Context context, String sharedSetId) {
    String query =
        "SELECT shared_criterion.resource_name, shared_criterion.criterion_id, "
            + "shared_criterion.keyword.text, shared_criterion.keyword.match_type, "
            + "shared_criterion.type FROM shared_criterion "
            + "WHERE shared_set.id = "
            + numericId(sharedSetId, "shared set")
            + " AND shared_criterion.type = 'KEYWORD'";
    List<GoogleNegativeKeywordResponse> result = new ArrayList<>();
    for (JsonNode row :
        apiClient.searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            query)) {
      JsonNode criterion = row.path("sharedCriterion");
      JsonNode keyword = criterion.path("keyword");
      String text = keyword.path("text").asText(null);
      String criterionId = criterion.path("criterionId").asText(null);
      if (!hasText(text) || !hasText(criterionId)) continue;
      result.add(
          new GoogleNegativeKeywordResponse(
              criterion.path("resourceName").asText(),
              criterionId,
              text,
              keyword.path("matchType").asText(),
              "ENABLED"));
    }
    return List.copyOf(result);
  }

  private void addSharedMembers(
      GoogleAdsAccountContextService.Context context,
      String sharedSetResourceName,
      List<GoogleNegativeKeywordInput> keywords) {
    List<Map<String, Object>> operations = new ArrayList<>();
    for (GoogleNegativeKeywordInput keyword : keywords) {
      operations.add(
          Map.of(
              "create",
              Map.of(
                  "sharedSet", sharedSetResourceName,
                  "keyword",
                      Map.of(
                          "text", keyword.text(),
                          "matchType", keyword.matchType()))));
    }
    apiClient.mutate(
        context.accessToken(),
        context.account().getExternalAccountId(),
        context.loginCustomerId(),
        "sharedCriteria",
        operations);
  }

  private Set<String> attachedSharedSetIds(
      GoogleAdsAccountContextService.Context context, AdCampaign campaign) {
    String query =
        "SELECT campaign_shared_set.resource_name, campaign_shared_set.status, "
            + "shared_set.id, shared_set.type FROM campaign_shared_set "
            + "WHERE campaign.id = "
            + numericId(campaign.getExternalCampaignId(), "campaign")
            + " AND shared_set.type = 'NEGATIVE_KEYWORDS' "
            + "AND campaign_shared_set.status != 'REMOVED'";
    Set<String> result = new HashSet<>();
    for (JsonNode row :
        apiClient.searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            query)) {
      String id = row.path("sharedSet").path("id").asText(null);
      if (hasText(id)) result.add(id);
    }
    return Set.copyOf(result);
  }

  private void ensureUniqueSharedSetName(
      GoogleAdsAccountContextService.Context context, String name) {
    String query =
        "SELECT shared_set.id FROM shared_set "
            + "WHERE shared_set.type = 'NEGATIVE_KEYWORDS' "
            + "AND shared_set.status != 'REMOVED' "
            + "AND shared_set.name = '"
            + gaql(name)
            + "' LIMIT 1";
    if (!apiClient
        .searchRows(
            context.accessToken(),
            context.account().getExternalAccountId(),
            context.loginCustomerId(),
            query)
        .isEmpty()) {
      throw new BadRequestException(
          "An active Google negative keyword list with this name already exists");
    }
  }

  private CampaignContext campaignContext(Long companyId, Long campaignId) {
    AdCampaign campaign =
        campaignRepository
            .findByIdAndCompanyId(campaignId, companyId)
            .orElseThrow(() -> new NotFoundException("Advertising campaign not found"));
    if (campaign.getProvider() != AdProvider.GOOGLE_ADS
        || !campaign.isAccessible()
        || !"SEARCH".equalsIgnoreCase(campaign.getObjective())) {
      throw new BadRequestException(
          "Google negative keywords are available only for manageable Search campaigns");
    }
    var google = contextService.requireManaged(companyId, campaign.getAdAccountId());
    return new CampaignContext(campaign, google);
  }

  private AdCampaign requireCampaignForAccount(
      Long companyId, Long campaignId, Long accountId) {
    AdCampaign campaign =
        campaignRepository
            .findByIdAndCompanyId(campaignId, companyId)
            .orElseThrow(() -> new NotFoundException("Advertising campaign not found"));
    if (campaign.getProvider() != AdProvider.GOOGLE_ADS
        || !campaign.isAccessible()
        || !campaign.getAdAccountId().equals(accountId)) {
      throw new BadRequestException("Google campaign does not belong to this ad account");
    }
    return campaign;
  }

  private List<GoogleNegativeKeywordInput> normalize(
      List<GoogleNegativeKeywordInput> keywords) {
    if (keywords == null || keywords.isEmpty()) {
      throw new BadRequestException("At least one negative keyword is required");
    }
    Map<String, GoogleNegativeKeywordInput> distinct = new LinkedHashMap<>();
    for (GoogleNegativeKeywordInput value : keywords) {
      if (value == null) continue;
      String text = value.text() == null ? "" : value.text().trim();
      String matchType =
          value.matchType() == null
              ? ""
              : value.matchType().trim().toUpperCase(Locale.ROOT);
      validateKeyword(text, matchType);
      distinct.putIfAbsent(
          key(text, matchType),
          new GoogleNegativeKeywordInput(text, matchType));
    }
    if (distinct.isEmpty()) {
      throw new BadRequestException("At least one valid negative keyword is required");
    }
    return List.copyOf(distinct.values());
  }

  private List<GoogleNegativeKeywordInput> overrideKeywords(JsonNode override) {
    JsonNode values = override == null ? null : override.get("negativeKeywords");
    if (values == null || values.isNull()) return List.of();
    if (!values.isArray()) {
      throw new BadRequestException("Google negativeKeywords must be an array");
    }
    List<GoogleNegativeKeywordInput> result = new ArrayList<>();
    for (JsonNode value : values) {
      if (!value.isObject()) {
        throw new BadRequestException("Google negative keyword entry must be an object");
      }
      result.add(
          new GoogleNegativeKeywordInput(
              value.path("text").asText(""),
              value.path("matchType").asText("")));
    }
    return result.isEmpty() ? List.of() : normalize(result);
  }

  private List<String> overrideSharedSetIds(JsonNode override) {
    JsonNode values = override == null ? null : override.get("negativeKeywordSharedSetIds");
    if (values == null || values.isNull()) return List.of();
    if (!values.isArray()) {
      throw new BadRequestException(
          "Google negativeKeywordSharedSetIds must be an array");
    }
    LinkedHashMap<String, String> distinct = new LinkedHashMap<>();
    for (JsonNode value : values) {
      String id = numericId(value.asText(""), "shared set");
      distinct.putIfAbsent(id, id);
    }
    return List.copyOf(distinct.values());
  }

  private void validateKeyword(String text, String matchType) {
    if (!hasText(text)) {
      throw new BadRequestException("Google negative keyword text is required");
    }
    if (text.length() > 80) {
      throw new BadRequestException(
          "Google negative keywords may contain at most 80 characters");
    }
    long words =
        java.util.Arrays.stream(text.trim().split("\\s+"))
            .filter(value -> !value.isBlank())
            .count();
    if (words > 10) {
      throw new BadRequestException(
          "Google negative keywords may contain at most 10 words");
    }
    if (!MATCH_TYPES.contains(matchType)) {
      throw new BadRequestException(
          "Google negative keyword match type must be BROAD, PHRASE, or EXACT");
    }
  }

  private String key(String text, String matchType) {
    return matchType.toUpperCase(Locale.ROOT)
        + "|"
        + text.trim().toLowerCase(Locale.ROOT);
  }

  private String numericId(String value, String label) {
    if (value == null || !value.matches("\\d+")) {
      throw new BadRequestException("Invalid Google Ads " + label + " ID");
    }
    return value;
  }

  private String suffix(String resourceName) {
    if (!hasText(resourceName)) {
      throw new BadRequestException("Google Ads did not return a resource name");
    }
    int slash = resourceName.lastIndexOf('/');
    if (slash < 0 || slash == resourceName.length() - 1) {
      throw new BadRequestException("Invalid Google Ads resource name");
    }
    return resourceName.substring(slash + 1);
  }

  private String gaql(String value) {
    return value.replace("\\", "\\\\").replace("'", "\\'");
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private record CampaignContext(
      AdCampaign campaign, GoogleAdsAccountContextService.Context google) {}

  public record ValidationResult(
      int campaignNegativeKeywordCount,
      int sharedListCount) {}

  public record LaunchApplyResult(
      int campaignNegativeKeywordsCreated,
      int campaignNegativeKeywordsReused,
      int sharedListsAttached,
      int sharedListsReused) {}
}
