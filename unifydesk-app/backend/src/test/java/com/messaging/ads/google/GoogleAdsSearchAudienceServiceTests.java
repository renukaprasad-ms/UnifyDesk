package com.messaging.ads.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdAudience;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.entity.AdProviderConnection;
import com.messaging.ads.entity.AdSet;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.repository.AdAudienceRepository;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.ads.repository.AdSetRepository;
import com.messaging.ads.service.AdActivityLogService;
import com.messaging.common.exception.BadRequestException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoogleAdsSearchAudienceServiceTests {

  @Mock private AdAudienceRepository audienceRepository;
  @Mock private AdCampaignRepository campaignRepository;
  @Mock private AdSetRepository adSetRepository;
  @Mock private GoogleAdsAccountContextService contextService;
  @Mock private GoogleAdsApiClient apiClient;
  @Mock private AdActivityLogService activityLogService;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private GoogleAdsSearchAudienceService service;

  @BeforeEach
  void setUp() {
    service =
        new GoogleAdsSearchAudienceService(
            audienceRepository,
            campaignRepository,
            adSetRepository,
            contextService,
            apiClient,
            objectMapper,
            activityLogService);
  }

  @Test
  void defaultsSearchAudienceModeToObservation() {
    AdAudience audience = searchEligibleAudience(101L, "501", 20L);
    when(audienceRepository.findByIdAndCompanyId(101L, 10L))
        .thenReturn(Optional.of(audience));

    var result =
        service.validateSelection(10L, 20L, List.of(101L), List.of(), null);

    assertThat(result.mode()).isEqualTo("OBSERVATION");
    assertThat(result.includedCount()).isEqualTo(1);
  }

  @Test
  void rejectsAudienceThatIsNotEligibleForSearch() {
    AdAudience audience = searchEligibleAudience(101L, "501", 20L);
    audience.setDeliveryStatusJson(
        """
        {"eligibleForSearch":false,"accountStatus":"ENABLED"}
        """);
    when(audienceRepository.findByIdAndCompanyId(101L, 10L))
        .thenReturn(Optional.of(audience));

    assertThatThrownBy(
            () ->
                service.validateSelection(
                    10L, 20L, List.of(101L), List.of(), "OBSERVATION"))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("not eligible for Search");
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void appliesObservationAndCreatesPositiveAndNegativeUserLists() throws Exception {
    AdSet adGroup = new AdSet();
    adGroup.setId(40L);
    adGroup.setCompanyId(10L);
    adGroup.setAdAccountId(20L);
    adGroup.setCampaignId(30L);
    adGroup.setProvider(AdProvider.GOOGLE_ADS);
    adGroup.setExternalAdSetId("888");
    adGroup.setAccessible(true);
    when(adSetRepository.findByIdAndCompanyId(40L, 10L))
        .thenReturn(Optional.of(adGroup));

    AdCampaign campaign = new AdCampaign();
    campaign.setId(30L);
    campaign.setCompanyId(10L);
    campaign.setAdAccountId(20L);
    campaign.setProvider(AdProvider.GOOGLE_ADS);
    campaign.setObjective("SEARCH");
    campaign.setExternalCampaignId("777");
    campaign.setAccessible(true);
    when(campaignRepository.findByIdAndCompanyId(30L, 10L))
        .thenReturn(Optional.of(campaign));

    AdAudience included = searchEligibleAudience(101L, "501", 20L);
    AdAudience excluded = searchEligibleAudience(102L, "502", 20L);
    when(audienceRepository.findByIdAndCompanyId(101L, 10L))
        .thenReturn(Optional.of(included));
    when(audienceRepository.findByIdAndCompanyId(102L, 10L))
        .thenReturn(Optional.of(excluded));

    AdAccount account = new AdAccount();
    account.setId(20L);
    account.setCompanyId(10L);
    account.setProvider(AdProvider.GOOGLE_ADS);
    account.setExternalAccountId("1234567890");
    account.setManaged(true);
    account.setAccessible(true);
    var context =
        new GoogleAdsAccountContextService.Context(
            account, new AdProviderConnection(), "access-token", null, false, false);
    when(contextService.requireManaged(10L, 20L)).thenReturn(context);

    when(apiClient.customerResourceName("1234567890", "adGroups", "888"))
        .thenReturn("customers/1234567890/adGroups/888");
    when(apiClient.customerResourceName("1234567890", "userLists", "501"))
        .thenReturn("customers/1234567890/userLists/501");
    when(apiClient.customerResourceName("1234567890", "userLists", "502"))
        .thenReturn("customers/1234567890/userLists/502");

    when(apiClient.searchRows(
            eq("access-token"), eq("1234567890"), eq(null), anyString()))
        .thenAnswer(
            invocation -> {
              String query = invocation.getArgument(3);
              if (query.contains("targeting_setting")) {
                return List.of(
                    objectMapper.readTree(
                        """
                        {"adGroup":{"targetingSetting":{"targetRestrictions":[
                          {"targetingDimension":"CONTENT","bidOnly":false}
                        ]}}}
                        """));
              }
              return List.of();
            });

    service.apply(
        10L,
        40L,
        List.of(101L),
        List.of(102L),
        "OBSERVATION",
        99L);

    ArgumentCaptor<List> operations = ArgumentCaptor.forClass(List.class);
    verify(apiClient)
        .mutate(
            eq("access-token"),
            eq("1234567890"),
            eq(null),
            eq("adGroupCriteria"),
            operations.capture());

    assertThat(operations.getValue()).hasSize(2);
    Map<String, Object> firstCreate =
        (Map<String, Object>)
            ((Map<String, Object>) operations.getValue().getFirst()).get("create");
    Map<String, Object> secondCreate =
        (Map<String, Object>)
            ((Map<String, Object>) operations.getValue().get(1)).get("create");

    assertThat(firstCreate.get("negative")).isEqualTo(false);
    assertThat(secondCreate.get("negative")).isEqualTo(true);

    ArgumentCaptor<List> adGroupOperations = ArgumentCaptor.forClass(List.class);
    verify(apiClient)
        .mutate(
            eq("access-token"),
            eq("1234567890"),
            eq(null),
            eq("adGroups"),
            adGroupOperations.capture());

    Map<String, Object> update =
        (Map<String, Object>)
            ((Map<String, Object>) adGroupOperations.getValue().getFirst()).get("update");
    Map<String, Object> targetingSetting =
        (Map<String, Object>) update.get("targetingSetting");
    List<Map<String, Object>> restrictions =
        (List<Map<String, Object>>) targetingSetting.get("targetRestrictions");
    assertThat(restrictions)
        .anySatisfy(
            restriction -> {
              assertThat(restriction.get("targetingDimension")).isEqualTo("CONTENT");
              assertThat(restriction.get("bidOnly")).isEqualTo(false);
            });
    assertThat(restrictions)
        .anySatisfy(
            restriction -> {
              assertThat(restriction.get("targetingDimension")).isEqualTo("AUDIENCE");
              assertThat(restriction.get("bidOnly")).isEqualTo(true);
            });
  }

  @Test
  void targetingSnapshotMapsKnownListsAndPreservesProviderOnlyCriteria() throws Exception {
    AdSet adGroup = new AdSet();
    adGroup.setId(40L);
    adGroup.setCompanyId(10L);
    adGroup.setAdAccountId(20L);
    adGroup.setCampaignId(30L);
    adGroup.setProvider(AdProvider.GOOGLE_ADS);
    adGroup.setExternalAdSetId("888");
    adGroup.setAccessible(true);
    when(adSetRepository.findByIdAndCompanyId(40L, 10L))
        .thenReturn(Optional.of(adGroup));

    AdCampaign campaign = new AdCampaign();
    campaign.setId(30L);
    campaign.setCompanyId(10L);
    campaign.setAdAccountId(20L);
    campaign.setProvider(AdProvider.GOOGLE_ADS);
    campaign.setObjective("SEARCH");
    campaign.setExternalCampaignId("777");
    campaign.setAccessible(true);
    when(campaignRepository.findByIdAndCompanyId(30L, 10L))
        .thenReturn(Optional.of(campaign));

    AdAudience known = searchEligibleAudience(101L, "501", 20L);
    when(audienceRepository.findByCompanyIdAndAdAccountIdOrderByProviderUpdatedAtDesc(
            10L, 20L))
        .thenReturn(List.of(known));

    AdAccount account = new AdAccount();
    account.setId(20L);
    account.setExternalAccountId("1234567890");
    var context =
        new GoogleAdsAccountContextService.Context(
            account, new AdProviderConnection(), "access-token", null, false, false);
    when(contextService.requireManaged(10L, 20L)).thenReturn(context);

    when(apiClient.searchRows(
            eq("access-token"), eq("1234567890"), eq(null), anyString()))
        .thenAnswer(
            invocation -> {
              String query = invocation.getArgument(3);
              if (query.contains("targeting_setting")) {
                return List.of(
                    objectMapper.readTree(
                        """
                        {"adGroup":{"targetingSetting":{"targetRestrictions":[
                          {"targetingDimension":"AUDIENCE","bidOnly":true},
                          {"targetingDimension":"CONTENT","bidOnly":false}
                        ]}}}
                        """));
              }
              return List.of(
                  objectMapper.readTree(
                      """
                      {"adGroupCriterion":{
                        "resourceName":"customers/1234567890/adGroupCriteria/888~1",
                        "negative":false,
                        "userList":{"userList":"customers/1234567890/userLists/501"}
                      }}
                      """),
                  objectMapper.readTree(
                      """
                      {"adGroupCriterion":{
                        "resourceName":"customers/1234567890/adGroupCriteria/888~2",
                        "negative":true,
                        "userList":{"userList":"customers/1234567890/userLists/999"}
                      }}
                      """));
            });

    var result = service.targeting(10L, 40L);

    assertThat(result.mode()).isEqualTo("OBSERVATION");
    assertThat(result.includedAudienceIds()).containsExactly(101L);
    assertThat(result.providerOnlyExcludedExternalIds()).containsExactly("999");
    assertThat(result.preservedTargetingDimensions()).containsExactly("CONTENT");
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void nativeReplaceRemovesOnlyMappedDeselectedCriteria() throws Exception {
    AdSet adGroup = new AdSet();
    adGroup.setId(40L);
    adGroup.setCompanyId(10L);
    adGroup.setAdAccountId(20L);
    adGroup.setCampaignId(30L);
    adGroup.setProvider(AdProvider.GOOGLE_ADS);
    adGroup.setExternalAdSetId("888");
    adGroup.setAccessible(true);
    when(adSetRepository.findByIdAndCompanyId(40L, 10L))
        .thenReturn(Optional.of(adGroup));

    AdCampaign campaign = new AdCampaign();
    campaign.setId(30L);
    campaign.setCompanyId(10L);
    campaign.setAdAccountId(20L);
    campaign.setProvider(AdProvider.GOOGLE_ADS);
    campaign.setObjective("SEARCH");
    campaign.setAccessible(true);
    when(campaignRepository.findByIdAndCompanyId(30L, 10L))
        .thenReturn(Optional.of(campaign));

    AdAudience known = searchEligibleAudience(101L, "501", 20L);
    when(audienceRepository.findByCompanyIdAndAdAccountIdOrderByProviderUpdatedAtDesc(
            10L, 20L))
        .thenReturn(List.of(known));

    AdAccount account = new AdAccount();
    account.setId(20L);
    account.setExternalAccountId("1234567890");
    var context =
        new GoogleAdsAccountContextService.Context(
            account, new AdProviderConnection(), "access-token", null, false, false);
    when(contextService.requireManaged(10L, 20L)).thenReturn(context);
    when(apiClient.customerResourceName("1234567890", "adGroups", "888"))
        .thenReturn("customers/1234567890/adGroups/888");

    when(apiClient.searchRows(
            eq("access-token"), eq("1234567890"), eq(null), anyString()))
        .thenAnswer(
            invocation -> {
              String query = invocation.getArgument(3);
              if (query.contains("targeting_setting")) {
                return List.of(
                    objectMapper.readTree(
                        """
                        {"adGroup":{"targetingSetting":{"targetRestrictions":[]}}}
                        """));
              }
              return List.of(
                  objectMapper.readTree(
                      """
                      {"adGroupCriterion":{
                        "resourceName":"customers/1234567890/adGroupCriteria/888~1",
                        "negative":false,
                        "userList":{"userList":"customers/1234567890/userLists/501"}
                      }}
                      """),
                  objectMapper.readTree(
                      """
                      {"adGroupCriterion":{
                        "resourceName":"customers/1234567890/adGroupCriteria/888~2",
                        "negative":false,
                        "userList":{"userList":"customers/1234567890/userLists/999"}
                      }}
                      """));
            });

    service.replace(10L, 40L, List.of(), List.of(), "OBSERVATION", 99L);

    ArgumentCaptor<List> operations = ArgumentCaptor.forClass(List.class);
    verify(apiClient)
        .mutate(
            eq("access-token"),
            eq("1234567890"),
            eq(null),
            eq("adGroupCriteria"),
            operations.capture());

    assertThat(operations.getValue()).containsExactly(
        Map.of("remove", "customers/1234567890/adGroupCriteria/888~1"));
  }

  private AdAudience searchEligibleAudience(
      Long id,
      String externalId,
      Long accountId) {
    AdAudience audience = new AdAudience();
    audience.setId(id);
    audience.setCompanyId(10L);
    audience.setAdAccountId(accountId);
    audience.setProvider(AdProvider.GOOGLE_ADS);
    audience.setExternalAudienceId(externalId);
    audience.setName("Audience " + id);
    audience.setAccessible(true);
    audience.setDeliveryStatusJson(
        """
        {"eligibleForSearch":true,"accountStatus":"ENABLED"}
        """);
    return audience;
  }
}
