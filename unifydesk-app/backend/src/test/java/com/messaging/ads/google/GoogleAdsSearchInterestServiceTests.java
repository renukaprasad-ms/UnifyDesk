package com.messaging.ads.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.entity.AdProviderConnection;
import com.messaging.ads.entity.AdSet;
import com.messaging.ads.enums.AdProvider;
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
class GoogleAdsSearchInterestServiceTests {

  @Mock private AdSetRepository adSetRepository;
  @Mock private AdCampaignRepository campaignRepository;
  @Mock private GoogleAdsAccountContextService contextService;
  @Mock private GoogleAdsApiClient apiClient;
  @Mock private AdActivityLogService activityLogService;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private GoogleAdsSearchInterestService service;

  @BeforeEach
  void setUp() {
    service =
        new GoogleAdsSearchInterestService(
            adSetRepository,
            campaignRepository,
            contextService,
            apiClient,
            activityLogService);
  }

  @Test
  void catalogReturnsOnlySearchAvailableAffinityAndInMarketSegments() throws Exception {
    var context = context(10L, 20L, "1234567890");
    when(contextService.requireManaged(10L, 20L)).thenReturn(context);
    when(apiClient.searchRows(
            eq("access-token"), eq("1234567890"), eq(null), anyString()))
        .thenReturn(
            List.of(
                objectMapper.readTree(
                    """
                    {"userInterest":{
                      "userInterestId":"501",
                      "resourceName":"customers/1234567890/userInterests/501",
                      "name":"Business Professionals",
                      "taxonomyType":"AFFINITY",
                      "launchedToAll":true
                    }}
                    """),
                objectMapper.readTree(
                    """
                    {"userInterest":{
                      "userInterestId":"502",
                      "resourceName":"customers/1234567890/userInterests/502",
                      "name":"CRM Software",
                      "taxonomyType":"IN_MARKET",
                      "launchedToAll":false,
                      "availabilities":[
                        {"channel":{
                          "availabilityMode":"CHANNEL_TYPE",
                          "advertisingChannelType":"SEARCH"
                        }}
                      ]
                    }}
                    """),
                objectMapper.readTree(
                    """
                    {"userInterest":{
                      "userInterestId":"503",
                      "resourceName":"customers/1234567890/userInterests/503",
                      "name":"Display only",
                      "taxonomyType":"AFFINITY",
                      "launchedToAll":false,
                      "availabilities":[
                        {"channel":{
                          "availabilityMode":"CHANNEL_TYPE",
                          "advertisingChannelType":"DISPLAY"
                        }}
                      ]
                    }}
                    """)));

    var result = service.catalog(10L, 20L, "", 40);

    assertThat(result.customSegmentsSupported()).isFalse();
    assertThat(result.interests())
        .extracting(item -> item.id())
        .containsExactly("502", "501");
  }

  @Test
  void validationRejectsNonSearchOrUnsupportedProviderSegment() throws Exception {
    var context = context(10L, 20L, "1234567890");
    when(contextService.requireManaged(10L, 20L)).thenReturn(context);
    when(apiClient.searchRows(
            eq("access-token"), eq("1234567890"), eq(null), anyString()))
        .thenReturn(
            List.of(
                objectMapper.readTree(
                    """
                    {"userInterest":{
                      "userInterestId":"501",
                      "resourceName":"customers/1234567890/userInterests/501",
                      "name":"Unsupported segment",
                      "taxonomyType":"VERTICAL_GEO",
                      "launchedToAll":true
                    }}
                    """)));

    assertThatThrownBy(
            () ->
                service.validateSelection(
                    10L,
                    20L,
                    List.of("customers/1234567890/userInterests/501"),
                    List.of()))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("Affinity or In-market");
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void replaceRemovesSupportedCriteriaButPreservesUnsupportedUserInterest() throws Exception {
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

    var context = context(10L, 20L, "1234567890");
    when(contextService.requireManaged(10L, 20L)).thenReturn(context);
    when(apiClient.customerResourceName("1234567890", "adGroups", "888"))
        .thenReturn("customers/1234567890/adGroups/888");

    when(apiClient.searchRows(
            eq("access-token"), eq("1234567890"), eq(null), anyString()))
        .thenAnswer(
            invocation -> {
              String query = invocation.getArgument(3);
              if (query.startsWith("SELECT user_interest.user_interest_id")) {
                return List.of();
              }
              return List.of(
                  objectMapper.readTree(
                      """
                      {
                        "adGroupCriterion":{
                          "resourceName":"customers/1234567890/adGroupCriteria/888~1",
                          "negative":false,
                          "userInterest":{
                            "userInterestCategory":"customers/1234567890/userInterests/501"
                          }
                        },
                        "userInterest":{
                          "resourceName":"customers/1234567890/userInterests/501",
                          "taxonomyType":"AFFINITY"
                        }
                      }
                      """),
                  objectMapper.readTree(
                      """
                      {
                        "adGroupCriterion":{
                          "resourceName":"customers/1234567890/adGroupCriteria/888~2",
                          "negative":false,
                          "userInterest":{
                            "userInterestCategory":"customers/1234567890/userInterests/999"
                          }
                        },
                        "userInterest":{
                          "resourceName":"customers/1234567890/userInterests/999",
                          "taxonomyType":"VERTICAL_GEO"
                        }
                      }
                      """));
            });

    service.replace(10L, 40L, List.of(), List.of(), 99L);

    ArgumentCaptor<List> operations = ArgumentCaptor.forClass(List.class);
    verify(apiClient)
        .mutate(
            eq("access-token"),
            eq("1234567890"),
            eq(null),
            eq("adGroupCriteria"),
            operations.capture());

    assertThat(operations.getValue())
        .containsExactly(
            Map.of(
                "remove",
                "customers/1234567890/adGroupCriteria/888~1"));
  }

  private GoogleAdsAccountContextService.Context context(
      Long companyId,
      Long accountId,
      String externalAccountId) {
    AdAccount account = new AdAccount();
    account.setId(accountId);
    account.setCompanyId(companyId);
    account.setProvider(AdProvider.GOOGLE_ADS);
    account.setExternalAccountId(externalAccountId);
    account.setManaged(true);
    account.setAccessible(true);
    return new GoogleAdsAccountContextService.Context(
        account,
        new AdProviderConnection(),
        "access-token",
        null,
        false,
        false);
  }
}
