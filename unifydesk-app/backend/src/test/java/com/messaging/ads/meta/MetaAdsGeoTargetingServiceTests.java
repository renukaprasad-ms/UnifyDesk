package com.messaging.ads.meta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.campaign.dto.AudienceLocationInput;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdProviderConnection;
import com.messaging.ads.enums.AdConnectionStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdProviderConnectionRepository;
import com.messaging.common.exception.BadRequestException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MetaAdsGeoTargetingServiceTests {

  @Mock private AdAccountRepository accountRepository;
  @Mock private AdProviderConnectionRepository connectionRepository;
  @Mock private MetaAdsTokenService tokenService;
  @Mock private MetaAdsApiClient apiClient;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private MetaAdsGeoTargetingService service;

  @BeforeEach
  void setUp() {
    service =
        new MetaAdsGeoTargetingService(
            accountRepository, connectionRepository, tokenService, apiClient);
  }

  @Test
  void resolvesCanonicalCityToProviderKey() throws Exception {
    prepareContext();

    when(apiClient.searchAdGeoLocations(
            eq("access-token"),
            eq("Bengaluru"),
            eq(List.of("city")),
            eq("IN"),
            anyInt()))
        .thenReturn(
            objectMapper.readTree(
                """
                {
                  "data": [
                    {
                      "key": "1028297",
                      "name": "Bengaluru",
                      "type": "city",
                      "country_code": "IN",
                      "country_name": "India",
                      "region": "Karnataka",
                      "region_id": "1735"
                    }
                  ]
                }
                """));

    var result =
        service.resolveCanonical(
            1L,
            20L,
            List.of(
                new AudienceLocationInput(
                    "IN",
                    "Karnataka",
                    "Bengaluru",
                    null,
                    null,
                    null,
                    null)));

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().type()).isEqualTo("CITY");
    assertThat(result.getFirst().key()).isEqualTo("1028297");
    assertThat(result.getFirst().name()).isEqualTo("Bengaluru");
    assertThat(result.getFirst().locationTypes())
        .containsExactly("home", "recent");
  }

  @Test
  void resolvesCoordinateRadiusWithoutProviderSearch() {
    prepareContext();

    var result =
        service.resolveCanonical(
            1L,
            20L,
            List.of(
                new AudienceLocationInput(
                    "IN",
                    null,
                    "Bengaluru",
                    null,
                    12.9716,
                    77.5946,
                    8.5)));

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().type()).isEqualTo("CUSTOM");
    assertThat(result.getFirst().latitude()).isEqualTo(12.9716);
    assertThat(result.getFirst().longitude()).isEqualTo(77.5946);
    assertThat(result.getFirst().radiusKm()).isEqualTo(8.5);
    verify(apiClient, never())
        .searchAdGeoLocations(
            eq("access-token"),
            eq("Bengaluru"),
            eq(List.of("city")),
            eq("IN"),
            anyInt());
  }

  @Test
  void rejectsRadiusWithoutCoordinatesBeforeProviderSearch() {
    prepareContext();

    assertThatThrownBy(
            () ->
                service.resolveCanonical(
                    1L,
                    20L,
                    List.of(
                        new AudienceLocationInput(
                            "IN",
                            null,
                            "Bengaluru",
                            null,
                            null,
                            null,
                            10.0))))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("latitude and longitude");
  }

  @Test
  void searchReturnsProviderGeoSuggestions() throws Exception {
    prepareContext();

    when(apiClient.searchAdGeoLocations(
            eq("access-token"),
            eq("Karnataka"),
            eq(List.of("region")),
            eq("IN"),
            eq(20)))
        .thenReturn(
            objectMapper.readTree(
                """
                {
                  "data": [
                    {
                      "key": "1735",
                      "name": "Karnataka",
                      "type": "region",
                      "country_code": "IN",
                      "country_name": "India"
                    }
                  ]
                }
                """));

    var result =
        service.search(
            1L, 20L, "Karnataka", List.of("REGION"), "IN", 20);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().key()).isEqualTo("1735");
    assertThat(result.getFirst().type()).isEqualTo("REGION");
    assertThat(result.getFirst().countryCode()).isEqualTo("IN");
  }

  private void prepareContext() {
    AdAccount account = new AdAccount();
    account.setId(20L);
    account.setCompanyId(1L);
    account.setConnectionId(30L);
    account.setProvider(AdProvider.META_ADS);
    account.setManaged(true);
    account.setAccessible(true);

    AdProviderConnection connection = new AdProviderConnection();
    connection.setId(30L);
    connection.setCompanyId(1L);
    connection.setProvider(AdProvider.META_ADS);
    connection.setStatus(AdConnectionStatus.ACTIVE);

    when(accountRepository.findByIdAndCompanyId(20L, 1L))
        .thenReturn(Optional.of(account));
    when(connectionRepository.findByIdAndCompanyId(30L, 1L))
        .thenReturn(Optional.of(connection));
    when(tokenService.read(30L))
        .thenReturn(
            new MetaAdsTokenService.TokenValue(
                new MetaAdsStoredToken(
                    "access-token", "bearer", "v26.0", "meta-user"),
                null));
  }
}
