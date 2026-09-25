package com.messaging.ads.campaign.service;

import com.messaging.ads.campaign.dto.ReachlyoCampaignAnalyticsResponse;
import com.messaging.ads.campaign.dto.ReachlyoCampaignAnalyticsSyncResponse;
import com.messaging.ads.campaign.entity.CampaignChannel;
import com.messaging.ads.campaign.entity.ProviderDeployment;
import com.messaging.ads.campaign.entity.ReachlyoCampaign;
import com.messaging.ads.campaign.repository.CampaignChannelRepository;
import com.messaging.ads.campaign.repository.ProviderDeploymentRepository;
import com.messaging.ads.campaign.repository.ReachlyoCampaignRepository;
import com.messaging.ads.entity.AdInsightDaily;
import com.messaging.ads.enums.AdInsightGranularity;
import com.messaging.ads.enums.AdInsightLevel;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.GoogleAdsInsightSyncService;
import com.messaging.ads.meta.MetaAdsInsightSyncService;
import com.messaging.ads.repository.AdInsightDailyRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReachlyoCampaignAnalyticsService {

  private static final BigDecimal ZERO = BigDecimal.ZERO;

  private final ReachlyoCampaignRepository campaignRepository;
  private final ProviderDeploymentRepository deploymentRepository;
  private final CampaignChannelRepository channelRepository;
  private final AdInsightDailyRepository insightRepository;
  private final GoogleAdsInsightSyncService googleInsightSyncService;
  private final MetaAdsInsightSyncService metaInsightSyncService;

  @Transactional(readOnly = true)
  public ReachlyoCampaignAnalyticsResponse analytics(
      Long companyId, Long campaignId, LocalDate from, LocalDate to) {
    validateRange(from, to);
    ReachlyoCampaign campaign = requireCampaign(companyId, campaignId);
    List<ProviderDeployment> deployments =
        deploymentRepository.findByCompanyIdAndReachlyoCampaignIdOrderByIdAsc(
            companyId, campaignId);
    List<CampaignChannel> channels =
        channelRepository.findByCompanyIdAndReachlyoCampaignIdOrderByIdAsc(
            companyId, campaignId);
    Map<Long, CampaignChannel> channelById = new LinkedHashMap<>();
    channels.forEach(channel -> channelById.put(channel.getId(), channel));

    List<Long> localCampaignIds =
        deployments.stream()
            .map(ProviderDeployment::getLocalCampaignId)
            .filter(value -> value != null)
            .distinct()
            .toList();

    if (localCampaignIds.isEmpty()) {
      return empty(campaign, from, to, deployments, channelById);
    }

    List<AdInsightDaily> daily =
        insightRepository
            .findByCompanyIdAndCampaignIdInAndInsightLevelAndGranularityAndInsightDateBetweenOrderByInsightDateAsc(
                companyId,
                localCampaignIds,
                AdInsightLevel.CAMPAIGN,
                AdInsightGranularity.DAILY,
                from,
                to);
    List<AdInsightDaily> range =
        insightRepository
            .findByCompanyIdAndCampaignIdInAndInsightLevelAndGranularityAndInsightDateAndPeriodEnd(
                companyId,
                localCampaignIds,
                AdInsightLevel.CAMPAIGN,
                AdInsightGranularity.RANGE,
                from,
                to);

    validateCurrencies(campaign.getCurrency(), daily);
    validateCurrencies(campaign.getCurrency(), range);

    Map<Long, List<AdInsightDaily>> dailyByCampaign = groupByCampaign(daily);
    Map<Long, AdInsightDaily> exactByCampaign = new LinkedHashMap<>();
    for (AdInsightDaily row : range) {
      if (row.getCampaignId() != null) exactByCampaign.putIfAbsent(row.getCampaignId(), row);
    }

    List<ReachlyoCampaignAnalyticsResponse.DeploymentRow> deploymentRows =
        new ArrayList<>();
    boolean exactRangeAvailable = !deployments.isEmpty();
    for (ProviderDeployment deployment : deployments) {
      Long localCampaignId = deployment.getLocalCampaignId();
      AdInsightDaily exact =
          localCampaignId == null ? null : exactByCampaign.get(localCampaignId);
      List<AdInsightDaily> deploymentDaily =
          localCampaignId == null
              ? List.of()
              : dailyByCampaign.getOrDefault(localCampaignId, List.of());
      ReachlyoCampaignAnalyticsResponse.Summary summary =
          exact == null ? aggregate(deploymentDaily) : summary(exact);
      boolean deploymentExact = exact != null;
      if (localCampaignId != null && !deploymentExact) exactRangeAvailable = false;
      if (localCampaignId == null) exactRangeAvailable = false;

      CampaignChannel channel = channelById.get(deployment.getCampaignChannelId());
      deploymentRows.add(
          new ReachlyoCampaignAnalyticsResponse.DeploymentRow(
              deployment.getId().toString(),
              deployment.getCampaignChannelId().toString(),
              deployment.getProvider(),
              channel == null ? null : channel.getChannelType(),
              deployment.getAdAccountId().toString(),
              localCampaignId == null ? null : localCampaignId.toString(),
              deployment.getExternalCampaignId(),
              deployment.getProviderStatus(),
              deployment.getDeploymentStatus(),
              summary,
              ZERO,
              deploymentExact,
              latest(exact == null ? deploymentDaily : List.of(exact))));
    }

    ReachlyoCampaignAnalyticsResponse.Summary total =
        exactRangeAvailable ? aggregate(range) : aggregate(daily);
    BigDecimal totalSpend = total.spend();
    List<ReachlyoCampaignAnalyticsResponse.DeploymentRow> withShare =
        deploymentRows.stream()
            .map(
                row ->
                    new ReachlyoCampaignAnalyticsResponse.DeploymentRow(
                        row.deploymentId(),
                        row.channelId(),
                        row.provider(),
                        row.channelType(),
                        row.adAccountId(),
                        row.localCampaignId(),
                        row.externalCampaignId(),
                        row.providerStatus(),
                        row.deploymentStatus(),
                        row.summary(),
                        percentage(row.summary().spend(), totalSpend),
                        row.exactRangeAvailable(),
                        row.lastSyncedAt()))
            .sorted(
                Comparator.comparing(
                        (ReachlyoCampaignAnalyticsResponse.DeploymentRow row) ->
                            row.summary().spend())
                    .reversed())
            .toList();

    List<ReachlyoCampaignAnalyticsResponse.DailyPoint> trend =
        dailyTrend(daily);

    return new ReachlyoCampaignAnalyticsResponse(
        campaign.getId().toString(),
        campaign.getCurrency(),
        from,
        to,
        total,
        trend,
        withShare,
        exactRangeAvailable,
        latest(concat(daily, range)));
  }

  public ReachlyoCampaignAnalyticsSyncResponse sync(
      Long companyId, Long campaignId, LocalDate from, LocalDate to) {
    validateRange(from, to);
    requireCampaign(companyId, campaignId);
    List<ProviderDeployment> deployments =
        deploymentRepository.findByCompanyIdAndReachlyoCampaignIdOrderByIdAsc(
            companyId, campaignId);

    Map<AccountKey, ProviderDeployment> accounts = new LinkedHashMap<>();
    for (ProviderDeployment deployment : deployments) {
      if (deployment.getLocalCampaignId() == null) continue;
      accounts.putIfAbsent(
          new AccountKey(deployment.getProvider(), deployment.getAdAccountId()),
          deployment);
    }

    int synchronizedAccounts = 0;
    List<ReachlyoCampaignAnalyticsSyncResponse.SyncFailure> failures =
        new ArrayList<>();
    for (AccountKey account : accounts.keySet()) {
      try {
        if (account.provider() == AdProvider.GOOGLE_ADS) {
          googleInsightSyncService.sync(companyId, account.accountId(), from, to);
        } else if (account.provider() == AdProvider.META_ADS) {
          metaInsightSyncService.sync(companyId, account.accountId(), from, to);
        }
        synchronizedAccounts++;
      } catch (RuntimeException exception) {
        failures.add(
            new ReachlyoCampaignAnalyticsSyncResponse.SyncFailure(
                account.provider(),
                account.accountId().toString(),
                safeMessage(exception)));
      }
    }

    return new ReachlyoCampaignAnalyticsSyncResponse(
        synchronizedAccounts,
        List.copyOf(failures),
        analytics(companyId, campaignId, from, to));
  }

  private ReachlyoCampaignAnalyticsResponse empty(
      ReachlyoCampaign campaign,
      LocalDate from,
      LocalDate to,
      List<ProviderDeployment> deployments,
      Map<Long, CampaignChannel> channelById) {
    List<ReachlyoCampaignAnalyticsResponse.DeploymentRow> rows =
        deployments.stream()
            .map(
                deployment -> {
                  CampaignChannel channel = channelById.get(deployment.getCampaignChannelId());
                  return new ReachlyoCampaignAnalyticsResponse.DeploymentRow(
                      deployment.getId().toString(),
                      deployment.getCampaignChannelId().toString(),
                      deployment.getProvider(),
                      channel == null ? null : channel.getChannelType(),
                      deployment.getAdAccountId().toString(),
                      null,
                      deployment.getExternalCampaignId(),
                      deployment.getProviderStatus(),
                      deployment.getDeploymentStatus(),
                      zeroSummary(),
                      ZERO,
                      false,
                      null);
                })
            .toList();
    return new ReachlyoCampaignAnalyticsResponse(
        campaign.getId().toString(),
        campaign.getCurrency(),
        from,
        to,
        zeroSummary(),
        List.of(),
        rows,
        false,
        null);
  }

  private ReachlyoCampaign requireCampaign(Long companyId, Long campaignId) {
    return campaignRepository
        .findByIdAndCompanyId(campaignId, companyId)
        .orElseThrow(() -> new NotFoundException("Reachlyo campaign not found"));
  }

  private void validateRange(LocalDate from, LocalDate to) {
    if (from == null || to == null || to.isBefore(from)) {
      throw new BadRequestException("A valid campaign analytics date range is required");
    }
  }

  private void validateCurrencies(String expected, List<AdInsightDaily> rows) {
    for (AdInsightDaily row : rows) {
      String currency = row.getCurrency();
      if (currency != null
          && !currency.isBlank()
          && expected != null
          && !expected.equalsIgnoreCase(currency)) {
        throw new BadRequestException(
            "Cross-provider analytics cannot aggregate mixed currencies");
      }
    }
  }

  private Map<Long, List<AdInsightDaily>> groupByCampaign(
      List<AdInsightDaily> rows) {
    Map<Long, List<AdInsightDaily>> grouped = new LinkedHashMap<>();
    for (AdInsightDaily row : rows) {
      if (row.getCampaignId() == null) continue;
      grouped.computeIfAbsent(row.getCampaignId(), ignored -> new ArrayList<>()).add(row);
    }
    return grouped;
  }

  private ReachlyoCampaignAnalyticsResponse.Summary aggregate(
      List<AdInsightDaily> rows) {
    BigDecimal spend = ZERO;
    long impressions = 0L;
    long clicks = 0L;
    BigDecimal conversions = ZERO;
    BigDecimal conversionValue = ZERO;
    BigDecimal leads = ZERO;
    BigDecimal purchases = ZERO;
    for (AdInsightDaily row : rows) {
      spend = spend.add(nz(row.getSpend()));
      impressions += row.getImpressions();
      clicks += row.getClicks();
      conversions = conversions.add(nz(row.getConversions()));
      conversionValue = conversionValue.add(nz(row.getConversionValue()));
      leads = leads.add(nz(row.getLeads()));
      purchases = purchases.add(nz(row.getPurchases()));
    }
    return calculated(
        spend, impressions, clicks, conversions, conversionValue, leads, purchases);
  }

  private ReachlyoCampaignAnalyticsResponse.Summary summary(
      AdInsightDaily row) {
    return calculated(
        nz(row.getSpend()),
        row.getImpressions(),
        row.getClicks(),
        nz(row.getConversions()),
        nz(row.getConversionValue()),
        nz(row.getLeads()),
        nz(row.getPurchases()));
  }

  private ReachlyoCampaignAnalyticsResponse.Summary calculated(
      BigDecimal spend,
      long impressions,
      long clicks,
      BigDecimal conversions,
      BigDecimal conversionValue,
      BigDecimal leads,
      BigDecimal purchases) {
    BigDecimal ctr =
        impressions == 0
            ? null
            : BigDecimal.valueOf(clicks)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(impressions), 8, RoundingMode.HALF_UP);
    BigDecimal cpa = divide(spend, conversions);
    BigDecimal roas = divide(conversionValue, spend);
    return new ReachlyoCampaignAnalyticsResponse.Summary(
        spend,
        impressions,
        clicks,
        ctr,
        conversions,
        conversionValue,
        cpa,
        roas,
        leads,
        purchases);
  }

  private List<ReachlyoCampaignAnalyticsResponse.DailyPoint> dailyTrend(
      List<AdInsightDaily> rows) {
    Map<LocalDate, List<AdInsightDaily>> byDate = new LinkedHashMap<>();
    for (AdInsightDaily row : rows) {
      byDate.computeIfAbsent(row.getInsightDate(), ignored -> new ArrayList<>()).add(row);
    }
    return byDate.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(
            entry -> {
              var summary = aggregate(entry.getValue());
              return new ReachlyoCampaignAnalyticsResponse.DailyPoint(
                  entry.getKey(),
                  summary.spend(),
                  summary.impressions(),
                  summary.clicks(),
                  summary.conversions(),
                  summary.conversionValue());
            })
        .toList();
  }

  private BigDecimal percentage(BigDecimal value, BigDecimal total) {
    if (total == null || total.signum() == 0) return ZERO;
    return nz(value)
        .multiply(BigDecimal.valueOf(100))
        .divide(total, 4, RoundingMode.HALF_UP);
  }

  private BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
    if (numerator == null || denominator == null || denominator.signum() == 0) return null;
    return numerator.divide(denominator, 8, RoundingMode.HALF_UP);
  }

  private BigDecimal nz(BigDecimal value) {
    return value == null ? ZERO : value;
  }

  private Instant latest(List<AdInsightDaily> rows) {
    return rows.stream()
        .map(AdInsightDaily::getLastSyncAt)
        .filter(value -> value != null)
        .max(Instant::compareTo)
        .orElse(null);
  }

  private List<AdInsightDaily> concat(
      List<AdInsightDaily> first, List<AdInsightDaily> second) {
    List<AdInsightDaily> result = new ArrayList<>(first.size() + second.size());
    result.addAll(first);
    result.addAll(second);
    return result;
  }

  private String safeMessage(RuntimeException exception) {
    String message = exception.getMessage();
    if (message == null || message.isBlank()) return "Provider analytics sync failed";
    return message.length() <= 500 ? message : message.substring(0, 500);
  }

  private ReachlyoCampaignAnalyticsResponse.Summary zeroSummary() {
    return calculated(ZERO, 0L, 0L, ZERO, ZERO, ZERO, ZERO);
  }

  private record AccountKey(AdProvider provider, Long accountId) {}
}
