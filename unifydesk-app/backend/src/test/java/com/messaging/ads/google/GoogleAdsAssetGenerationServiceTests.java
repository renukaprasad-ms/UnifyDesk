package com.messaging.ads.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.capability.AdFeatureAvailability;
import com.messaging.ads.capability.AdFeatureCapability;
import com.messaging.ads.capability.AdFeatureCapabilityResponse;
import com.messaging.ads.capability.AdFeatureCapabilityService;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdProviderConnection;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.GoogleAssetGenerationImageRequest;
import com.messaging.ads.google.dto.GoogleAssetGenerationTextRequest;
import com.messaging.ads.service.AdActivityLogService;
import com.messaging.common.exception.BadRequestException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoogleAdsAssetGenerationServiceTests {

  @Mock private GoogleAdsAccountContextService contextService;
  @Mock private GoogleAdsApiClient apiClient;
  @Mock private AdFeatureCapabilityService capabilityService;
  @Mock private AdActivityLogService activityLogService;

  private GoogleAdsAssetGenerationService service;
  private GoogleAdsAccountContextService.Context context;

  @BeforeEach
  void setUp() {
    service =
        new GoogleAdsAssetGenerationService(
            contextService, apiClient, capabilityService, activityLogService);

    AdAccount account = new AdAccount();
    account.setId(20L);
    account.setCompanyId(10L);
    account.setProvider(AdProvider.GOOGLE_ADS);
    account.setExternalAccountId("1234567890");
    account.setManaged(true);
    account.setAccessible(true);

    AdProviderConnection connection = new AdProviderConnection();
    connection.setId(30L);
    connection.setCompanyId(10L);
    connection.setProvider(AdProvider.GOOGLE_ADS);

    context =
        new GoogleAdsAccountContextService.Context(
            account, connection, "access-token", "9999999999", false, false);
  }

  @Test
  @SuppressWarnings("unchecked")
  void mapsTextGenerationRequestAndReturnsDraftSuggestions() throws Exception {
    allowCapability();
    when(contextService.requireManaged(10L, 20L)).thenReturn(context);
    when(apiClient.customerColonAction(
            eq("access-token"),
            eq("1234567890"),
            eq("9999999999"),
            eq("generateText"),
            any()))
        .thenReturn(
            new ObjectMapper()
                .readTree(
                    """
                    {
                      "generatedTexts": [
                        {"text":"Shared inbox for every conversation","assetFieldType":"HEADLINE"},
                        {"text":"Bring customer conversations into one workspace.","assetFieldType":"DESCRIPTION"}
                      ]
                    }
                    """));

    var result =
        service.generateText(
            10L,
            20L,
            new GoogleAssetGenerationTextRequest(
                "SEARCH",
                List.of("HEADLINE", "DESCRIPTION"),
                "https://example.com/product",
                "Focus on simple onboarding",
                List.of("shared inbox", "customer messaging")),
            40L);

    assertThat(result.suggestions()).hasSize(2);
    assertThat(result.suggestions().getFirst().assetFieldType()).isEqualTo("HEADLINE");

    ArgumentCaptor<Map<String, Object>> body = ArgumentCaptor.forClass(Map.class);
    verify(apiClient)
        .customerColonAction(
            eq("access-token"),
            eq("1234567890"),
            eq("9999999999"),
            eq("generateText"),
            body.capture());

    assertThat(body.getValue().get("customerId")).isEqualTo("1234567890");
    assertThat(body.getValue().get("advertisingChannelType")).isEqualTo("SEARCH");
    assertThat(body.getValue().get("assetFieldTypes"))
        .isEqualTo(List.of("HEADLINE", "DESCRIPTION"));
    assertThat(body.getValue().get("finalUrl"))
        .isEqualTo("https://example.com/product");
  }

  @Test
  @SuppressWarnings("unchecked")
  void mapsFreeformImageGenerationAndReturnsTemporaryImages() throws Exception {
    allowCapability();
    when(contextService.requireManaged(10L, 20L)).thenReturn(context);
    when(apiClient.customerColonAction(
            eq("access-token"),
            eq("1234567890"),
            eq("9999999999"),
            eq("generateImages"),
            any()))
        .thenReturn(
            new ObjectMapper()
                .readTree(
                    """
                    {
                      "generatedImages": [
                        {
                          "assetFieldType":"SQUARE_MARKETING_IMAGE",
                          "image":{"fullSizeImageUrl":"https://google.example/generated.png"}
                        }
                      ]
                    }
                    """));

    var result =
        service.generateImages(
            10L,
            20L,
            new GoogleAssetGenerationImageRequest(
                "PERFORMANCE_MAX",
                List.of("SQUARE_MARKETING_IMAGE"),
                "FREEFORM",
                null,
                "A clean product workspace on a laptop"),
            40L);

    assertThat(result.suggestions()).hasSize(1);
    assertThat(result.suggestions().getFirst().imageTemporaryUrl())
        .isEqualTo("https://google.example/generated.png");

    ArgumentCaptor<Map<String, Object>> body = ArgumentCaptor.forClass(Map.class);
    verify(apiClient)
        .customerColonAction(
            eq("access-token"),
            eq("1234567890"),
            eq("9999999999"),
            eq("generateImages"),
            body.capture());

    assertThat(body.getValue().get("advertisingChannelType"))
        .isEqualTo("PERFORMANCE_MAX");
    assertThat(body.getValue()).containsKey("freeformGeneration");
  }

  @Test
  void blocksGenerationWhenCapabilityIsUnavailable() {
    when(capabilityService.accountCapability(
            10L, 20L, AdFeatureCapability.GOOGLE_ASSET_GENERATION))
        .thenReturn(
            new AdFeatureCapabilityResponse(
                AdFeatureCapability.GOOGLE_ASSET_GENERATION,
                AdFeatureAvailability.ACCOUNT_ELIGIBILITY_REQUIRED,
                true,
                false,
                false,
                true,
                "Google AI asset generation is not enabled for this account.",
                "v25",
                Set.of("https://www.googleapis.com/auth/adwords")));

    assertThatThrownBy(
            () ->
                service.generateText(
                    10L,
                    20L,
                    new GoogleAssetGenerationTextRequest(
                        "SEARCH",
                        List.of("HEADLINE"),
                        "https://example.com",
                        null,
                        List.of()),
                    40L))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("not enabled");

    verify(contextService, never()).requireManaged(10L, 20L);
  }

  private void allowCapability() {
    when(capabilityService.accountCapability(
            10L, 20L, AdFeatureCapability.GOOGLE_ASSET_GENERATION))
        .thenReturn(
            new AdFeatureCapabilityResponse(
                AdFeatureCapability.GOOGLE_ASSET_GENERATION,
                AdFeatureAvailability.ACCOUNT_ELIGIBILITY_REQUIRED,
                true,
                true,
                null,
                true,
                "Google AssetGenerationService is a closed-beta capability.",
                "v25",
                Set.of("https://www.googleapis.com/auth/adwords")));
  }
}
