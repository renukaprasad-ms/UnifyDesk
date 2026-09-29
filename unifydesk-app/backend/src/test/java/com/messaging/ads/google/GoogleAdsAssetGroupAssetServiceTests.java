package com.messaging.ads.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.entity.AdProviderConnection;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.ads.service.AdActivityLogService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoogleAdsAssetGroupAssetServiceTests {

  @Mock private GoogleAdsAccountContextService contextService;
  @Mock private GoogleAdsApiClient apiClient;
  @Mock private AdCampaignRepository campaignRepository;
  @Mock private AdActivityLogService activityLogService;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private GoogleAdsAssetGroupAssetService service;

  @BeforeEach
  void setUp() {
    service =
        new GoogleAdsAssetGroupAssetService(
            contextService, apiClient, campaignRepository, activityLogService);
  }

  @Test
  void exposesProviderServingAndPolicyDiagnostics() throws Exception {
    AdCampaign campaign = new AdCampaign();
    campaign.setId(700L);
    campaign.setCompanyId(10L);
    campaign.setAdAccountId(20L);
    campaign.setProvider(AdProvider.GOOGLE_ADS);
    campaign.setObjective("PERFORMANCE_MAX");
    campaign.setExternalCampaignId("777");
    campaign.setAccessible(true);
    when(campaignRepository.findByIdAndCompanyId(700L, 10L))
        .thenReturn(Optional.of(campaign));

    AdAccount account = new AdAccount();
    account.setId(20L);
    account.setCompanyId(10L);
    account.setProvider(AdProvider.GOOGLE_ADS);
    account.setExternalAccountId("1234567890");
    account.setManaged(true);
    account.setAccessible(true);
    when(contextService.requireManaged(10L, 20L))
        .thenReturn(
            new GoogleAdsAccountContextService.Context(
                account,
                new AdProviderConnection(),
                "access-token",
                null,
                false,
                false));

    when(apiClient.searchRows(
            eq("access-token"), eq("1234567890"), eq(null), anyString()))
        .thenReturn(
            List.of(
                objectMapper.readTree(
                    """
                    {
                      "assetGroupAsset": {
                        "resourceName": "customers/1234567890/assetGroupAssets/501~601~HEADLINE",
                        "fieldType": "HEADLINE",
                        "status": "ENABLED",
                        "primaryStatus": "LIMITED",
                        "primaryStatusReasons": ["ASSET_APPROVED_LABELED"],
                        "source": "ADVERTISER",
                        "policySummary": {
                          "approvalStatus": "APPROVED_LIMITED",
                          "reviewStatus": "REVIEWED"
                        }
                      },
                      "asset": {
                        "resourceName": "customers/1234567890/assets/601",
                        "id": "601",
                        "name": "Headline",
                        "type": "TEXT",
                        "textAsset": {"text": "Reach more customers"}
                      }
                    }
                    """)));

    var result = service.list(10L, 700L, "501");

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().primaryStatus()).isEqualTo("LIMITED");
    assertThat(result.getFirst().primaryStatusReasons())
        .containsExactly("ASSET_APPROVED_LABELED");
    assertThat(result.getFirst().source()).isEqualTo("ADVERTISER");
    assertThat(result.getFirst().policyApprovalStatus())
        .isEqualTo("APPROVED_LIMITED");
    assertThat(result.getFirst().policyReviewStatus()).isEqualTo("REVIEWED");
  }
}
