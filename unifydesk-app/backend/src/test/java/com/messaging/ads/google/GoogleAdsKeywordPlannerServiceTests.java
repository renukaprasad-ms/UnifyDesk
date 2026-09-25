package com.messaging.ads.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.campaign.dto.AudienceLocationInput;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.dto.GoogleKeywordForecastRequest;
import com.messaging.ads.google.dto.GoogleKeywordPlannerIdeasRequest;
import com.messaging.ads.google.dto.GoogleKeywordPlannerResponse;
import com.messaging.ads.service.AdActivityLogService;
import com.messaging.common.exception.BadRequestException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class GoogleAdsKeywordPlannerServiceTests {

  @Mock private GoogleAdsAccountContextService contextService;
  @Mock private GoogleAdsApiClient apiClient;
  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;
  @Mock private AdActivityLogService activityLogService;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private GoogleAdsKeywordPlannerService service;

  @BeforeEach
  void setUp() {
    lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    service =
        new GoogleAdsKeywordPlannerService(
            contextService,
            apiClient,
            redisTemplate,
            objectMapper,
            activityLogService);
  }

  @Test
  void generatesKeywordIdeasWithTargetingHistoricalMetricsAndConcepts() throws Exception {
    AdAccount account = account();
    when(contextService.requireManaged(1L, 20L))
        .thenReturn(
            new GoogleAdsAccountContextService.Context(
                account, null, "token", null, false, false));
    when(valueOperations.get(anyString())).thenReturn(null);

    when(apiClient.searchRows(eq("token"), eq("1234567890"), eq(null), anyString()))
        .thenAnswer(
            invocation -> {
              String query = invocation.getArgument(3);
              if (query.contains("geo_target_constant")) {
                return List.of(
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
                        """));
              }
              return List.of(
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
                      """));
            });

    when(apiClient.customerColonAction(
            eq("token"),
            eq("1234567890"),
            eq(null),
            eq("generateKeywordIdeas"),
            any()))
        .thenReturn(
            objectMapper.readTree(
                """
                {
                  "results": [
                    {
                      "text": "customer support software",
                      "closeVariants": ["support software"],
                      "keywordIdeaMetrics": {
                        "avgMonthlySearches": "1200",
                        "competition": "HIGH",
                        "competitionIndex": "81",
                        "lowTopOfPageBidMicros": "2500000",
                        "highTopOfPageBidMicros": "7500000",
                        "averageCpcMicros": "4200000",
                        "monthlySearchVolumes": [
                          {"year":"2026","month":"AUGUST","monthlySearches":"1300"}
                        ]
                      },
                      "keywordAnnotations": {
                        "concepts": [
                          {
                            "name": "Customer service software",
                            "conceptGroup": {
                              "name": "Business software",
                              "type": "BRAND_OR_OTHER"
                            }
                          }
                        ]
                      }
                    }
                  ],
                  "totalSize": "1"
                }
                """));

    GoogleKeywordPlannerResponse result =
        service.ideas(
            1L,
            20L,
            new GoogleKeywordPlannerIdeasRequest(
                List.of("customer support"),
                "https://example.com",
                false,
                List.of(new AudienceLocationInput("IN", null, null, null, null, null, null)),
                "en",
                "GOOGLE_SEARCH",
                false,
                100,
                null),
            99L);

    assertThat(result.cached()).isFalse();
    assertThat(result.currency()).isEqualTo("INR");
    assertThat(result.rows()).singleElement().satisfies(
        row -> {
          assertThat(row.text()).isEqualTo("customer support software");
          assertThat(row.averageMonthlySearches()).isEqualTo(1200L);
          assertThat(row.competition()).isEqualTo("HIGH");
          assertThat(row.lowTopOfPageBid()).isEqualByComparingTo("2.5");
          assertThat(row.highTopOfPageBid()).isEqualByComparingTo("7.5");
          assertThat(row.averageCpc()).isEqualByComparingTo("4.2");
          assertThat(row.closeVariants()).containsExactly("support software");
          assertThat(row.monthlyVolumes()).singleElement()
              .satisfies(month -> assertThat(month.searches()).isEqualTo(1300L));
          assertThat(row.concepts()).singleElement()
              .satisfies(concept -> assertThat(concept.groupName()).isEqualTo("Business software"));
        });

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> body = ArgumentCaptor.forClass(Map.class);
    verify(apiClient)
        .customerColonAction(
            eq("token"),
            eq("1234567890"),
            eq(null),
            eq("generateKeywordIdeas"),
            body.capture());

    assertThat(body.getValue().get("language")).isEqualTo("languageConstants/1000");
    assertThat(body.getValue().get("geoTargetConstants"))
        .isEqualTo(List.of("geoTargetConstants/2356"));
    assertThat(body.getValue().get("keywordPlanNetwork")).isEqualTo("GOOGLE_SEARCH");
    assertThat(body.getValue()).containsKey("keywordAndUrlSeed");
    assertThat(body.getValue().get("keywordAnnotation"))
        .isEqualTo(List.of("KEYWORD_CONCEPT"));

    verify(valueOperations)
        .set(anyString(), anyString(), eq(Duration.ofHours(6)));
  }

  @Test
  void generatesHistoricalMetricsForEnteredKeywords() throws Exception {
    AdAccount account = account();
    when(contextService.requireManaged(1L, 20L))
        .thenReturn(
            new GoogleAdsAccountContextService.Context(
                account, null, "token", null, false, false));
    when(valueOperations.get(anyString())).thenReturn(null);
    when(apiClient.customerColonAction(
            eq("token"),
            eq("1234567890"),
            eq(null),
            eq("generateKeywordHistoricalMetrics"),
            any()))
        .thenReturn(
            objectMapper.readTree(
                """
                {
                  "results": [
                    {
                      "text": "omnichannel inbox",
                      "closeVariants": ["omni channel inbox"],
                      "keywordMetrics": {
                        "avgMonthlySearches": "900",
                        "competition": "MEDIUM",
                        "competitionIndex": "54",
                        "lowTopOfPageBidMicros": "1500000",
                        "highTopOfPageBidMicros": "4800000"
                      }
                    }
                  ]
                }
                """));

    var result =
        service.historical(
            1L,
            20L,
            new com.messaging.ads.google.dto.GoogleKeywordPlannerHistoricalRequest(
                List.of("omnichannel inbox"),
                List.of(),
                null,
                "GOOGLE_SEARCH",
                false),
            99L);

    assertThat(result.rows()).singleElement().satisfies(
        row -> {
          assertThat(row.text()).isEqualTo("omnichannel inbox");
          assertThat(row.averageMonthlySearches()).isEqualTo(900L);
          assertThat(row.lowTopOfPageBid()).isEqualByComparingTo("1.5");
          assertThat(row.highTopOfPageBid()).isEqualByComparingTo("4.8");
        });
    verify(valueOperations)
        .set(anyString(), anyString(), eq(Duration.ofHours(24)));
  }

  @Test
  void generatesKeywordForecastWithManualCpc() throws Exception {
    AdAccount account = account();
    when(contextService.requireManaged(1L, 20L))
        .thenReturn(
            new GoogleAdsAccountContextService.Context(
                account, null, "token", null, false, false));
    when(valueOperations.get(anyString())).thenReturn(null);
    when(apiClient.customerColonAction(
            eq("token"),
            eq("1234567890"),
            eq(null),
            eq("generateKeywordForecastMetrics"),
            any()))
        .thenReturn(
            objectMapper.readTree(
                """
                {
                  "campaignForecastMetrics": {
                    "clicks": 123.5,
                    "costMicros": "50000000",
                    "averageCpcMicros": "404858",
                    "conversions": 7.25,
                    "averageCpaMicros": "6896552"
                  }
                }
                """));

    LocalDate start = LocalDate.now().plusDays(1);
    LocalDate end = start.plusDays(29);
    var result =
        service.forecast(
            1L,
            20L,
            new GoogleKeywordForecastRequest(
                List.of(
                    new GoogleKeywordForecastRequest.Keyword(
                        "omnichannel inbox",
                        GoogleKeywordForecastRequest.MatchType.PHRASE)),
                List.of(),
                null,
                start,
                end,
                GoogleKeywordForecastRequest.BiddingStrategy.MANUAL_CPC,
                new BigDecimal("100"),
                new BigDecimal("8"),
                null),
            99L);

    assertThat(result.clicks()).isEqualTo(123.5);
    assertThat(result.cost()).isEqualByComparingTo("50");
    assertThat(result.averageCpc()).isEqualByComparingTo("0.404858");
    assertThat(result.conversions()).isEqualTo(7.25);
    assertThat(result.averageCpa()).isEqualByComparingTo("6.896552");

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> body = ArgumentCaptor.forClass(Map.class);
    verify(apiClient)
        .customerColonAction(
            eq("token"),
            eq("1234567890"),
            eq(null),
            eq("generateKeywordForecastMetrics"),
            body.capture());

    @SuppressWarnings("unchecked")
    Map<String, Object> campaign =
        (Map<String, Object>) body.getValue().get("campaign");
    @SuppressWarnings("unchecked")
    Map<String, Object> strategy =
        (Map<String, Object>) campaign.get("biddingStrategy");
    @SuppressWarnings("unchecked")
    Map<String, Object> manual =
        (Map<String, Object>) strategy.get("manualCpcBiddingStrategy");
    assertThat(manual.get("dailyBudgetMicros")).isEqualTo(100_000_000L);
    assertThat(manual.get("maxCpcBidMicros")).isEqualTo(8_000_000L);
    verify(valueOperations)
        .set(anyString(), anyString(), eq(Duration.ofHours(1)));
  }

  @Test
  void validatesForecastWindowAndRequiredBidInputs() {
    AdAccount account = account();
    when(contextService.requireManaged(1L, 20L))
        .thenReturn(
            new GoogleAdsAccountContextService.Context(
                account, null, "token", null, false, false));

    assertThatThrownBy(
            () ->
                service.forecast(
                    1L,
                    20L,
                    new GoogleKeywordForecastRequest(
                        List.of(
                            new GoogleKeywordForecastRequest.Keyword(
                                "crm",
                                GoogleKeywordForecastRequest.MatchType.BROAD)),
                        List.of(),
                        null,
                        LocalDate.now(),
                        LocalDate.now().plusDays(30),
                        GoogleKeywordForecastRequest.BiddingStrategy.MAXIMIZE_CLICKS,
                        new BigDecimal("100"),
                        null,
                        null),
                    99L))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("future");

    assertThatThrownBy(
            () ->
                service.forecast(
                    1L,
                    20L,
                    new GoogleKeywordForecastRequest(
                        List.of(
                            new GoogleKeywordForecastRequest.Keyword(
                                "crm",
                                GoogleKeywordForecastRequest.MatchType.BROAD)),
                        List.of(),
                        null,
                        LocalDate.now().plusDays(1),
                        LocalDate.now().plusDays(30),
                        GoogleKeywordForecastRequest.BiddingStrategy.MANUAL_CPC,
                        new BigDecimal("100"),
                        null,
                        null),
                    99L))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("maxCpcBid");
  }

  @Test
  void returnsCachedPlannerResponseWithoutCallingGoogle() throws Exception {
    AdAccount account = account();
    when(contextService.requireManaged(1L, 20L))
        .thenReturn(
            new GoogleAdsAccountContextService.Context(
                account, null, "token", null, false, false));

    GoogleKeywordPlannerResponse cached =
        new GoogleKeywordPlannerResponse(
            false,
            "INR",
            "GOOGLE_SEARCH",
            null,
            List.of(),
            List.of(
                new GoogleKeywordPlannerResponse.Row(
                    "crm",
                    500L,
                    "MEDIUM",
                    50L,
                    null,
                    null,
                    null,
                    List.of(),
                    List.of(),
                    List.of())),
            null,
            1);
    when(valueOperations.get(anyString()))
        .thenReturn(objectMapper.writeValueAsString(cached));

    var result =
        service.ideas(
            1L,
            20L,
            new GoogleKeywordPlannerIdeasRequest(
                List.of("crm"),
                null,
                false,
                List.of(),
                null,
                null,
                false,
                null,
                null),
            99L);

    assertThat(result.cached()).isTrue();
    assertThat(result.rows()).extracting(GoogleKeywordPlannerResponse.Row::text)
        .containsExactly("crm");
    verify(apiClient, never())
        .customerColonAction(anyString(), anyString(), any(), anyString(), any());
  }

  @Test
  void rejectsRadiusTargetingInsteadOfSilentlyDroppingIt() {
    AdAccount account = account();
    when(contextService.requireManaged(1L, 20L))
        .thenReturn(
            new GoogleAdsAccountContextService.Context(
                account, null, "token", null, false, false));

    assertThatThrownBy(
            () ->
                service.ideas(
                    1L,
                    20L,
                    new GoogleKeywordPlannerIdeasRequest(
                        List.of("crm"),
                        null,
                        false,
                        List.of(
                            new AudienceLocationInput(
                                "IN",
                                null,
                                "Bengaluru",
                                null,
                                12.97,
                                77.59,
                                20.0)),
                        "en",
                        "GOOGLE_SEARCH",
                        false,
                        null,
                        null),
                    99L))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("proximity/radius");
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
