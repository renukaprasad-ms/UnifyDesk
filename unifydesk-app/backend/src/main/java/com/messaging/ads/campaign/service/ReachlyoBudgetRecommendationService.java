package com.messaging.ads.campaign.service;

import com.messaging.ads.campaign.dto.ReachlyoBudgetRecommendationResponse;
import com.messaging.ads.campaign.dto.ReachlyoBudgetRecommendationResponse.ChannelRecommendation;
import com.messaging.ads.campaign.entity.BudgetPlan;
import com.messaging.ads.campaign.entity.CampaignChannel;
import com.messaging.ads.campaign.repository.BudgetPlanRepository;
import com.messaging.ads.campaign.repository.CampaignChannelRepository;
import com.messaging.common.exception.NotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReachlyoBudgetRecommendationService {

  private static final BigDecimal ZERO = BigDecimal.ZERO;
  private static final BigDecimal MIN_SHARE = new BigDecimal("10");

  private final ReachlyoCampaignAnalyticsService analyticsService;
  private final BudgetPlanRepository budgetPlanRepository;
  private final CampaignChannelRepository channelRepository;

  @Transactional(readOnly = true)
  public ReachlyoBudgetRecommendationResponse recommend(
      Long companyId, Long campaignId, LocalDate from, LocalDate to) {
    LocalDate effectiveTo = to == null ? LocalDate.now() : to;
    LocalDate effectiveFrom = from == null ? effectiveTo.minusDays(29) : from;

    var analytics =
        analyticsService.analytics(companyId, campaignId, effectiveFrom, effectiveTo);
    BudgetPlan budget =
        budgetPlanRepository
            .findByCompanyIdAndReachlyoCampaignId(companyId, campaignId)
            .orElseThrow(() -> new NotFoundException("Campaign budget plan not found"));
    List<CampaignChannel> channels =
        channelRepository.findByCompanyIdAndReachlyoCampaignIdOrderByIdAsc(
            companyId, campaignId);
    Map<String, CampaignChannel> channelById = new LinkedHashMap<>();
    channels.forEach(channel -> channelById.put(channel.getId().toString(), channel));

    var active =
        analytics.deployments().stream()
            .filter(row -> row.summary().spend().signum() > 0)
            .toList();
    if (active.size() < 2) {
      return response(
          analytics,
          budget,
          channelById,
          "LOW",
          false,
          "At least two deployed channels with spend are required before UnifyDesk can compare budget efficiency.",
          null);
    }

    BigDecimal totalConversions =
        active.stream()
            .map(row -> row.summary().conversions())
            .reduce(ZERO, BigDecimal::add);
    boolean enoughConversions = totalConversions.compareTo(new BigDecimal("10")) >= 0;
    String confidence =
        totalConversions.compareTo(new BigDecimal("30")) >= 0
            ? "HIGH"
            : enoughConversions ? "MEDIUM" : "LOW";

    Map<String, BigDecimal> scores = new LinkedHashMap<>();
    BigDecimal totalScore = ZERO;
    for (var row : active) {
      BigDecimal conversions = row.summary().conversions();
      BigDecimal spend = row.summary().spend();
      BigDecimal roas = row.summary().roas();
      BigDecimal score;
      if (roas != null && roas.signum() > 0) {
        score = roas.max(new BigDecimal("0.10"));
      } else if (spend.signum() > 0 && conversions.signum() > 0) {
        score =
            conversions
                .divide(spend, 8, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
      } else {
        score = new BigDecimal("0.10");
      }
      BigDecimal evidence =
          conversions
              .divide(new BigDecimal("20"), 8, RoundingMode.HALF_UP)
              .min(BigDecimal.ONE)
              .max(new BigDecimal("0.25"));
      score = score.multiply(evidence);
      scores.put(row.channelId(), score);
      totalScore = totalScore.add(score);
    }

    Map<String, BigDecimal> recommended = new LinkedHashMap<>();
    int count = active.size();
    BigDecimal floorTotal = MIN_SHARE.multiply(BigDecimal.valueOf(count));
    BigDecimal distributable =
        new BigDecimal("100").subtract(floorTotal).max(ZERO);
    for (var row : active) {
      BigDecimal proportional =
          totalScore.signum() == 0
              ? BigDecimal.ONE.divide(BigDecimal.valueOf(count), 8, RoundingMode.HALF_UP)
              : scores.get(row.channelId()).divide(totalScore, 8, RoundingMode.HALF_UP);
      recommended.put(
          row.channelId(),
          MIN_SHARE.add(distributable.multiply(proportional))
              .setScale(2, RoundingMode.HALF_UP));
    }
    normalizeHundred(recommended);

    return response(
        analytics,
        budget,
        channelById,
        confidence,
        enoughConversions,
        enoughConversions
            ? "Trailing performance is used only as historical evidence. Each active channel keeps a 10% exploration floor; the remaining budget is weighted by ROAS or conversion efficiency and evidence volume. This is advisory and is not a forecast."
            : "There are fewer than 10 attributed conversions in the selected range. Recommendations are shown for exploration only and should not be applied automatically.",
        recommended);
  }

  private ReachlyoBudgetRecommendationResponse response(
      com.messaging.ads.campaign.dto.ReachlyoCampaignAnalyticsResponse analytics,
      BudgetPlan budget,
      Map<String, CampaignChannel> channelById,
      String confidence,
      boolean actionable,
      String methodology,
      Map<String, BigDecimal> recommended) {
    List<ChannelRecommendation> rows = new ArrayList<>();
    for (var deployment : analytics.deployments()) {
      CampaignChannel channel = channelById.get(deployment.channelId());
      BigDecimal planned =
          channel == null || channel.getAllocationPercent() == null
              ? deployment.spendSharePercent()
              : channel.getAllocationPercent();
      BigDecimal next =
          recommended == null
              ? planned
              : recommended.getOrDefault(deployment.channelId(), planned);
      Long minor =
          budget.getTotalBudgetMinor() == null
              ? null
              : BigDecimal.valueOf(budget.getTotalBudgetMinor())
                  .multiply(next)
                  .divide(new BigDecimal("100"), 0, RoundingMode.HALF_UP)
                  .longValue();
      String reason =
          deployment.summary().conversions().signum() == 0
              ? "No attributed conversions in this range; keep only an exploration allocation until more evidence is available."
              : deployment.summary().roas() != null
                  ? "Weighted from historical ROAS and conversion volume."
                  : "Weighted from historical conversions per unit of spend.";
      rows.add(
          new ChannelRecommendation(
              deployment.channelId(),
              deployment.provider(),
              deployment.channelType(),
              planned,
              deployment.spendSharePercent(),
              next,
              minor,
              deployment.summary().spend(),
              deployment.summary().conversions(),
              deployment.summary().cpa(),
              deployment.summary().roas(),
              reason));
    }
    return new ReachlyoBudgetRecommendationResponse(
        analytics.campaignId(),
        analytics.currency(),
        analytics.from(),
        analytics.to(),
        confidence,
        actionable,
        methodology,
        List.copyOf(rows));
  }

  private void normalizeHundred(Map<String, BigDecimal> shares) {
    if (shares.isEmpty()) return;
    BigDecimal sum = shares.values().stream().reduce(ZERO, BigDecimal::add);
    BigDecimal delta = new BigDecimal("100.00").subtract(sum);
    if (delta.signum() != 0) {
      String first = shares.keySet().iterator().next();
      shares.put(first, shares.get(first).add(delta));
    }
  }
}
