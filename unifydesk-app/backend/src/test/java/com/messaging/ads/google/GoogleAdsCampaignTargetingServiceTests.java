package com.messaging.ads.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.campaign.entity.AudienceDefinition;
import com.messaging.ads.entity.AdAccount;
import com.messaging.common.exception.BadRequestException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoogleAdsCampaignTargetingServiceTests {

  @Mock private GoogleAdsAccountContextService contextService;
  @Mock private GoogleAdsApiClient apiClient;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private GoogleAdsCampaignTargetingService service;

  @BeforeEach
  void setUp() {
    service =
        new GoogleAdsCampaignTargetingService(
            contextService, apiClient, null, objectMapper);
  }

  @Test
  void appliesSafeSearchDefaultsWhenNoProviderOverrideIsPresent() throws Exception {
    var settings = service.searchCampaignSettings(objectMapper.readTree("{}"));

    assertThat(settings.targetSearchNetwork()).isTrue();
    assertThat(settings.targetContentNetwork()).isFalse();
    assertThat(settings.positiveGeoTargetType()).isEqualTo("PRESENCE_OR_INTEREST");
  }

  @Test
  void acceptsExplicitNetworkAndPresenceControls() throws Exception {
    var settings =
        service.searchCampaignSettings(
            objectMapper.readTree(
                """
                {
                  "targetSearchNetwork": false,
                  "targetContentNetwork": true,
                  "positiveGeoTargetType": "PRESENCE"
                }
                """));

    assertThat(settings.targetSearchNetwork()).isFalse();
    assertThat(settings.targetContentNetwork()).isTrue();
    assertThat(settings.positiveGeoTargetType()).isEqualTo("PRESENCE");
  }

  @Test
  void rejectsInvalidGeoPresenceModeBeforeProviderMutation() throws Exception {
    assertThatThrownBy(
            () ->
                service.searchCampaignSettings(
                    objectMapper.readTree(
                        """
                        {"positiveGeoTargetType":"SEARCH_INTEREST"}
                        """)))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("PRESENCE");
  }

  @Test
  void parsesAiMaxAutomationAndTextGuidelines() throws Exception {
    var settings =
        service.searchCampaignSettings(
            objectMapper.readTree(
                """
                {
                  "aiMaxEnabled": true,
                  "aiMaxTextCustomizationEnabled": true,
                  "aiMaxFinalUrlExpansionEnabled": true,
                  "aiMaxTermExclusions": ["free", "cheap"],
                  "aiMaxMessagingRestrictions": [
                    "Do not imply guaranteed outcomes"
                  ]
                }
                """));

    assertThat(settings.aiMaxEnabled()).isTrue();
    assertThat(settings.textCustomizationEnabled()).isTrue();
    assertThat(settings.finalUrlExpansionEnabled()).isTrue();
    assertThat(settings.termExclusions()).containsExactly("free", "cheap");
    assertThat(settings.messagingRestrictions())
        .containsExactly("Do not imply guaranteed outcomes");
  }

  @Test
  void rejectsAiMaxGuidelinesWhenAiMaxIsDisabled() throws Exception {
    assertThatThrownBy(
            () ->
                service.searchCampaignSettings(
                    objectMapper.readTree(
                        """
                        {
                          "aiMaxEnabled": false,
                          "aiMaxTermExclusions": ["free"]
                        }
                        """)))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("require AI Max");
  }

  @Test
  void enforcesCurrentAiMaxTextGuidelineLimits() throws Exception {
    String overlongTerm = "x".repeat(31);
    assertThatThrownBy(
            () ->
                service.searchCampaignSettings(
                    objectMapper.readTree(
                        """
                        {
                          "aiMaxEnabled": true,
                          "aiMaxTermExclusions": [""""
                            + overlongTerm
                            + ""]
                        }")))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("30 characters");
  }

  @Test
  void resolvesCountryLanguageAndDaypartDuringPreflight() throws Exception {
    AdAccount account = new AdAccount();
    account.setId(2L);
    account.setExternalAccountId("1234567890");

    when(contextService.requireManaged(1L, 2L))
        .thenReturn(
            new GoogleAdsAccountContextService.Context(
                account, null, "access-token", null, false, false));

    var countryRow =
        objectMapper.readTree(
            """
            {
              "geoTargetConstant": {
                "resourceName": "geoTargetConstants/2356",
                "countryCode": "IN",
                "targetType": "Country",
                "status": "ENABLED"
              }
            }
            """);
    var languageRow =
        objectMapper.readTree(
            """
            {
              "languageConstant": {
                "resourceName": "languageConstants/1000",
                "code": "en",
                "name": "English",
                "targetable": true
              }
            }
            """);

    when(apiClient.searchRows(
            eq("access-token"), eq("1234567890"), eq(null), anyString()))
        .thenAnswer(
            invocation -> {
              String query = invocation.getArgument(3);
              return query.contains("geo_target_constant")
                  ? List.of(countryRow)
                  : List.of(languageRow);
            });

    AudienceDefinition audience = new AudienceDefinition();
    audience.setLocationsJson("[{\"countryCode\":\"IN\"}]");
    audience.setLanguagesJson("[\"en\"]");

    var result =
        service.resolve(
            1L,
            2L,
            audience,
            objectMapper.readTree(
                """
                {
                  "adSchedules": [
                    {
                      "dayOfWeek": "MONDAY",
                      "startTime": "09:00",
                      "endTime": "18:30"
                    }
                  ]
                }
                """));

    assertThat(result.locationCount()).isEqualTo(1);
    assertThat(result.languageCount()).isEqualTo(1);
    assertThat(result.scheduleCount()).isEqualTo(1);
  }

  @Test
  void rejectsNonQuarterHourDaypartDuringPreflight() throws Exception {
    AdAccount account = new AdAccount();
    account.setId(2L);
    account.setExternalAccountId("1234567890");

    when(contextService.requireManaged(1L, 2L))
        .thenReturn(
            new GoogleAdsAccountContextService.Context(
                account, null, "access-token", null, false, false));

    assertThatThrownBy(
            () ->
                service.resolve(
                    1L,
                    2L,
                    null,
                    objectMapper.readTree(
                        """
                        {
                          "adSchedules": [
                            {
                              "dayOfWeek": "MONDAY",
                              "startTime": "09:10",
                              "endTime": "18:00"
                            }
                          ]
                        }
                        """)))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("00, 15, 30, or 45");
  }
}
