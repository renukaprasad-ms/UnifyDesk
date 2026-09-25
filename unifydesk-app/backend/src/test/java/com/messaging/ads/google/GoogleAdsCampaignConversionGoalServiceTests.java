package com.messaging.ads.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.campaign.entity.ConversionGoalMapping;
import com.messaging.ads.campaign.enums.ConversionTargetType;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.enums.AdProvider;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoogleAdsCampaignConversionGoalServiceTests {

  @Mock private GoogleAdsAccountContextService contextService;
  @Mock private GoogleAdsApiClient apiClient;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private GoogleAdsCampaignConversionGoalService service;

  @BeforeEach
  void setUp() {
    service = new GoogleAdsCampaignConversionGoalService(contextService, apiClient);
  }

  @Test
  void createsCustomGoalAndAttachesItToCampaign() throws Exception {
    AdAccount account = account();
    when(contextService.requireManaged(1L, 2L))
        .thenReturn(
            new GoogleAdsAccountContextService.Context(
                account, null, "token", "9999999999", false, false));

    when(apiClient.searchRows(
            eq("token"), eq("1234567890"), eq("9999999999"), anyString()))
        .thenAnswer(
            invocation -> {
              String query = invocation.getArgument(3);
              if (query.contains("conversion_tracking_setting")) {
                return List.of(
                    objectMapper.readTree(
                        """
                        {
                          "customer": {
                            "conversionTrackingSetting": {
                              "googleAdsConversionCustomer": "customers/5555555555"
                            }
                          }
                        }
                        """));
              }
              if (query.contains("campaign_conversion_goal")) {
                return List.of(
                    objectMapper.readTree(
                        """
                        {
                          "campaignConversionGoal": {
                            "resourceName":
                              "customers/1234567890/campaignConversionGoals/444~LEAD~WEBSITE",
                            "biddable": true
                          }
                        }
                        """));
              }
              return List.of();
            });

    when(apiClient.searchRows(
            eq("token"), eq("5555555555"), eq("9999999999"), anyString()))
        .thenReturn(List.of());

    when(apiClient.customerResourceName("5555555555", "conversionActions", "777"))
        .thenReturn("customers/5555555555/conversionActions/777");
    when(apiClient.mutate(
            eq("token"),
            eq("5555555555"),
            eq("9999999999"),
            eq("customConversionGoals"),
            org.mockito.ArgumentMatchers.anyList()))
        .thenReturn(
            objectMapper.readTree(
                """
                {
                  "results": [
                    {
                      "resourceName":
                        "customers/5555555555/customConversionGoals/333"
                    }
                  ]
                }
                """));
    when(apiClient.firstMutatedResourceName(org.mockito.ArgumentMatchers.any()))
        .thenReturn("customers/5555555555/customConversionGoals/333");
    when(apiClient.customerResourceName(
            "1234567890", "conversionGoalCampaignConfigs", "444"))
        .thenReturn(
            "customers/1234567890/conversionGoalCampaignConfigs/444");

    var result =
        service.apply(1L, 2L, "444", 10L, mapping());

    assertThat(result.created()).isTrue();
    assertThat(result.customConversionGoalResource())
        .isEqualTo("customers/5555555555/customConversionGoals/333");
    assertThat(result.disabledCampaignGoals()).isEqualTo(1);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, Object>>> operations =
        ArgumentCaptor.forClass(List.class);
    verify(apiClient)
        .mutate(
            eq("token"),
            eq("1234567890"),
            eq("9999999999"),
            eq("conversionGoalCampaignConfigs"),
            operations.capture());
    assertThat(operations.getValue()).hasSize(1);
    assertThat(operations.getValue().getFirst().get("updateMask"))
        .isEqualTo("custom_conversion_goal");

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, Object>>> campaignGoalOperations =
        ArgumentCaptor.forClass(List.class);
    verify(apiClient)
        .mutate(
            eq("token"),
            eq("1234567890"),
            eq("9999999999"),
            eq("campaignConversionGoals"),
            campaignGoalOperations.capture());
    assertThat(campaignGoalOperations.getValue()).hasSize(1);
    @SuppressWarnings("unchecked")
    Map<String, Object> disabledGoal =
        (Map<String, Object>) campaignGoalOperations.getValue().getFirst().get("update");
    assertThat(disabledGoal.get("biddable")).isEqualTo(false);
  }

  @Test
  void reusesMatchingExistingGoalWithoutRecreatingIt() throws Exception {
    AdAccount account = account();
    when(contextService.requireManaged(1L, 2L))
        .thenReturn(
            new GoogleAdsAccountContextService.Context(
                account, null, "token", null, false, false));
    when(apiClient.searchRows(eq("token"), eq("1234567890"), eq(null), anyString()))
        .thenAnswer(
            invocation -> {
              String query = invocation.getArgument(3);
              if (query.contains("conversion_tracking_setting")) {
                return List.of(
                    objectMapper.readTree(
                        """
                        {
                          "customer": {
                            "conversionTrackingSetting": {
                              "googleAdsConversionCustomer": "customers/1234567890"
                            }
                          }
                        }
                        """));
              }
              return List.of(
                  objectMapper.readTree(
                      """
                      {
                        "customConversionGoal": {
                          "resourceName":
                            "customers/1234567890/customConversionGoals/333",
                          "name": "UnifyDesk campaign 10 conversion mapping 20",
                          "status": "ENABLED",
                          "conversionActions": [
                            "customers/1234567890/conversionActions/777"
                          ]
                        }
                      }
                      """));
            });
    when(apiClient.customerResourceName("1234567890", "conversionActions", "777"))
        .thenReturn("customers/1234567890/conversionActions/777");
    when(apiClient.customerResourceName(
            "1234567890", "conversionGoalCampaignConfigs", "444"))
        .thenReturn(
            "customers/1234567890/conversionGoalCampaignConfigs/444");

    var result =
        service.apply(1L, 2L, "444", 10L, mapping());

    assertThat(result.created()).isFalse();
    assertThat(result.updated()).isFalse();
    assertThat(result.customConversionGoalResource())
        .isEqualTo("customers/1234567890/customConversionGoals/333");
  }

  private AdAccount account() {
    AdAccount account = new AdAccount();
    account.setId(2L);
    account.setExternalAccountId("1234567890");
    account.setProvider(AdProvider.GOOGLE_ADS);
    account.setManaged(true);
    account.setAccessible(true);
    return account;
  }

  private ConversionGoalMapping mapping() {
    ConversionGoalMapping mapping = new ConversionGoalMapping();
    mapping.setId(20L);
    mapping.setCompanyId(1L);
    mapping.setReachlyoCampaignId(10L);
    mapping.setProvider(AdProvider.GOOGLE_ADS);
    mapping.setAdAccountId(2L);
    mapping.setTargetType(ConversionTargetType.PROVIDER_CONVERSION);
    mapping.setAdConversionId(30L);
    mapping.setExternalConversionId("777");
    mapping.setValidated(true);
    return mapping;
  }
}
