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
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.GoogleAssetLinkRequest;
import com.messaging.ads.google.dto.GoogleAdvancedReusableAssetCreateRequest;
import com.messaging.ads.google.dto.GooglePriceOfferingInput;
import com.messaging.ads.google.dto.GoogleReusableAssetCreateRequest;
import com.messaging.ads.google.dto.GoogleUrlCustomParameter;
import com.messaging.ads.google.dto.GoogleUrlOptionsRequest;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.ads.repository.AdSetRepository;
import com.messaging.ads.service.AdActivityLogService;
import com.messaging.common.exception.BadRequestException;
import java.math.BigDecimal;
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
class GoogleAdsReusableAssetServiceTests {

  @Mock private GoogleAdsAccountContextService contextService;
  @Mock private GoogleAdsApiClient apiClient;
  @Mock private AdCampaignRepository campaignRepository;
  @Mock private AdSetRepository adSetRepository;
  @Mock private AdActivityLogService activityLogService;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private GoogleAdsReusableAssetService service;

  @BeforeEach
  void setUp() {
    service =
        new GoogleAdsReusableAssetService(
            contextService,
            apiClient,
            campaignRepository,
            adSetRepository,
            activityLogService);
  }

  @Test
  void createsReusableSitelinkWithAssetUrlOptions() throws Exception {
    AdAccount account = account();
    when(contextService.requireManaged(1L, 20L))
        .thenReturn(context(account));

    var mutateResponse =
        objectMapper.readTree(
            """
            {"results":[{"resourceName":"customers/1234567890/assets/77"}]}
            """);
    when(apiClient.mutate(
            eq("token"),
            eq("1234567890"),
            eq(null),
            eq("assets"),
            any()))
        .thenReturn(mutateResponse);
    when(apiClient.firstMutatedResourceName(mutateResponse))
        .thenReturn("customers/1234567890/assets/77");
    when(apiClient.searchRows(eq("token"), eq("1234567890"), eq(null), anyString()))
        .thenReturn(
            List.of(
                objectMapper.readTree(
                    """
                    {
                      "asset": {
                        "id": "77",
                        "resourceName": "customers/1234567890/assets/77",
                        "name": "Pricing",
                        "type": "SITELINK",
                        "source": "ADVERTISER",
                        "sitelinkAsset": {
                          "linkText": "Pricing",
                          "description1": "Simple plans",
                          "description2": "Compare options"
                        },
                        "finalUrls": ["https://example.com/pricing"],
                        "trackingUrlTemplate": "{lpurl}?src={_channel}",
                        "urlCustomParameters": [
                          {"key":"channel","value":"search"}
                        ]
                      }
                    }
                    """)));

    var result =
        service.create(
            1L,
            20L,
            new GoogleReusableAssetCreateRequest(
                GoogleReusableAssetCreateRequest.Type.SITELINK,
                "Pricing",
                "Pricing",
                "Simple plans",
                "Compare options",
                null,
                null,
                null,
                "https://example.com/pricing",
                null,
                "{lpurl}?src={_channel}",
                "source=google",
                List.of(new GoogleUrlCustomParameter("channel", "search"))),
            99L);

    assertThat(result.id()).isEqualTo("77");
    assertThat(result.type()).isEqualTo("SITELINK");
    assertThat(result.linkText()).isEqualTo("Pricing");

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, Object>>> operations =
        ArgumentCaptor.forClass(List.class);
    verify(apiClient)
        .mutate(
            eq("token"),
            eq("1234567890"),
            eq(null),
            eq("assets"),
            operations.capture());

    @SuppressWarnings("unchecked")
    Map<String, Object> create =
        (Map<String, Object>) operations.getValue().getFirst().get("create");
    assertThat(create.get("finalUrls"))
        .isEqualTo(List.of("https://example.com/pricing"));
    assertThat(create.get("trackingUrlTemplate"))
        .isEqualTo("{lpurl}?src={_channel}");
    assertThat(create.get("finalUrlSuffix")).isEqualTo("source=google");

    @SuppressWarnings("unchecked")
    Map<String, Object> sitelink =
        (Map<String, Object>) create.get("sitelinkAsset");
    assertThat(sitelink.get("linkText")).isEqualTo("Pricing");
  }

  @Test
  void attachesExistingAssetToCampaignUsingLocalCampaignId() throws Exception {
    AdAccount account = account();
    when(contextService.requireManaged(1L, 20L))
        .thenReturn(context(account));

    AdCampaign campaign = new AdCampaign();
    campaign.setId(30L);
    campaign.setCompanyId(1L);
    campaign.setAdAccountId(20L);
    campaign.setProvider(AdProvider.GOOGLE_ADS);
    campaign.setExternalCampaignId("555");
    campaign.setAccessible(true);
    when(campaignRepository.findByIdAndCompanyId(30L, 1L))
        .thenReturn(Optional.of(campaign));

    var assetRow =
        objectMapper.readTree(
            """
            {
              "asset": {
                "id": "77",
                "resourceName": "customers/1234567890/assets/77",
                "name": "Free support",
                "type": "CALLOUT",
                "source": "ADVERTISER",
                "calloutAsset": {"calloutText":"24/7 support"}
              }
            }
            """);
    var linkRow =
        objectMapper.readTree(
            """
            {
              "campaignAsset": {
                "resourceName": "customers/1234567890/campaignAssets/555~77~CALLOUT",
                "asset": "customers/1234567890/assets/77",
                "fieldType": "CALLOUT",
                "status": "ENABLED",
                "primaryStatus": "ELIGIBLE"
              },
              "asset": {
                "id":"77",
                "resourceName":"customers/1234567890/assets/77",
                "name":"Free support",
                "type":"CALLOUT"
              }
            }
            """);

    when(apiClient.searchRows(eq("token"), eq("1234567890"), eq(null), anyString()))
        .thenReturn(List.of(assetRow), List.of(), List.of(linkRow));
    when(apiClient.customerResourceName("1234567890", "campaigns", "555"))
        .thenReturn("customers/1234567890/campaigns/555");
    when(apiClient.customerResourceName("1234567890", "assets", "77"))
        .thenReturn("customers/1234567890/assets/77");

    var mutateResponse =
        objectMapper.readTree(
            """
            {"results":[{"resourceName":"customers/1234567890/campaignAssets/555~77~CALLOUT"}]}
            """);
    when(apiClient.mutate(
            eq("token"),
            eq("1234567890"),
            eq(null),
            eq("campaignAssets"),
            any()))
        .thenReturn(mutateResponse);
    when(apiClient.firstMutatedResourceName(mutateResponse))
        .thenReturn("customers/1234567890/campaignAssets/555~77~CALLOUT");

    var result =
        service.attach(
            1L,
            20L,
            "77",
            new GoogleAssetLinkRequest(
                GoogleAssetLinkRequest.Level.CAMPAIGN, 30L),
            99L);

    assertThat(result.assetId()).isEqualTo("77");
    assertThat(result.level()).isEqualTo("CAMPAIGN");
    assertThat(result.targetId()).isEqualTo("30");

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, Object>>> operations =
        ArgumentCaptor.forClass(List.class);
    verify(apiClient)
        .mutate(
            eq("token"),
            eq("1234567890"),
            eq(null),
            eq("campaignAssets"),
            operations.capture());

    @SuppressWarnings("unchecked")
    Map<String, Object> create =
        (Map<String, Object>) operations.getValue().getFirst().get("create");
    assertThat(create.get("campaign"))
        .isEqualTo("customers/1234567890/campaigns/555");
    assertThat(create.get("asset"))
        .isEqualTo("customers/1234567890/assets/77");
    assertThat(create.get("fieldType")).isEqualTo("CALLOUT");
  }

  @Test
  void createsPromotionWithPercentDiscountInGoogleMicros() throws Exception {
    AdAccount account = account();
    when(contextService.requireManaged(1L, 20L))
        .thenReturn(context(account));

    var mutateResponse =
        objectMapper.readTree(
            """
            {"results":[{"resourceName":"customers/1234567890/assets/88"}]}
            """);
    when(apiClient.mutate(
            eq("token"),
            eq("1234567890"),
            eq(null),
            eq("assets"),
            any()))
        .thenReturn(mutateResponse);
    when(apiClient.firstMutatedResourceName(mutateResponse))
        .thenReturn("customers/1234567890/assets/88");
    when(apiClient.searchRows(eq("token"), eq("1234567890"), eq(null), anyString()))
        .thenReturn(List.of());

    service.createAdvanced(
        1L,
        20L,
        new GoogleAdvancedReusableAssetCreateRequest(
            GoogleAdvancedReusableAssetCreateRequest.Type.PROMOTION,
            "Diwali offer",
            null,
            null,
            null,
            null,
            null,
            null,
            "Annual plan",
            new BigDecimal("20"),
            null,
            null,
            "UP_TO",
            "SAVE20",
            "DIWALI",
            "en",
            "https://example.com/offer",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null),
        99L);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, Object>>> operations =
        ArgumentCaptor.forClass(List.class);
    verify(apiClient)
        .mutate(
            eq("token"),
            eq("1234567890"),
            eq(null),
            eq("assets"),
            operations.capture());
    @SuppressWarnings("unchecked")
    Map<String, Object> create =
        (Map<String, Object>) operations.getValue().getFirst().get("create");
    @SuppressWarnings("unchecked")
    Map<String, Object> promotion =
        (Map<String, Object>) create.get("promotionAsset");
    assertThat(promotion.get("percentOff")).isEqualTo(200_000L);
    assertThat(promotion.get("promotionCode")).isEqualTo("SAVE20");
    assertThat(create.get("finalUrls"))
        .isEqualTo(List.of("https://example.com/offer"));
  }

  @Test
  void createsPriceAssetWithThreeOfferings() throws Exception {
    AdAccount account = account();
    when(contextService.requireManaged(1L, 20L))
        .thenReturn(context(account));

    var mutateResponse =
        objectMapper.readTree(
            """
            {"results":[{"resourceName":"customers/1234567890/assets/89"}]}
            """);
    when(apiClient.mutate(
            eq("token"),
            eq("1234567890"),
            eq(null),
            eq("assets"),
            any()))
        .thenReturn(mutateResponse);
    when(apiClient.firstMutatedResourceName(mutateResponse))
        .thenReturn("customers/1234567890/assets/89");
    when(apiClient.searchRows(eq("token"), eq("1234567890"), eq(null), anyString()))
        .thenReturn(List.of());

    GooglePriceOfferingInput offering =
        new GooglePriceOfferingInput(
            "Starter",
            "For small teams",
            new BigDecimal("999"),
            "INR",
            "PER_MONTH",
            "https://example.com/pricing",
            null);

    service.createAdvanced(
        1L,
        20L,
        new GoogleAdvancedReusableAssetCreateRequest(
            GoogleAdvancedReusableAssetCreateRequest.Type.PRICE,
            "Plans",
            null,
            null,
            "SERVICES",
            "FROM",
            "en",
            List.of(offering, offering, offering),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null),
        99L);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, Object>>> operations =
        ArgumentCaptor.forClass(List.class);
    verify(apiClient)
        .mutate(
            eq("token"),
            eq("1234567890"),
            eq(null),
            eq("assets"),
            operations.capture());

    @SuppressWarnings("unchecked")
    Map<String, Object> create =
        (Map<String, Object>) operations.getValue().getFirst().get("create");
    @SuppressWarnings("unchecked")
    Map<String, Object> price =
        (Map<String, Object>) create.get("priceAsset");
    assertThat(price.get("type")).isEqualTo("SERVICES");
    assertThat((List<?>) price.get("priceOfferings")).hasSize(3);
  }

  @Test
  void leadFormCanOnlyAttachAtCampaignLevel() throws Exception {
    AdAccount account = account();
    when(contextService.requireManaged(1L, 20L))
        .thenReturn(context(account));

    var assetRow =
        objectMapper.readTree(
            """
            {
              "asset": {
                "id": "91",
                "resourceName": "customers/1234567890/assets/91",
                "name": "Demo form",
                "type": "LEAD_FORM",
                "source": "ADVERTISER"
              }
            }
            """);
    when(apiClient.searchRows(eq("token"), eq("1234567890"), eq(null), anyString()))
        .thenReturn(List.of(assetRow));

    assertThatThrownBy(
            () ->
                service.attach(
                    1L,
                    20L,
                    "91",
                    new GoogleAssetLinkRequest(
                        GoogleAssetLinkRequest.Level.CUSTOMER, null),
                    99L))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("campaign level");
  }

  @Test
  void customerUrlOptionsUseCustomerMutateEndpointAndRejectCustomParams()
      throws Exception {
    AdAccount account = account();
    when(contextService.requireManaged(1L, 20L))
        .thenReturn(context(account));

    var before =
        objectMapper.readTree(
            """
            {"customer":{"id":"1234567890","trackingUrlTemplate":"","finalUrlSuffix":""}}
            """);
    var after =
        objectMapper.readTree(
            """
            {
              "customer":{
                "id":"1234567890",
                "trackingUrlTemplate":"{lpurl}?source=google",
                "finalUrlSuffix":"campaign={campaignid}"
              }
            }
            """);
    when(apiClient.searchRows(eq("token"), eq("1234567890"), eq(null), anyString()))
        .thenReturn(List.of(before), List.of(after));

    var result =
        service.updateUrlOptions(
            1L,
            20L,
            GoogleAssetLinkRequest.Level.CUSTOMER,
            null,
            new GoogleUrlOptionsRequest(
                "{lpurl}?source=google",
                "campaign={campaignid}",
                null),
            99L);

    assertThat(result.trackingUrlTemplate())
        .isEqualTo("{lpurl}?source=google");
    assertThat(result.finalUrlSuffix())
        .isEqualTo("campaign={campaignid}");

    verify(apiClient)
        .mutateCustomer(
            eq("token"),
            eq("1234567890"),
            eq(null),
            any());

    assertThatThrownBy(
            () ->
                service.updateUrlOptions(
                    1L,
                    20L,
                    GoogleAssetLinkRequest.Level.CUSTOMER,
                    null,
                    new GoogleUrlOptionsRequest(
                        null,
                        null,
                        List.of(new GoogleUrlCustomParameter("x", "1"))),
                    99L))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  void rejectsDuplicateCustomParameterKeysIgnoringCase() {
    AdAccount account = account();
    when(contextService.requireManaged(1L, 20L))
        .thenReturn(context(account));

    assertThatThrownBy(
            () ->
                service.create(
                    1L,
                    20L,
                    new GoogleReusableAssetCreateRequest(
                        GoogleReusableAssetCreateRequest.Type.SITELINK,
                        "Pricing",
                        "Pricing",
                        null,
                        null,
                        null,
                        null,
                        null,
                        "https://example.com/pricing",
                        null,
                        null,
                        null,
                        List.of(
                            new GoogleUrlCustomParameter("Promo", "one"),
                            new GoogleUrlCustomParameter("promo", "two"))),
                    99L))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("unique ignoring case");
  }


  @Test
  void linksRawImageAssetUsingExplicitAdImageRole() throws Exception {
    AdAccount account = account();
    when(contextService.requireManaged(1L, 20L)).thenReturn(context(account));

    AdCampaign campaign = new AdCampaign();
    campaign.setId(30L);
    campaign.setCompanyId(1L);
    campaign.setAdAccountId(20L);
    campaign.setProvider(AdProvider.GOOGLE_ADS);
    campaign.setExternalCampaignId("555");
    campaign.setAccessible(true);
    when(campaignRepository.findByIdAndCompanyId(30L, 1L))
        .thenReturn(Optional.of(campaign));

    var assetRow =
        objectMapper.readTree(
            """
            {
              "asset": {
                "id": "92",
                "resourceName": "customers/1234567890/assets/92",
                "name": "Reusable image",
                "type": "IMAGE",
                "source": "ADVERTISER",
                "imageAsset": {
                  "fullSize": {
                    "url": "https://example.com/image.png",
                    "widthPixels": 1200,
                    "heightPixels": 628
                  }
                }
              }
            }
            """);
    var linkRow =
        objectMapper.readTree(
            """
            {
              "campaignAsset": {
                "resourceName": "customers/1234567890/campaignAssets/555~92~AD_IMAGE",
                "asset": "customers/1234567890/assets/92",
                "fieldType": "AD_IMAGE",
                "status": "ENABLED"
              },
              "asset": {
                "id": "92",
                "resourceName": "customers/1234567890/assets/92",
                "name": "Reusable image",
                "type": "IMAGE"
              }
            }
            """);
    when(apiClient.searchRows(eq("token"), eq("1234567890"), eq(null), anyString()))
        .thenReturn(List.of(assetRow), List.of(), List.of(linkRow));
    when(apiClient.customerResourceName("1234567890", "campaigns", "555"))
        .thenReturn("customers/1234567890/campaigns/555");
    when(apiClient.customerResourceName("1234567890", "assets", "92"))
        .thenReturn("customers/1234567890/assets/92");

    var mutateResponse =
        objectMapper.readTree(
            """
            {"results":[{"resourceName":"customers/1234567890/campaignAssets/555~92~AD_IMAGE"}]}
            """);
    when(apiClient.mutate(
            eq("token"), eq("1234567890"), eq(null), eq("campaignAssets"), any()))
        .thenReturn(mutateResponse);
    when(apiClient.firstMutatedResourceName(mutateResponse))
        .thenReturn("customers/1234567890/campaignAssets/555~92~AD_IMAGE");

    var result =
        service.attach(
            1L,
            20L,
            "92",
            new GoogleAssetLinkRequest(
                GoogleAssetLinkRequest.Level.CAMPAIGN, 30L, "AD_IMAGE"),
            99L);

    assertThat(result.fieldType()).isEqualTo("AD_IMAGE");

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, Object>>> operations =
        ArgumentCaptor.forClass(List.class);
    verify(apiClient)
        .mutate(
            eq("token"),
            eq("1234567890"),
            eq(null),
            eq("campaignAssets"),
            operations.capture());

    @SuppressWarnings("unchecked")
    Map<String, Object> create =
        (Map<String, Object>) operations.getValue().getFirst().get("create");
    assertThat(create.get("fieldType")).isEqualTo("AD_IMAGE");
  }

  private GoogleAdsAccountContextService.Context context(AdAccount account) {
    return new GoogleAdsAccountContextService.Context(
        account, null, "token", null, false, false);
  }

  private AdAccount account() {
    AdAccount account = new AdAccount();
    account.setId(20L);
    account.setCompanyId(1L);
    account.setExternalAccountId("1234567890");
    account.setCurrency("INR");
    account.setProvider(AdProvider.GOOGLE_ADS);
    account.setManaged(true);
    account.setAccessible(true);
    return account;
  }
}
