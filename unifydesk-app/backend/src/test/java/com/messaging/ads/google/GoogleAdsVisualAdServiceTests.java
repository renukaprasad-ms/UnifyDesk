package com.messaging.ads.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.Ad;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.entity.AdProviderConnection;
import com.messaging.ads.entity.AdSet;
import com.messaging.ads.enums.AdEntityStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.CreateGoogleResponsiveDisplayAdRequest;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.ads.repository.AdSetRepository;
import com.messaging.ads.service.AdActivityLogService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoogleAdsVisualAdServiceTests {

  @Mock private AdSetRepository adSetRepository;
  @Mock private AdCampaignRepository campaignRepository;
  @Mock private GoogleAdsAccountContextService contextService;
  @Mock private GoogleAdsApiClient apiClient;
  @Mock private GoogleAdsAdSyncService adSyncService;
  @Mock private GoogleAdsAdPersistenceService adPersistenceService;
  @Mock private AdActivityLogService activityLogService;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private GoogleAdsVisualAdService service;
  private GoogleAdsAccountContextService.Context context;
  private AdSet adGroup;
  private AdCampaign campaign;

  @BeforeEach
  void setUp() {
    service =
        new GoogleAdsVisualAdService(
            adSetRepository,
            campaignRepository,
            contextService,
            apiClient,
            adSyncService,
            adPersistenceService,
            activityLogService);

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
            account,
            new AdProviderConnection(),
            "access-token",
            null,
            false,
            false);

    campaign = new AdCampaign();
    campaign.setId(30L);
    campaign.setCompanyId(10L);
    campaign.setAdAccountId(20L);
    campaign.setProvider(AdProvider.GOOGLE_ADS);
    campaign.setExternalCampaignId("901");
    campaign.setObjective("DISPLAY");
    campaign.setAccessible(true);

    adGroup = new AdSet();
    adGroup.setId(40L);
    adGroup.setCompanyId(10L);
    adGroup.setAdAccountId(20L);
    adGroup.setCampaignId(30L);
    adGroup.setProvider(AdProvider.GOOGLE_ADS);
    adGroup.setExternalAdSetId("902");
    adGroup.setAccessible(true);
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void createsDisplayAssetsAndPausedAdInOneBulkMutation() throws Exception {
    when(adSetRepository.findByIdAndCompanyId(40L, 10L))
        .thenReturn(java.util.Optional.of(adGroup));
    when(campaignRepository.findByIdAndCompanyId(30L, 10L))
        .thenReturn(java.util.Optional.of(campaign));
    when(contextService.requireManaged(10L, 20L)).thenReturn(context);

    when(apiClient.customerResourceName("1234567890", "assets", "-1"))
        .thenReturn("customers/1234567890/assets/-1");
    when(apiClient.customerResourceName("1234567890", "assets", "-2"))
        .thenReturn("customers/1234567890/assets/-2");
    when(apiClient.customerResourceName("1234567890", "assets", "-3"))
        .thenReturn("customers/1234567890/assets/-3");
    when(apiClient.customerResourceName("1234567890", "adGroups", "902"))
        .thenReturn("customers/1234567890/adGroups/902");
    when(apiClient.mutateAll(
            eq("access-token"), eq("1234567890"), eq(null), any()))
        .thenReturn(objectMapper.readTree("{"mutateOperationResponses":[]}"));
    when(apiClient.firstBulkResourceName(any(), eq("adGroupAdResult")))
        .thenReturn("customers/1234567890/adGroupAds/902~903");

    var adRow =
        objectMapper.readTree(
            """
            {"campaign":{"id":"901"},"adGroup":{"id":"902"},"adGroupAd":{"status":"PAUSED","ad":{"id":"903","name":"Display Ad"}}}
            """);
    when(adSyncService.ad(context, "902", "903")).thenReturn(adRow);

    Ad persisted = new Ad();
    persisted.setId(50L);
    persisted.setCompanyId(10L);
    persisted.setAdAccountId(20L);
    persisted.setCampaignId(30L);
    persisted.setAdSetId(40L);
    persisted.setProvider(AdProvider.GOOGLE_ADS);
    persisted.setExternalAdId("903");
    persisted.setName("Display Ad");
    persisted.setStatus(AdEntityStatus.PAUSED);
    persisted.setAccessible(true);
    when(adPersistenceService.persistOne(10L, 40L, adRow))
        .thenReturn(persisted);

    var response =
        service.createResponsiveDisplay(
            10L,
            40L,
            new CreateGoogleResponsiveDisplayAdRequest(
                "Display Ad",
                "https://example.com",
                "Acme",
                List.of("Discover Acme"),
                "Discover a better way to work",
                List.of("Built for modern teams"),
                image(600, 314),
                image(300, 300),
                null),
            99L);

    assertThat(response.externalAdId()).isEqualTo("903");

    ArgumentCaptor<List> operations = ArgumentCaptor.forClass(List.class);
    verify(apiClient)
        .mutateAll(
            eq("access-token"),
            eq("1234567890"),
            eq(null),
            operations.capture());

    assertThat(operations.getValue()).hasSize(3);
    assertThat((Map<String, Object>) operations.getValue().get(0))
        .containsKey("assetOperation");
    assertThat((Map<String, Object>) operations.getValue().get(1))
        .containsKey("assetOperation");

    Map<String, Object> adOperation =
        (Map<String, Object>) operations.getValue().get(2);
    Map<String, Object> create =
        (Map<String, Object>)
            ((Map<String, Object>) adOperation.get("adGroupAdOperation"))
                .get("create");
    assertThat(create.get("status")).isEqualTo("PAUSED");
  }

  private String image(int width, int height) throws Exception {
    BufferedImage image =
        new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ImageIO.write(image, "png", output);
    return Base64.getEncoder().encodeToString(output.toByteArray());
  }
}
