package com.messaging.ads.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.entity.AdProviderConnection;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.CreateGoogleCampaignMixExperimentRequest;
import com.messaging.ads.google.dto.CreateGoogleExperimentRequest;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.ads.service.AdActivityLogService;
import com.messaging.common.exception.BadRequestException;
import java.time.LocalDate;
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
class GoogleAdsExperimentServiceTests {

  @Mock private GoogleAdsAccountContextService contextService;
  @Mock private GoogleAdsApiClient apiClient;
  @Mock private AdCampaignRepository campaignRepository;
  @Mock private AdActivityLogService activityLogService;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private GoogleAdsExperimentService service;
  private GoogleAdsAccountContextService.Context context;

  @BeforeEach
  void setUp() {
    service =
        new GoogleAdsExperimentService(
            contextService, apiClient, campaignRepository, activityLogService);

    AdAccount account = new AdAccount();
    account.setId(20L);
    account.setCompanyId(10L);
    account.setProvider(AdProvider.GOOGLE_ADS);
    account.setExternalAccountId("1234567890");
    account.setCurrency("INR");
    account.setManaged(true);
    account.setAccessible(true);

    context =
        new GoogleAdsAccountContextService.Context(
            account, new AdProviderConnection(), "access-token", null, false, false);
  }

  @Test
  void createsSearchCustomExperimentWithAtomicArmsAndTreatmentMutation() throws Exception {
    when(contextService.requireManaged(10L, 20L)).thenReturn(context);
    AdCampaign base = searchCampaign();
    when(campaignRepository.findByIdAndCompanyId(30L, 10L))
        .thenReturn(Optional.of(base));

    when(apiClient.customerResourceName("1234567890", "campaigns", "900"))
        .thenReturn("customers/1234567890/campaigns/900");

    JsonNode experimentMutation =
        objectMapper.readTree(
            """
            {"results":[{"resourceName":"customers/1234567890/experiments/55"}]}
            """);
    JsonNode campaignMutation = objectMapper.readTree("{\"results\":[]}");
    when(apiClient.mutate(
            eq("access-token"),
            eq("1234567890"),
            isNull(),
            anyString(),
            anyList()))
        .thenAnswer(
            invocation -> {
              String collection = invocation.getArgument(3);
              if ("experiments".equals(collection)) return experimentMutation;
              if ("campaigns".equals(collection)) return campaignMutation;
              return objectMapper.readTree("{\"results\":[]}");
            });
    when(apiClient.firstMutatedResourceName(experimentMutation))
        .thenReturn("customers/1234567890/experiments/55");

    JsonNode armMutation =
        objectMapper.readTree(
            """
            {
              "results":[
                {"experimentArm":{"resourceName":"customers/1234567890/experimentArms/11","name":"Control","control":true,"trafficSplit":"50","campaigns":["customers/1234567890/campaigns/900"]}},
                {"experimentArm":{"resourceName":"customers/1234567890/experimentArms/12","name":"Treatment","control":false,"trafficSplit":"50","inDesignCampaigns":["customers/1234567890/campaigns/901"]}}
              ]
            }
            """);
    when(apiClient.mutateReturningMutableResource(
            eq("access-token"),
            eq("1234567890"),
            isNull(),
            eq("experimentArms"),
            anyList()))
        .thenReturn(armMutation);

    JsonNode experimentRow =
        objectMapper.readTree(
            """
            {
              "experiment":{
                "resourceName":"customers/1234567890/experiments/55",
                "experimentId":"55",
                "name":"Bid strategy test",
                "description":"Control versus maximize conversions",
                "type":"SEARCH_CUSTOM",
                "status":"SETUP",
                "suffix":"[UnifyDesk experiment]"
              },
              "metrics":{"impressions":"1000","clicks":"70","conversions":"8.5"}
            }
            """);
    JsonNode controlArm =
        objectMapper.readTree(
            """
            {"experimentArm":{"resourceName":"customers/1234567890/experimentArms/11","experiment":"customers/1234567890/experiments/55","name":"Control","control":true,"trafficSplit":"50","campaigns":["customers/1234567890/campaigns/900"]}}
            """);
    JsonNode treatmentArm =
        objectMapper.readTree(
            """
            {"experimentArm":{"resourceName":"customers/1234567890/experimentArms/12","experiment":"customers/1234567890/experiments/55","name":"Treatment","control":false,"trafficSplit":"50","inDesignCampaigns":["customers/1234567890/campaigns/901"]}}
            """);

    when(apiClient.searchRows(
            eq("access-token"), eq("1234567890"), isNull(), anyString()))
        .thenAnswer(
            invocation ->
                invocation.<String>getArgument(3).contains("FROM experiment ")
                    ? List.of(experimentRow)
                    : List.of());
    when(apiClient.searchRows(
            eq("access-token"), eq("1234567890"), isNull(), anyString(), eq(true)))
        .thenReturn(List.of(controlArm, treatmentArm));

    var created =
        service.createSearchCustom(
            10L,
            20L,
            new CreateGoogleExperimentRequest(
                "Bid strategy test",
                "Control versus maximize conversions",
                30L,
                50,
                "MAXIMIZE_CONVERSIONS",
                null,
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 15)),
            77L);

    assertThat(created.id()).isEqualTo("55");
    assertThat(created.type()).isEqualTo("SEARCH_CUSTOM");
    assertThat(created.arms()).hasSize(2);
    assertThat(created.metrics().impressions()).isEqualTo(1000L);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, Object>>> armOperations =
        ArgumentCaptor.forClass(List.class);
    verify(apiClient)
        .mutateReturningMutableResource(
            eq("access-token"),
            eq("1234567890"),
            isNull(),
            eq("experimentArms"),
            armOperations.capture());
    assertThat(armOperations.getValue()).hasSize(2);

    @SuppressWarnings("unchecked")
    Map<String, Object> control =
        (Map<String, Object>) armOperations.getValue().get(0).get("create");
    @SuppressWarnings("unchecked")
    Map<String, Object> treatment =
        (Map<String, Object>) armOperations.getValue().get(1).get("create");
    assertThat(control.get("control")).isEqualTo(true);
    assertThat(control.get("trafficSplit")).isEqualTo(50);
    assertThat(treatment.get("control")).isEqualTo(false);
    assertThat(treatment.get("trafficSplit")).isEqualTo(50);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, Object>>> mutations =
        ArgumentCaptor.forClass(List.class);
    verify(apiClient)
        .mutate(
            eq("access-token"),
            eq("1234567890"),
            isNull(),
            eq("campaigns"),
            mutations.capture());
    @SuppressWarnings("unchecked")
    Map<String, Object> update =
        (Map<String, Object>) mutations.getValue().get(0).get("update");
    assertThat(update.get("resourceName"))
        .isEqualTo("customers/1234567890/campaigns/901");
    assertThat(update).containsKey("maximizeConversions");
  }

  @Test
  void schedulesSetupExperimentThroughGoogleLifecycleAction() throws Exception {
    when(contextService.requireManaged(10L, 20L)).thenReturn(context);
    when(apiClient.customerResourceName("1234567890", "experiments", "55"))
        .thenReturn("customers/1234567890/experiments/55");

    JsonNode experimentRow =
        objectMapper.readTree(
            """
            {
              "experiment":{
                "resourceName":"customers/1234567890/experiments/55",
                "experimentId":"55",
                "name":"Setup experiment",
                "type":"SEARCH_CUSTOM",
                "status":"SETUP"
              }
            }
            """);
    when(apiClient.searchRows(
            eq("access-token"), eq("1234567890"), isNull(), anyString()))
        .thenReturn(List.of(experimentRow));
    when(apiClient.searchRows(
            eq("access-token"), eq("1234567890"), isNull(), anyString(), eq(true)))
        .thenReturn(List.of());
    when(apiClient.customerAction(
            eq("access-token"),
            eq("1234567890"),
            isNull(),
            eq("experiments/55:scheduleExperiment"),
            eq(Map.of("resourceName", "customers/1234567890/experiments/55"))))
        .thenReturn(objectMapper.readTree("{\"name\":\"operations/schedule-55\"}"));

    var response = service.schedule(10L, 20L, "55", 77L);

    assertThat(response.action()).isEqualTo("SCHEDULE");
    assertThat(response.operationName()).isEqualTo("operations/schedule-55");
    verify(apiClient)
        .customerAction(
            "access-token",
            "1234567890",
            null,
            "experiments/55:scheduleExperiment",
            Map.of("resourceName", "customers/1234567890/experiments/55"));
  }

  @Test
  void rejectsCampaignMixWithIdenticalArmCampaigns() {
    when(contextService.requireManaged(10L, 20L)).thenReturn(context);
    AdCampaign campaign = searchCampaign();
    when(campaignRepository.findByIdAndCompanyId(30L, 10L))
        .thenReturn(Optional.of(campaign));

    assertThatThrownBy(
            () ->
                service.createCampaignMix(
                    10L,
                    20L,
                    new CreateGoogleCampaignMixExperimentRequest(
                        "Invalid mix",
                        null,
                        List.of(30L),
                        List.of(30L),
                        50,
                        null,
                        null),
                    77L))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("identical");
  }

  @Test
  void rejectsNonSearchBaseCampaign() {
    when(contextService.requireManaged(10L, 20L)).thenReturn(context);
    AdCampaign base = searchCampaign();
    base.setObjective("PERFORMANCE_MAX");
    when(campaignRepository.findByIdAndCompanyId(30L, 10L))
        .thenReturn(Optional.of(base));

    assertThatThrownBy(
            () ->
                service.createSearchCustom(
                    10L,
                    20L,
                    new CreateGoogleExperimentRequest(
                        "Invalid",
                        null,
                        30L,
                        50,
                        "MAXIMIZE_CONVERSIONS",
                        null,
                        null,
                        null),
                    77L))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("SEARCH_CUSTOM");
  }

  @Test
  void rejectsTreatmentStrategyThatMatchesBase() {
    when(contextService.requireManaged(10L, 20L)).thenReturn(context);
    AdCampaign base = searchCampaign();
    base.setBidStrategy("TARGET_SPEND");
    when(campaignRepository.findByIdAndCompanyId(30L, 10L))
        .thenReturn(Optional.of(base));

    assertThatThrownBy(
            () ->
                service.createSearchCustom(
                    10L,
                    20L,
                    new CreateGoogleExperimentRequest(
                        "Same bid",
                        null,
                        30L,
                        50,
                        "MAXIMIZE_CLICKS",
                        null,
                        null,
                        null),
                    77L))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("differs");
  }

  private AdCampaign searchCampaign() {
    AdCampaign campaign = new AdCampaign();
    campaign.setId(30L);
    campaign.setCompanyId(10L);
    campaign.setAdAccountId(20L);
    campaign.setProvider(AdProvider.GOOGLE_ADS);
    campaign.setExternalCampaignId("900");
    campaign.setName("Search base");
    campaign.setObjective("SEARCH");
    campaign.setBidStrategy("MANUAL_CPC");
    campaign.setAccessible(true);
    return campaign;
  }
}
