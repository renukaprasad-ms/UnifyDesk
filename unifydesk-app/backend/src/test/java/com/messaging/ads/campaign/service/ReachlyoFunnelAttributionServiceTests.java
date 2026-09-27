package com.messaging.ads.campaign.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.messaging.ads.campaign.dto.ReachlyoCampaignAnalyticsResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReachlyoFunnelAttributionServiceTests {

  @Mock private ReachlyoCampaignAnalyticsService analyticsService;

  private ReachlyoFunnelAttributionService service;

  @BeforeEach
  void setUp() {
    service = new ReachlyoFunnelAttributionService(analyticsService);
  }

  @Test
  void normalizesProviderAttributedFunnelWithoutClaimingPersonLevelIdentity() {
    LocalDate from = LocalDate.of(2026, 9, 1);
    LocalDate to = LocalDate.of(2026, 9, 30);
    var summary =
        new ReachlyoCampaignAnalyticsResponse.Summary(
            new BigDecimal("1000"),
            10_000L,
            1_000L,
            new BigDecimal("10"),
            new BigDecimal("100"),
            new BigDecimal("5000"),
            new BigDecimal("10"),
            new BigDecimal("5"),
            new BigDecimal("80"),
            new BigDecimal("20"));
    when(analyticsService.analytics(10L, 100L, from, to))
        .thenReturn(
            new ReachlyoCampaignAnalyticsResponse(
                "100",
                "INR",
                from,
                to,
                summary,
                List.of(),
                List.of(),
                true,
                null));

    var result = service.attribution(10L, 100L, from, to);

    assertThat(result.attributionMode()).isEqualTo("PROVIDER_ATTRIBUTED_NORMALIZED");
    assertThat(result.totals().clickThroughRate()).isEqualByComparingTo("10");
    assertThat(result.totals().clickToLeadRate()).isEqualByComparingTo("8");
    assertThat(result.totals().leadToPurchaseRate()).isEqualByComparingTo("25");
    assertThat(result.limitation()).contains("does not claim deterministic person-level");
  }
}
