package com.messaging.ads.campaign.service;

import com.messaging.ads.campaign.dto.ReachlyoFunnelAttributionResponse;
import com.messaging.ads.campaign.dto.ReachlyoFunnelAttributionResponse.DeploymentFunnel;
import com.messaging.ads.campaign.dto.ReachlyoFunnelAttributionResponse.Funnel;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReachlyoFunnelAttributionService {

  private static final BigDecimal HUNDRED = new BigDecimal("100");

  private final ReachlyoCampaignAnalyticsService analyticsService;

  public ReachlyoFunnelAttributionResponse attribution(
      Long companyId, Long campaignId, LocalDate from, LocalDate to) {
    var analytics = analyticsService.analytics(companyId, campaignId, from, to);
    Funnel total = funnel(analytics.summary());
    List<DeploymentFunnel> deployments =
        analytics.deployments().stream()
            .map(
                row ->
                    new DeploymentFunnel(
                        row.deploymentId(),
                        row.provider(),
                        row.channelType(),
                        row.localCampaignId(),
                        funnel(row.summary())))
            .toList();

    return new ReachlyoFunnelAttributionResponse(
        analytics.campaignId(),
        analytics.currency(),
        analytics.from(),
        analytics.to(),
        "PROVIDER_ATTRIBUTED_NORMALIZED",
        "UnifyDesk normalizes provider-attributed impressions, clicks, leads, conversions and purchases. It does not claim deterministic person-level cross-device attribution between Google and Meta.",
        total,
        deployments);
  }

  private Funnel funnel(
      com.messaging.ads.campaign.dto.ReachlyoCampaignAnalyticsResponse.Summary summary) {
    return new Funnel(
        summary.impressions(),
        summary.clicks(),
        summary.leads(),
        summary.conversions(),
        summary.purchases(),
        summary.spend(),
        summary.conversionValue(),
        rate(BigDecimal.valueOf(summary.clicks()), BigDecimal.valueOf(summary.impressions())),
        rate(summary.leads(), BigDecimal.valueOf(summary.clicks())),
        rate(summary.purchases(), summary.leads()),
        rate(summary.conversions(), BigDecimal.valueOf(summary.clicks())),
        summary.cpa(),
        summary.roas());
  }

  private BigDecimal rate(BigDecimal numerator, BigDecimal denominator) {
    if (numerator == null
        || denominator == null
        || denominator.signum() == 0) return null;
    return numerator
        .multiply(HUNDRED)
        .divide(denominator, 4, RoundingMode.HALF_UP);
  }
}
