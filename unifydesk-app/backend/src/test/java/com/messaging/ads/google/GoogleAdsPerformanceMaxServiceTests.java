package com.messaging.ads.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.entity.AdProviderConnection;
import com.messaging.ads.enums.AdEntityStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.CreateGooglePerformanceMaxCampaignRequest;
import com.messaging.ads.google.dto.GooglePerformanceMaxAutomationRequest;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.ads.service.AdActivityLogService;
import com.messaging.common.exception.BadRequestException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoogleAdsPerformanceMaxServiceTests {

  @Mock private GoogleAdsAccountContextService contextService;
  @Mock private GoogleAdsApiClient apiClient;
  @Mock private GoogleAdsCampaignSyncService campaignSyncService;
  @Mock private GoogleAdsCampaignPersistenceService campaignPersistenceService;
  @Mock private AdCampaignRepository campaignRepository;
  @Mock private AdActivityLogService activityLogService;

  private GoogleAdsPerformanceMaxService service;
  private GoogleAdsAccountContextService.Context context;

  @BeforeEach
  void setUp() {
    service =
        new GoogleAdsPerformanceMaxService(
            contextService,
            apiClient,
            campaignSyncService,
            campaignPersistenceService,
            campaignRepository,
            activityLogService);

    AdAccount account = new AdAccount();
    account.setId(20L);
    account.setCompanyId(10L);
    account.setConnectionId(30L);
    account.setProvider(AdProvider.GOOGLE_ADS);
    account.setExternalAccountId("1234567890");
    account.setName("Reachlyo Test Ads");
    account.setCurrency("INR");
    account.setManaged(true);
    account.setAccessible(true);

    AdProviderConnection connection = new AdProviderConnection();
    connection.setId(30L);
    connection.setCompanyId(10L);
    connection.setProvider(AdProvider.GOOGLE_ADS);

    context =
        new GoogleAdsAccountContextService.Context(
            account, connection, "access-token", "9999999999", false, true);
  }

  @Test
  void rejectsAssetGroupWithoutMinimumHeadlineCount() throws Exception {
    when(contextService.requireManaged(10L, 20L)).thenReturn(context);

    var request =
        new CreateGooglePerformanceMaxCampaignRequest(
            "PMax",
            50000L,
            "https://example.com",
            "Reachlyo",
            List.of("One", "Two"),
            "A long headline",
            List.of("Short description", "Second description"),
            image(600, 314),
            image(300, 300),
            image(128, 128),
            null,
            null,
            List.of(),
            "MAXIMIZE_CONVERSIONS",
            false,
            null,
            null);

    assertThatThrownBy(() -> service.create(10L, 20L, request, 40L))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("3 to 15 headlines");
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void createsRequiredPerformanceMaxResourcesInOneBulkMutation() throws Exception {
    when(contextService.requireManaged(10L, 20L)).thenReturn(context);
    when(apiClient.mutateAll(
            eq("access-token"),
            eq("1234567890"),
            eq("9999999999"),
            any()))
        .thenReturn(new ObjectMapper().readTree("{\"mutateOperationResponses\":[]}"));
    when(apiClient.firstBulkResourceName(any(), eq("campaignResult")))
        .thenReturn("customers/1234567890/campaigns/777");

    var campaignRow =
        new ObjectMapper()
            .readTree(
                "{\"campaign\":{\"id\":\"777\",\"name\":\"PMax\",\"status\":\"PAUSED\",\"advertisingChannelType\":\"PERFORMANCE_MAX\"}}");
    when(campaignSyncService.campaign(context, "777")).thenReturn(campaignRow);

    AdCampaign persisted = new AdCampaign();
    persisted.setId(700L);
    persisted.setCompanyId(10L);
    persisted.setAdAccountId(20L);
    persisted.setProvider(AdProvider.GOOGLE_ADS);
    persisted.setExternalCampaignId("777");
    persisted.setName("PMax");
    persisted.setObjective("PERFORMANCE_MAX");
    persisted.setStatus(AdEntityStatus.PAUSED);
    persisted.setAccessible(true);
    when(campaignPersistenceService.persistOne(10L, 20L, campaignRow))
        .thenReturn(persisted);

    var request =
        new CreateGooglePerformanceMaxCampaignRequest(
            "PMax",
            50000L,
            "https://example.com",
            "Reachlyo",
            List.of("Headline One", "Headline Two", "Headline Three"),
            "A long headline for Reachlyo",
            List.of("Short description", "Second description for Reachlyo"),
            image(600, 314),
            image(300, 300),
            image(128, 128),
            null,
            null,
            List.of(),
            "MAXIMIZE_CONVERSIONS",
            false,
            LocalDate.now(),
            LocalDate.now().plusDays(30));

    var created = service.create(10L, 20L, request, 40L);

    assertThat(created.externalCampaignId()).isEqualTo("777");
    assertThat(created.objective()).isEqualTo("PERFORMANCE_MAX");

    ArgumentCaptor<List> operationsCaptor = ArgumentCaptor.forClass(List.class);
    verify(apiClient)
        .mutateAll(
            eq("access-token"),
            eq("1234567890"),
            eq("9999999999"),
            operationsCaptor.capture());

    List<Map<String, Object>> operations =
        (List<Map<String, Object>>) operationsCaptor.getValue();

    assertThat(operations.get(0)).containsKey("campaignBudgetOperation");
    assertThat(operations.get(1)).containsKey("campaignOperation");

    int assetGroupIndex = indexOf(operations, "assetGroupOperation");
    int firstAssetLinkIndex = indexOf(operations, "assetGroupAssetOperation");
    int lastAssetIndex = lastIndexOf(operations, "assetOperation");

    assertThat(lastAssetIndex).isGreaterThan(1);
    assertThat(assetGroupIndex).isGreaterThan(lastAssetIndex);
    assertThat(firstAssetLinkIndex).isGreaterThan(assetGroupIndex);
    assertThat(
            operations.stream()
                .filter(operation -> operation.containsKey("assetGroupAssetOperation"))
                .count())
        .isEqualTo(10L);
  }


  @Test
  void rejectsDisablingTextAutomationWhileFinalUrlExpansionIsEnabled() {
    AdCampaign campaign = performanceMaxCampaign();
    when(campaignRepository.findByIdAndCompanyId(700L, 10L))
        .thenReturn(Optional.of(campaign));
    when(contextService.requireManaged(10L, 20L)).thenReturn(context);
    when(apiClient.searchRows(
            eq("access-token"), eq("1234567890"), eq("9999999999"), any()))
        .thenReturn(List.of());

    assertThatThrownBy(
            () ->
                service.updateAutomation(
                    10L,
                    700L,
                    new GooglePerformanceMaxAutomationRequest(
                        true, false, null, null, null, null, null),
                    40L))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("cannot be opted out");
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void updatesPerformanceMaxAssetAutomationSettings() {
    AdCampaign campaign = performanceMaxCampaign();
    when(campaignRepository.findByIdAndCompanyId(700L, 10L))
        .thenReturn(Optional.of(campaign));
    when(contextService.requireManaged(10L, 20L)).thenReturn(context);
    when(apiClient.searchRows(
            eq("access-token"), eq("1234567890"), eq("9999999999"), any()))
        .thenReturn(List.of());

    service.updateAutomation(
        10L,
        700L,
        new GooglePerformanceMaxAutomationRequest(
            false, false, true, true, false, null, null),
        40L);

    ArgumentCaptor<List> operationsCaptor = ArgumentCaptor.forClass(List.class);
    verify(apiClient)
        .mutate(
            eq("access-token"),
            eq("1234567890"),
            eq("9999999999"),
            eq("campaigns"),
            operationsCaptor.capture());

    Map<String, Object> operation =
        (Map<String, Object>) operationsCaptor.getValue().getFirst();
    Map<String, Object> update = (Map<String, Object>) operation.get("update");
    assertThat(update).containsKey("assetAutomationSettings");
    assertThat(operation.get("updateMask")).isEqualTo("asset_automation_settings");
  }

  private AdCampaign performanceMaxCampaign() {
    AdCampaign campaign = new AdCampaign();
    campaign.setId(700L);
    campaign.setCompanyId(10L);
    campaign.setAdAccountId(20L);
    campaign.setProvider(AdProvider.GOOGLE_ADS);
    campaign.setExternalCampaignId("777");
    campaign.setObjective("PERFORMANCE_MAX");
    campaign.setAccessible(true);
    return campaign;
  }

  private int indexOf(List<Map<String, Object>> operations, String key) {
    for (int i = 0; i < operations.size(); i++) {
      if (operations.get(i).containsKey(key)) return i;
    }
    return -1;
  }

  private int lastIndexOf(List<Map<String, Object>> operations, String key) {
    for (int i = operations.size() - 1; i >= 0; i--) {
      if (operations.get(i).containsKey(key)) return i;
    }
    return -1;
  }

  private String image(int width, int height) throws Exception {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ImageIO.write(image, "png", output);
    return Base64.getEncoder().encodeToString(output.toByteArray());
  }
}
