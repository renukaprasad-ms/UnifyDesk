package com.messaging.crm;

import com.messaging.common.exception.ApiException;
import com.messaging.common.id.IdGenerator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CrmForecastService {

  private final JdbcTemplate jdbc;

  public record ForecastSummary(
      int totalOpenDeals,
      BigDecimal unweightedPipeline,
      BigDecimal weightedPipeline,
      int wonDealsCount,
      BigDecimal wonAmount,
      int lostDealsCount,
      BigDecimal lostAmount,
      BigDecimal totalForecast,
      BigDecimal targetQuota,
      double quotaAttainmentPct,
      double projectedAttainmentPct,
      double winRatePct,
      String currency) {}

  public record StageForecast(
      String stageKey,
      String stageName,
      String category,
      int probability,
      String color,
      int dealsCount,
      BigDecimal totalAmount,
      BigDecimal weightedAmount) {}

  public record TimelineCohort(
      String periodKey,
      String periodLabel,
      int openDealsCount,
      BigDecimal unweightedAmount,
      BigDecimal weightedAmount,
      int wonDealsCount,
      BigDecimal wonAmount) {}

  public record RepForecast(
      String userId,
      String userName,
      int openDealsCount,
      BigDecimal unweightedAmount,
      BigDecimal weightedAmount,
      int wonDealsCount,
      BigDecimal wonAmount,
      BigDecimal targetQuota,
      double quotaAttainmentPct,
      double projectedAttainmentPct) {}

  public record ForecastDeal(
      String id,
      String title,
      String stage,
      String stageName,
      String category,
      int probability,
      BigDecimal amount,
      BigDecimal weightedAmount,
      String currency,
      String expectedCloseDate,
      String ownerUserId,
      String ownerName,
      String accountName,
      String contactName) {}

  public record ForecastResponse(
      String period,
      String periodLabel,
      String startDate,
      String endDate,
      Long pipelineId,
      String pipelineName,
      ForecastSummary summary,
      List<StageForecast> stages,
      List<TimelineCohort> timeline,
      List<RepForecast> reps,
      List<ForecastDeal> deals,
      Instant generatedAt) {}

  public record SalesQuotaDto(
      String id,
      String userId,
      String userName,
      String periodType,
      String periodKey,
      BigDecimal targetAmount,
      String currency,
      Instant updatedAt) {}

  public record SetQuotaInput(
      Long userId, String periodType, String periodKey, BigDecimal targetAmount, String currency) {}

  private record StageMeta(
      String key, String name, String category, int probability, String color, int displayOrder) {}

  @Transactional(readOnly = true)
  public ForecastResponse getForecast(
      long companyId, String period, Long pipelineId, Long ownerUserId) {

    LocalDate today = LocalDate.now();
    LocalDate startDate = null;
    LocalDate endDate = null;
    String periodLabel = "All Open Opportunities";

    String p = period != null ? period.toLowerCase(Locale.ROOT).trim() : "this_quarter";

    switch (p) {
      case "this_month" -> {
        startDate = today.withDayOfMonth(1);
        endDate = today.with(TemporalAdjusters.lastDayOfMonth());
        periodLabel = today.format(DateTimeFormatter.ofPattern("MMMM yyyy"));
      }
      case "next_month" -> {
        LocalDate nextMonth = today.plusMonths(1);
        startDate = nextMonth.withDayOfMonth(1);
        endDate = nextMonth.with(TemporalAdjusters.lastDayOfMonth());
        periodLabel = nextMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy"));
      }
      case "this_quarter" -> {
        int quarter = today.get(IsoFields.QUARTER_OF_YEAR);
        int firstMonth = (quarter - 1) * 3 + 1;
        startDate = LocalDate.of(today.getYear(), firstMonth, 1);
        endDate = startDate.plusMonths(2).with(TemporalAdjusters.lastDayOfMonth());
        periodLabel = "Q" + quarter + " " + today.getYear();
      }
      case "next_quarter" -> {
        LocalDate later = today.plusMonths(3);
        int quarter = later.get(IsoFields.QUARTER_OF_YEAR);
        int firstMonth = (quarter - 1) * 3 + 1;
        startDate = LocalDate.of(later.getYear(), firstMonth, 1);
        endDate = startDate.plusMonths(2).with(TemporalAdjusters.lastDayOfMonth());
        periodLabel = "Q" + quarter + " " + later.getYear();
      }
      case "this_year" -> {
        startDate = LocalDate.of(today.getYear(), 1, 1);
        endDate = LocalDate.of(today.getYear(), 12, 31);
        periodLabel = "FY " + today.getYear();
      }
      case "all_open" -> {
        startDate = null;
        endDate = null;
        periodLabel = "All Open Pipeline";
      }
      default -> {
        // Default to this quarter
        int quarter = today.get(IsoFields.QUARTER_OF_YEAR);
        int firstMonth = (quarter - 1) * 3 + 1;
        startDate = LocalDate.of(today.getYear(), firstMonth, 1);
        endDate = startDate.plusMonths(2).with(TemporalAdjusters.lastDayOfMonth());
        periodLabel = "Q" + quarter + " " + today.getYear();
      }
    }

    // Resolve pipeline
    String pipelineName = "All Pipelines";
    if (pipelineId != null) {
      List<String> pNames =
          jdbc.query(
              "select name from crm_pipelines where company_id = ? and id = ?",
              (rs, row) -> rs.getString("name"),
              companyId,
              pipelineId);
      if (!pNames.isEmpty()) {
        pipelineName = pNames.getFirst();
      }
    }

    // Load stages for stage probabilities
    Map<String, StageMeta> stageMap = loadStageMap(companyId, pipelineId);

    // Query deals
    StringBuilder sql =
        new StringBuilder(
            """
        select d.id, d.title, d.stage, d.amount, d.currency, d.expected_close_date,
               d.owner_user_id, u.name as owner_name,
               a.name as account_name, c.display_name as contact_name
        from crm_deals d
        left join users u on u.id = d.owner_user_id
        left join crm_accounts a on a.company_id = d.company_id and a.id = d.account_id
        left join contacts c on c.company_id = d.company_id and c.id = d.contact_id
        where d.company_id = ?
        """);

    List<Object> params = new ArrayList<>();
    params.add(companyId);

    if (pipelineId != null) {
      sql.append(" and d.pipeline_id = ? ");
      params.add(pipelineId);
    }

    if (ownerUserId != null) {
      sql.append(" and d.owner_user_id = ? ");
      params.add(ownerUserId);
    }

    // Date filtering:
    // For OPEN deals: filter by expected_close_date (if dates specified) or include if
    // expected_close_date is null/future
    // For WON/LOST deals: filter by expected_close_date or updated_at within range
    if (startDate != null && endDate != null) {
      sql.append(
          """
          and (
            (d.stage not in ('WON', 'LOST') and (d.expected_close_date between ? and ? or d.expected_close_date is null))
            or
            (d.stage in ('WON', 'LOST') and (d.expected_close_date between ? and ? or (d.closed_at between ? and ?)))
          )
          """);
      params.add(java.sql.Date.valueOf(startDate));
      params.add(java.sql.Date.valueOf(endDate));
      params.add(java.sql.Date.valueOf(startDate));
      params.add(java.sql.Date.valueOf(endDate));
      params.add(Timestamp.valueOf(startDate.atStartOfDay()));
      params.add(Timestamp.valueOf(endDate.atTime(23, 59, 59)));
    }

    sql.append(" order by coalesce(d.expected_close_date, '2099-12-31') asc, d.amount desc ");

    List<ForecastDeal> dealList = new ArrayList<>();
    int totalOpenDeals = 0;
    BigDecimal unweightedPipeline = BigDecimal.ZERO;
    BigDecimal weightedPipeline = BigDecimal.ZERO;
    int wonDealsCount = 0;
    BigDecimal wonAmount = BigDecimal.ZERO;
    int lostDealsCount = 0;
    BigDecimal lostAmount = BigDecimal.ZERO;
    String currency = "INR";

    // Stage aggregators
    Map<String, StageAggregator> stageAggs = new LinkedHashMap<>();
    for (StageMeta sm : stageMap.values()) {
      stageAggs.put(sm.key(), new StageAggregator(sm));
    }

    // Timeline aggregators (by month)
    Map<String, TimelineAggregator> timelineAggs = new TreeMap<>();

    // Rep aggregators
    Map<Long, RepAggregator> repAggs = new HashMap<>();

    List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), params.toArray());

    for (Map<String, Object> r : rows) {
      long id = ((Number) r.get("id")).longValue();
      String title = (String) r.get("title");
      String stage = (String) r.get("stage");
      BigDecimal amount = r.get("amount") != null ? (BigDecimal) r.get("amount") : BigDecimal.ZERO;
      String dealCurrency = (String) r.get("currency");
      if (dealCurrency != null && !dealCurrency.isBlank()) currency = dealCurrency;

      Object closeDateObj = r.get("expected_close_date");
      String expectedCloseDate = closeDateObj != null ? closeDateObj.toString() : null;

      Long repId =
          r.get("owner_user_id") != null ? ((Number) r.get("owner_user_id")).longValue() : null;
      String repName = (String) r.get("owner_name");
      String accountName = (String) r.get("account_name");
      String contactName = (String) r.get("contact_name");

      StageMeta meta = stageMap.get(stage);
      String stageName = meta != null ? meta.name() : stage;
      String category =
          meta != null
              ? meta.category()
              : ("WON".equals(stage) ? "WON" : "LOST".equals(stage) ? "LOST" : "OPEN");
      int probability = meta != null ? meta.probability() : defaultProbability(stage, category);

      BigDecimal weighted;
      if ("WON".equalsIgnoreCase(category)) {
        weighted = amount;
        wonDealsCount++;
        wonAmount = wonAmount.add(amount);
      } else if ("LOST".equalsIgnoreCase(category)) {
        weighted = BigDecimal.ZERO;
        lostDealsCount++;
        lostAmount = lostAmount.add(amount);
      } else {
        weighted =
            amount
                .multiply(BigDecimal.valueOf(probability))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        totalOpenDeals++;
        unweightedPipeline = unweightedPipeline.add(amount);
        weightedPipeline = weightedPipeline.add(weighted);
      }

      dealList.add(
          new ForecastDeal(
              String.valueOf(id),
              title,
              stage,
              stageName,
              category,
              probability,
              amount,
              weighted,
              dealCurrency,
              expectedCloseDate,
              repId != null ? String.valueOf(repId) : null,
              repName != null ? repName : "Unassigned",
              accountName,
              contactName));

      // Stage aggregation
      StageAggregator sa =
          stageAggs.computeIfAbsent(
              stage,
              k ->
                  new StageAggregator(
                      new StageMeta(stage, stageName, category, probability, "slate", 99)));
      sa.dealsCount++;
      sa.totalAmount = sa.totalAmount.add(amount);
      sa.weightedAmount = sa.weightedAmount.add(weighted);

      // Timeline aggregation (bucket by YYYY-MM)
      String monthKey =
          expectedCloseDate != null && expectedCloseDate.length() >= 7
              ? expectedCloseDate.substring(0, 7)
              : "No Date";
      TimelineAggregator ta =
          timelineAggs.computeIfAbsent(monthKey, k -> new TimelineAggregator(k));
      if ("WON".equalsIgnoreCase(category)) {
        ta.wonDealsCount++;
        ta.wonAmount = ta.wonAmount.add(amount);
      } else if (!"LOST".equalsIgnoreCase(category)) {
        ta.openDealsCount++;
        ta.unweightedAmount = ta.unweightedAmount.add(amount);
        ta.weightedAmount = ta.weightedAmount.add(weighted);
      }

      // Rep aggregation
      long rKey = repId != null ? repId : 0L;
      RepAggregator ra =
          repAggs.computeIfAbsent(
              rKey, k -> new RepAggregator(k, repName != null ? repName : "Unassigned"));
      if ("WON".equalsIgnoreCase(category)) {
        ra.wonDealsCount++;
        ra.wonAmount = ra.wonAmount.add(amount);
      } else if (!"LOST".equalsIgnoreCase(category)) {
        ra.openDealsCount++;
        ra.unweightedAmount = ra.unweightedAmount.add(amount);
        ra.weightedAmount = ra.weightedAmount.add(weighted);
      }
    }

    BigDecimal totalForecast = wonAmount.add(weightedPipeline);
    int totalClosed = wonDealsCount + lostDealsCount;
    double winRatePct = totalClosed > 0 ? (wonDealsCount * 100.0 / totalClosed) : 0.0;

    // Quotas matching for the period
    BigDecimal targetQuota = BigDecimal.ZERO;
    String quotaPeriodType = p.contains("quarter") ? "QUARTERLY" : "MONTHLY";
    String quotaPeriodKey =
        p.contains("quarter")
            ? today.getYear() + "-Q" + today.get(IsoFields.QUARTER_OF_YEAR)
            : today.getYear() + "-" + String.format("%02d", today.getMonthValue());

    List<SalesQuotaDto> quotas = getQuotas(companyId, quotaPeriodType, quotaPeriodKey);
    Map<Long, BigDecimal> repQuotaMap = new HashMap<>();
    for (SalesQuotaDto q : quotas) {
      if (ownerUserId == null || String.valueOf(ownerUserId).equals(q.userId())) {
        targetQuota = targetQuota.add(q.targetAmount());
      }
      try {
        repQuotaMap.put(Long.parseLong(q.userId()), q.targetAmount());
      } catch (Exception ignored) {
      }
    }

    double quotaAttainmentPct =
        targetQuota.compareTo(BigDecimal.ZERO) > 0
            ? wonAmount
                .multiply(BigDecimal.valueOf(100))
                .divide(targetQuota, 2, RoundingMode.HALF_UP)
                .doubleValue()
            : 0.0;

    double projectedAttainmentPct =
        targetQuota.compareTo(BigDecimal.ZERO) > 0
            ? totalForecast
                .multiply(BigDecimal.valueOf(100))
                .divide(targetQuota, 2, RoundingMode.HALF_UP)
                .doubleValue()
            : 0.0;

    ForecastSummary summary =
        new ForecastSummary(
            totalOpenDeals,
            unweightedPipeline,
            weightedPipeline,
            wonDealsCount,
            wonAmount,
            lostDealsCount,
            lostAmount,
            totalForecast,
            targetQuota,
            round1(quotaAttainmentPct),
            round1(projectedAttainmentPct),
            round1(winRatePct),
            currency);

    // Format stages
    List<StageForecast> stageList = new ArrayList<>();
    for (StageAggregator sa : stageAggs.values()) {
      stageList.add(
          new StageForecast(
              sa.meta.key(),
              sa.meta.name(),
              sa.meta.category(),
              sa.meta.probability(),
              sa.meta.color(),
              sa.dealsCount,
              sa.totalAmount,
              sa.weightedAmount));
    }

    // Format timeline
    List<TimelineCohort> timelineList = new ArrayList<>();
    for (TimelineAggregator ta : timelineAggs.values()) {
      String label = ta.monthKey;
      if (!"No Date".equals(ta.monthKey) && ta.monthKey.length() == 7) {
        try {
          YearMonth ym = YearMonth.parse(ta.monthKey);
          label = ym.format(DateTimeFormatter.ofPattern("MMM yyyy"));
        } catch (Exception ignored) {
        }
      }
      timelineList.add(
          new TimelineCohort(
              ta.monthKey,
              label,
              ta.openDealsCount,
              ta.unweightedAmount,
              ta.weightedAmount,
              ta.wonDealsCount,
              ta.wonAmount));
    }

    // Format reps
    List<RepForecast> repList = new ArrayList<>();
    for (RepAggregator ra : repAggs.values()) {
      BigDecimal repTarget = repQuotaMap.getOrDefault(ra.userId, BigDecimal.ZERO);
      double repAttain =
          repTarget.compareTo(BigDecimal.ZERO) > 0
              ? ra.wonAmount
                  .multiply(BigDecimal.valueOf(100))
                  .divide(repTarget, 2, RoundingMode.HALF_UP)
                  .doubleValue()
              : 0.0;
      BigDecimal repTotal = ra.wonAmount.add(ra.weightedAmount);
      double repProjected =
          repTarget.compareTo(BigDecimal.ZERO) > 0
              ? repTotal
                  .multiply(BigDecimal.valueOf(100))
                  .divide(repTarget, 2, RoundingMode.HALF_UP)
                  .doubleValue()
              : 0.0;

      repList.add(
          new RepForecast(
              String.valueOf(ra.userId),
              ra.userName,
              ra.openDealsCount,
              ra.unweightedAmount,
              ra.weightedAmount,
              ra.wonDealsCount,
              ra.wonAmount,
              repTarget,
              round1(repAttain),
              round1(repProjected)));
    }
    repList.sort((a, b) -> b.weightedAmount().compareTo(a.weightedAmount()));

    return new ForecastResponse(
        p,
        periodLabel,
        startDate != null ? startDate.toString() : null,
        endDate != null ? endDate.toString() : null,
        pipelineId,
        pipelineName,
        summary,
        stageList,
        timelineList,
        repList,
        dealList,
        Instant.now());
  }

  // ── Quota Management ──────────────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public List<SalesQuotaDto> getQuotas(long companyId, String periodType, String periodKey) {
    String sql =
        """
        select q.id, q.user_id, u.name as user_name, q.period_type, q.period_key, q.target_amount, q.currency, q.updated_at
        from crm_sales_quotas q
        join users u on u.id = q.user_id
        where q.company_id = ?
        """;
    List<Object> params = new ArrayList<>();
    params.add(companyId);

    if (periodType != null && !periodType.isBlank()) {
      sql += " and q.period_type = ? ";
      params.add(periodType.toUpperCase(Locale.ROOT));
    }
    if (periodKey != null && !periodKey.isBlank()) {
      sql += " and q.period_key = ? ";
      params.add(periodKey.trim());
    }
    sql += " order by u.name asc ";

    return jdbc.query(
        sql,
        (rs, row) ->
            new SalesQuotaDto(
                String.valueOf(rs.getLong("id")),
                String.valueOf(rs.getLong("user_id")),
                rs.getString("user_name"),
                rs.getString("period_type"),
                rs.getString("period_key"),
                rs.getBigDecimal("target_amount"),
                rs.getString("currency"),
                rs.getTimestamp("updated_at").toInstant()),
        params.toArray());
  }

  @Transactional
  public SalesQuotaDto setQuota(long companyId, SetQuotaInput input, long actorUserId) {
    if (input.userId() == null) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "USER_REQUIRED");
    }
    if (input.periodType() == null
        || !List.of("MONTHLY", "QUARTERLY").contains(input.periodType().toUpperCase(Locale.ROOT))) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PERIOD_TYPE");
    }
    if (input.periodKey() == null || input.periodKey().isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "PERIOD_KEY_REQUIRED");
    }
    BigDecimal target =
        input.targetAmount() != null && input.targetAmount().compareTo(BigDecimal.ZERO) >= 0
            ? input.targetAmount()
            : BigDecimal.ZERO;
    String currency =
        input.currency() != null && !input.currency().isBlank()
            ? input.currency().toUpperCase(Locale.ROOT)
            : "INR";
    String pType = input.periodType().toUpperCase(Locale.ROOT);
    String pKey = input.periodKey().trim();

    long quotaId = IdGenerator.nextId();
    Timestamp now = Timestamp.from(Instant.now());

    jdbc.update(
        """
        insert into crm_sales_quotas
          (id, company_id, user_id, period_type, period_key, target_amount, currency, created_at, updated_at)
        values (?, ?, ?, ?, ?, ?, ?, ?, ?)
        on conflict (company_id, user_id, period_type, period_key, currency)
        do update set target_amount = excluded.target_amount, updated_at = excluded.updated_at
        """,
        quotaId,
        companyId,
        input.userId(),
        pType,
        pKey,
        target,
        currency,
        now,
        now);

    List<SalesQuotaDto> updated = getQuotas(companyId, pType, pKey);
    return updated.stream()
        .filter(q -> String.valueOf(input.userId()).equals(q.userId()))
        .findFirst()
        .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "QUOTA_SAVE_FAILED"));
  }

  // ── Helpers ───────────────────────────────────────────────────────────────────

  private Map<String, StageMeta> loadStageMap(long companyId, Long pipelineId) {
    Map<String, StageMeta> map = new LinkedHashMap<>();

    String sql =
        """
        select s.key, s.name, s.category, s.probability, s.color, s.display_order
        from crm_pipeline_stages s
        where s.company_id = ?
        """;
    List<Object> params = new ArrayList<>();
    params.add(companyId);

    if (pipelineId != null) {
      sql += " and s.pipeline_id = ? ";
      params.add(pipelineId);
    } else {
      sql +=
          " and s.pipeline_id = (select id from crm_pipelines where company_id = ? and is_default = true limit 1) ";
      params.add(companyId);
    }
    sql += " order by s.display_order asc ";

    List<StageMeta> list =
        jdbc.query(
            sql,
            (rs, row) ->
                new StageMeta(
                    rs.getString("key"),
                    rs.getString("name"),
                    rs.getString("category"),
                    rs.getInt("probability"),
                    rs.getString("color"),
                    rs.getInt("display_order")),
            params.toArray());

    for (StageMeta sm : list) {
      map.put(sm.key(), sm);
    }

    // Always ensure standard fallbacks exist
    map.putIfAbsent("NEW", new StageMeta("NEW", "New", "OPEN", 10, "slate", 1));
    map.putIfAbsent("QUALIFIED", new StageMeta("QUALIFIED", "Qualified", "OPEN", 25, "blue", 2));
    map.putIfAbsent("PROPOSAL", new StageMeta("PROPOSAL", "Proposal", "OPEN", 50, "indigo", 3));
    map.putIfAbsent(
        "NEGOTIATION", new StageMeta("NEGOTIATION", "Negotiation", "OPEN", 80, "amber", 4));
    map.putIfAbsent("WON", new StageMeta("WON", "Won", "WON", 100, "emerald", 5));
    map.putIfAbsent("LOST", new StageMeta("LOST", "Lost", "LOST", 0, "rose", 6));

    return map;
  }

  private static int defaultProbability(String stage, String category) {
    if ("WON".equalsIgnoreCase(category)) return 100;
    if ("LOST".equalsIgnoreCase(category)) return 0;
    return switch (stage != null ? stage.toUpperCase(Locale.ROOT) : "") {
      case "NEW" -> 10;
      case "QUALIFIED" -> 25;
      case "PROPOSAL" -> 50;
      case "NEGOTIATION" -> 80;
      case "WON" -> 100;
      case "LOST" -> 0;
      default -> 20;
    };
  }

  private static double round1(double v) {
    return Math.round(v * 10.0) / 10.0;
  }

  private static class StageAggregator {
    final StageMeta meta;
    int dealsCount = 0;
    BigDecimal totalAmount = BigDecimal.ZERO;
    BigDecimal weightedAmount = BigDecimal.ZERO;

    StageAggregator(StageMeta meta) {
      this.meta = meta;
    }
  }

  private static class TimelineAggregator {
    final String monthKey;
    int openDealsCount = 0;
    BigDecimal unweightedAmount = BigDecimal.ZERO;
    BigDecimal weightedAmount = BigDecimal.ZERO;
    int wonDealsCount = 0;
    BigDecimal wonAmount = BigDecimal.ZERO;

    TimelineAggregator(String monthKey) {
      this.monthKey = monthKey;
    }
  }

  private static class RepAggregator {
    final long userId;
    final String userName;
    int openDealsCount = 0;
    BigDecimal unweightedAmount = BigDecimal.ZERO;
    BigDecimal weightedAmount = BigDecimal.ZERO;
    int wonDealsCount = 0;
    BigDecimal wonAmount = BigDecimal.ZERO;

    RepAggregator(long userId, String userName) {
      this.userId = userId;
      this.userName = userName;
    }
  }
}
