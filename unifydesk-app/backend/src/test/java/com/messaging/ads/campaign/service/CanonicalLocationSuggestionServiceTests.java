package com.messaging.ads.campaign.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.GoogleAdsCampaignTargetingService;
import com.messaging.ads.google.dto.GoogleGeoLocationSuggestion;
import com.messaging.ads.meta.MetaAdsGeoTargetingService;
import com.messaging.ads.meta.dto.MetaGeoLocationSuggestion;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CanonicalLocationSuggestionServiceTests {

  @Mock private GoogleAdsCampaignTargetingService googleTargetingService;
  @Mock private MetaAdsGeoTargetingService metaGeoTargetingService;

  private CanonicalLocationSuggestionService service;

  @BeforeEach
  void setUp() {
    service =
        new CanonicalLocationSuggestionService(
            googleTargetingService, metaGeoTargetingService);
  }

  @Test
  void mergesSameCityAcrossGoogleAndMeta() {
    when(googleTargetingService.searchLocations(
            1L, 10L, "Bengaluru", "IN", 30))
        .thenReturn(
            List.of(
                new GoogleGeoLocationSuggestion(
                    "geoTargetConstants/100",
                    "Bengaluru",
                    "Bengaluru,Karnataka,India",
                    "City",
                    "IN")));

    when(metaGeoTargetingService.search(
            1L,
            20L,
            "Bengaluru",
            List.of("COUNTRY", "REGION", "CITY", "ZIP"),
            "IN",
            30))
        .thenReturn(
            List.of(
                new MetaGeoLocationSuggestion(
                    "1028297",
                    "Bengaluru",
                    "CITY",
                    "IN",
                    "India",
                    "Karnataka",
                    "1735",
                    true,
                    true)));

    var result =
        service.search(1L, "Bengaluru", "IN", 10L, 20L, 10);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().type()).isEqualTo("CITY");
    assertThat(result.getFirst().countryCode()).isEqualTo("IN");
    assertThat(result.getFirst().region()).isEqualTo("Karnataka");
    assertThat(result.getFirst().city()).isEqualTo("Bengaluru");
    assertThat(result.getFirst().supportedProviders())
        .containsExactlyInAnyOrder(AdProvider.GOOGLE_ADS, AdProvider.META_ADS);
  }

  @Test
  void keepsProviderOnlySuggestionVisibleInsteadOfClaimingCrossProviderSupport() {
    when(googleTargetingService.searchLocations(
            1L, 10L, "560001", "IN", 30))
        .thenReturn(List.of());

    when(metaGeoTargetingService.search(
            1L,
            20L,
            "560001",
            List.of("COUNTRY", "REGION", "CITY", "ZIP"),
            "IN",
            30))
        .thenReturn(
            List.of(
                new MetaGeoLocationSuggestion(
                    "560001",
                    "560001",
                    "ZIP",
                    "IN",
                    "India",
                    "Karnataka",
                    "1735",
                    true,
                    true)));

    var result =
        service.search(1L, "560001", "IN", 10L, 20L, 10);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().postalCode()).isEqualTo("560001");
    assertThat(result.getFirst().supportedProviders())
        .containsExactly(AdProvider.META_ADS);
  }
}
