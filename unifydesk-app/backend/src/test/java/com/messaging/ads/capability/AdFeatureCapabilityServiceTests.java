package com.messaging.ads.capability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.enums.AdAccountStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.GoogleAdsProperties;
import com.messaging.ads.meta.MetaAdsProperties;
import com.messaging.ads.provider.AdsProviderRegistry;
import com.messaging.ads.repository.AdAccountRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdFeatureCapabilityServiceTests {

  @Mock private AdsProviderRegistry providerRegistry;
  @Mock private AdAccountRepository accountRepository;

  private GoogleAdsProperties googleProperties;
  private MetaAdsProperties metaProperties;
  private AdFeatureCapabilityService service;

  @BeforeEach
  void setUp() {
    googleProperties = new GoogleAdsProperties();
    googleProperties.setApiVersion("v25");
    googleProperties.setDeveloperToken("developer-token");
    metaProperties = new MetaAdsProperties();
    metaProperties.setGraphApiVersion("v26.0");
    service =
        new AdFeatureCapabilityService(
            providerRegistry, accountRepository, googleProperties, metaProperties);
  }

  @Test
  void reportsImplementedAndPlannedGoogleCapabilitiesSeparately() {
    when(providerRegistry.isConfigured(AdProvider.GOOGLE_ADS)).thenReturn(true);

    var capabilities = service.providerCapabilities(AdProvider.GOOGLE_ADS);

    var search =
        capabilities.stream()
            .filter(value -> value.feature() == AdFeatureCapability.GOOGLE_SEARCH)
            .findFirst()
            .orElseThrow();
    var searchTerms =
        capabilities.stream()
            .filter(
                value ->
                    value.feature()
                        == AdFeatureCapability.GOOGLE_SEARCH_TERM_REPORTING)
            .findFirst()
            .orElseThrow();
    var aiMax =
        capabilities.stream()
            .filter(value -> value.feature() == AdFeatureCapability.GOOGLE_AI_MAX)
            .findFirst()
            .orElseThrow();

    assertThat(search.implemented()).isTrue();
    assertThat(search.supported()).isTrue();
    assertThat(searchTerms.implemented()).isTrue();
    assertThat(searchTerms.supported()).isTrue();
    assertThat(aiMax.implemented()).isTrue();
    assertThat(aiMax.supported()).isTrue();

    var planner =
        capabilities.stream()
            .filter(item -> item.feature() == AdFeatureCapability.GOOGLE_KEYWORD_PLANNER)
            .findFirst()
            .orElseThrow();
    assertThat(planner.implemented()).isTrue();
    assertThat(planner.supported()).isTrue();
    assertThat(aiMax.availability()).isEqualTo(AdFeatureAvailability.AVAILABLE);
    assertThat(planner.availability()).isEqualTo(AdFeatureAvailability.AVAILABLE);

    var forecast =
        capabilities.stream()
            .filter(item -> item.feature() == AdFeatureCapability.GOOGLE_FORECAST)
            .findFirst()
            .orElseThrow();
    assertThat(forecast.implemented()).isTrue();
    assertThat(forecast.supported()).isTrue();
    assertThat(forecast.availability()).isEqualTo(AdFeatureAvailability.AVAILABLE);

    var reusableAssets =
        capabilities.stream()
            .filter(item -> item.feature() == AdFeatureCapability.GOOGLE_REUSABLE_ASSETS)
            .findFirst()
            .orElseThrow();
    assertThat(reusableAssets.implemented()).isTrue();
    assertThat(reusableAssets.supported()).isTrue();
    assertThat(reusableAssets.availability())
        .isEqualTo(AdFeatureAvailability.AVAILABLE);

    var assetGeneration =
        capabilities.stream()
            .filter(item -> item.feature() == AdFeatureCapability.GOOGLE_ASSET_GENERATION)
            .findFirst()
            .orElseThrow();
    assertThat(assetGeneration.implemented()).isTrue();
    assertThat(assetGeneration.supported()).isTrue();
    assertThat(assetGeneration.availability())
        .isEqualTo(AdFeatureAvailability.ACCOUNT_ELIGIBILITY_REQUIRED);

    var appCampaign =
        capabilities.stream()
            .filter(item -> item.feature() == AdFeatureCapability.GOOGLE_APP_CAMPAIGN)
            .findFirst()
            .orElseThrow();
    assertThat(appCampaign.implemented()).isTrue();
    assertThat(appCampaign.supported()).isTrue();
    assertThat(appCampaign.availability()).isEqualTo(AdFeatureAvailability.AVAILABLE);
  }

  @Test
  void reportsMetaCustomerListAudienceAsImplemented() {
    when(providerRegistry.isConfigured(AdProvider.META_ADS)).thenReturn(true);

    var capability =
        service.providerCapabilities(AdProvider.META_ADS).stream()
            .filter(
                item ->
                    item.feature()
                        == AdFeatureCapability.META_CUSTOM_AUDIENCE_CUSTOMER_LIST)
            .findFirst()
            .orElseThrow();

    assertThat(capability.implemented()).isTrue();
    assertThat(capability.supported()).isTrue();
    assertThat(capability.availability()).isEqualTo(AdFeatureAvailability.AVAILABLE);
  }

  @Test
  void reportsMetaAdvantageAudienceAsImplemented() {
    when(providerRegistry.isConfigured(AdProvider.META_ADS)).thenReturn(true);

    var capability =
        service.providerCapabilities(AdProvider.META_ADS).stream()
            .filter(
                item ->
                    item.feature()
                        == AdFeatureCapability.META_ADVANTAGE_AUDIENCE)
            .findFirst()
            .orElseThrow();

    assertThat(capability.implemented()).isTrue();
    assertThat(capability.supported()).isTrue();
    assertThat(capability.availability()).isEqualTo(AdFeatureAvailability.AVAILABLE);
  }

  @Test
  void reportsMetaAdvantagePlacementsAsImplemented() {
    when(providerRegistry.isConfigured(AdProvider.META_ADS)).thenReturn(true);

    var capability =
        service.providerCapabilities(AdProvider.META_ADS).stream()
            .filter(
                item ->
                    item.feature()
                        == AdFeatureCapability.META_ADVANTAGE_PLACEMENTS)
            .findFirst()
            .orElseThrow();

    assertThat(capability.implemented()).isTrue();
    assertThat(capability.supported()).isTrue();
    assertThat(capability.availability()).isEqualTo(AdFeatureAvailability.AVAILABLE);
  }

  @Test
  void managedAccountRequirementIsResolvedPerAccount() {
    when(providerRegistry.isConfigured(AdProvider.META_ADS)).thenReturn(true);

    AdAccount account = new AdAccount();
    account.setId(22L);
    account.setCompanyId(11L);
    account.setConnectionId(33L);
    account.setProvider(AdProvider.META_ADS);
    account.setExternalAccountId("act_123");
    account.setName("Meta");
    account.setStatus(AdAccountStatus.ACTIVE);
    account.setAccessible(true);
    account.setManaged(false);
    when(accountRepository.findByIdAndCompanyId(22L, 11L)).thenReturn(Optional.of(account));

    var campaignCreate =
        service.accountCapability(11L, 22L, AdFeatureCapability.CAMPAIGN_CREATE);

    assertThat(campaignCreate.implemented()).isTrue();
    assertThat(campaignCreate.supported()).isFalse();
    assertThat(campaignCreate.accountEligible()).isFalse();
    assertThat(campaignCreate.reason()).contains("not enabled for management");
  }
}
