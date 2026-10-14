package com.messaging.crm;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CrmAttributionService {

  private final JdbcTemplate jdbc;

  public record ChannelAttribution(
      String channel,
      String channelLabel,
      int dealsCount,
      BigDecimal openAmount,
      int wonDealsCount,
      BigDecimal wonAmount,
      int lostDealsCount,
      double winRatePct) {}

  public record CampaignRoi(
      String id,
      String title,
      String campaignType, // 'BROADCAST', 'AD_CAMPAIGN', 'WEB_FORM'
      String subtype,      // 'WHATSAPP', 'EMAIL', 'META', 'GOOGLE'
      int dealsCount,
      BigDecimal openAmount,
      int wonDealsCount,
      BigDecimal wonAmount) {}

  public record AttributionReport(
      String period,
      String periodLabel,
      BigDecimal totalWonRevenue,
      BigDecimal marketingAttributedWon,
      double marketingContributionPct,
      int totalAttributedDeals,
      List<ChannelAttribution> channels,
      List<CampaignRoi> topBroadcasts,
      List<CampaignRoi> topAdCampaigns,
      List<CampaignRoi> topLeadForms,
      Instant generatedAt) {}

  public record BroadcastOption(String id, String subject, String channelType) {}
  public record AdCampaignOption(String id, String name, String provider) {}
  public record LeadFormOption(String id, String title, String formKey) {}

  public record AttributionOptions(
      List<BroadcastOption> broadcasts,
      List<AdCampaignOption> adCampaigns,
      List<LeadFormOption> leadForms,
      List<String> standardChannels) {}

  @Transactional(readOnly = true)
  public AttributionOptions getOptions(long companyId) {
    // 1. Broadcasts
    List<BroadcastOption> broadcasts =
        jdbc.query(
            """
            select id, coalesce(nullif(trim(subject), ''), 'Broadcast #' || id) as title, channel_type
            from broadcast_messages
            where company_id = ?
            order by created_at desc limit 100
            """,
            (rs, row) ->
                new BroadcastOption(
                    String.valueOf(rs.getLong("id")),
                    rs.getString("title"),
                    rs.getString("channel_type")),
            companyId);

    // 2. Ad Campaigns
    List<AdCampaignOption> ads =
        jdbc.query(
            """
            select id, name, provider
            from ad_campaigns
            where company_id = ? and accessible = true
            order by created_at desc limit 100
            """,
            (rs, row) ->
                new AdCampaignOption(
                    String.valueOf(rs.getLong("id")),
                    rs.getString("name"),
                    rs.getString("provider")),
            companyId);

    // 3. Lead Forms
    List<LeadFormOption> forms =
        jdbc.query(
            """
            select id, title, form_key
            from crm_lead_forms
            where company_id = ?
            order by created_at desc limit 100
            """,
            (rs, row) ->
                new LeadFormOption(
                    String.valueOf(rs.getLong("id")),
                    rs.getString("title"),
                    rs.getString("form_key")),
            companyId);

    List<String> channels =
        List.of(
            "DIRECT_SALES",
            "BROADCAST",
            "META_ADS",
            "GOOGLE_ADS",
            "WEB_FORM",
            "REFERRAL",
            "ORGANIC",
            "EVENT_WEBINAR",
            "PARTNER");

    return new AttributionOptions(broadcasts, ads, forms, channels);
  }

  @Transactional(readOnly = true)
  public AttributionReport getReport(long companyId, String period) {
    LocalDate today = LocalDate.now();
    LocalDate startDate = null;
    LocalDate endDate = null;
    String periodLabel = "All Time";

    String p = period != null ? period.toLowerCase(Locale.ROOT).trim() : "this_quarter";

    switch (p) {
      case "this_month" -> {
        startDate = today.withDayOfMonth(1);
        endDate = today.with(TemporalAdjusters.lastDayOfMonth());
        periodLabel = today.getMonth().name() + " " + today.getYear();
      }
      case "this_quarter" -> {
        int quarter = today.get(IsoFields.QUARTER_OF_YEAR);
        int firstMonth = (quarter - 1) * 3 + 1;
        startDate = LocalDate.of(today.getYear(), firstMonth, 1);
        endDate = startDate.plusMonths(2).with(TemporalAdjusters.lastDayOfMonth());
        periodLabel = "Q" + quarter + " " + today.getYear();
      }
      case "this_year" -> {
        startDate = LocalDate.of(today.getYear(), 1, 1);
        endDate = LocalDate.of(today.getYear(), 12, 31);
        periodLabel = "FY " + today.getYear();
      }
      case "all_time" -> {
        startDate = null;
        endDate = null;
        periodLabel = "All Time";
      }
      default -> {
        int quarter = today.get(IsoFields.QUARTER_OF_YEAR);
        int firstMonth = (quarter - 1) * 3 + 1;
        startDate = LocalDate.of(today.getYear(), firstMonth, 1);
        endDate = startDate.plusMonths(2).with(TemporalAdjusters.lastDayOfMonth());
        periodLabel = "Q" + quarter + " " + today.getYear();
      }
    }

    StringBuilder dateFilter = new StringBuilder();
    List<Object> dateParams = new ArrayList<>();
    if (startDate != null && endDate != null) {
      dateFilter.append(" and (d.created_at between ? and ? or d.closed_at between ? and ?) ");
      dateParams.add(Timestamp.valueOf(startDate.atStartOfDay()));
      dateParams.add(Timestamp.valueOf(endDate.atTime(23, 59, 59)));
      dateParams.add(Timestamp.valueOf(startDate.atStartOfDay()));
      dateParams.add(Timestamp.valueOf(endDate.atTime(23, 59, 59)));
    }

    // 1. Channel Breakdown
    String channelSql = """
        select coalesce(nullif(trim(d.attribution_channel), ''), 'DIRECT_SALES') as channel,
               count(*) as total_deals,
               coalesce(sum(case when d.stage not in ('WON', 'LOST') then d.amount else 0 end), 0) as open_amount,
               coalesce(sum(case when d.stage = 'WON' then 1 else 0 end), 0) as won_deals,
               coalesce(sum(case when d.stage = 'WON' then d.amount else 0 end), 0) as won_amount,
               coalesce(sum(case when d.stage = 'LOST' then 1 else 0 end), 0) as lost_deals
        from crm_deals d
        where d.company_id = ?
        """ + dateFilter + """
        group by 1
        order by won_amount desc, open_amount desc
        """;

    List<Object> chParams = new ArrayList<>();
    chParams.add(companyId);
    chParams.addAll(dateParams);

    List<ChannelAttribution> channels =
        jdbc.query(
            channelSql,
            (rs, row) -> {
              String ch = rs.getString("channel");
              int wonDeals = rs.getInt("won_deals");
              int lostDeals = rs.getInt("lost_deals");
              int totalClosed = wonDeals + lostDeals;
              double winRate = totalClosed > 0 ? (wonDeals * 100.0 / totalClosed) : 0.0;

              return new ChannelAttribution(
                  ch,
                  channelLabel(ch),
                  rs.getInt("total_deals"),
                  rs.getBigDecimal("open_amount"),
                  wonDeals,
                  rs.getBigDecimal("won_amount"),
                  lostDeals,
                  round1(winRate));
            },
            chParams.toArray());

    BigDecimal totalWonRevenue = BigDecimal.ZERO;
    BigDecimal marketingAttributedWon = BigDecimal.ZERO;
    int totalAttributedDeals = 0;

    for (ChannelAttribution c : channels) {
      totalWonRevenue = totalWonRevenue.add(c.wonAmount());
      if (isMarketingChannel(c.channel())) {
        marketingAttributedWon = marketingAttributedWon.add(c.wonAmount());
        totalAttributedDeals += c.dealsCount();
      }
    }

    double marketingContributionPct =
        totalWonRevenue.compareTo(BigDecimal.ZERO) > 0
            ? marketingAttributedWon
                .multiply(BigDecimal.valueOf(100))
                .divide(totalWonRevenue, 2, RoundingMode.HALF_UP)
                .doubleValue()
            : 0.0;

    // 2. Top Broadcast Messages
    String bcSql = """
        select bm.id, coalesce(nullif(trim(bm.subject), ''), 'Broadcast #' || bm.id) as title,
               bm.channel_type,
               count(d.id) as deals_count,
               coalesce(sum(case when d.stage not in ('WON', 'LOST') then d.amount else 0 end), 0) as open_amount,
               coalesce(sum(case when d.stage = 'WON' then 1 else 0 end), 0) as won_deals,
               coalesce(sum(case when d.stage = 'WON' then d.amount else 0 end), 0) as won_amount
        from crm_deals d
        join broadcast_messages bm on bm.id = d.broadcast_message_id
        where d.company_id = ?
        """ + dateFilter + """
        group by bm.id, bm.subject, bm.channel_type
        order by won_amount desc, open_amount desc
        limit 15
        """;

    List<CampaignRoi> topBroadcasts =
        jdbc.query(
            bcSql,
            (rs, row) ->
                new CampaignRoi(
                    String.valueOf(rs.getLong("id")),
                    rs.getString("title"),
                    "BROADCAST",
                    rs.getString("channel_type"),
                    rs.getInt("deals_count"),
                    rs.getBigDecimal("open_amount"),
                    rs.getInt("won_deals"),
                    rs.getBigDecimal("won_amount")),
            chParams.toArray());

    // 3. Top Ad Campaigns
    String adSql = """
        select ac.id, ac.name as title, ac.provider,
               count(d.id) as deals_count,
               coalesce(sum(case when d.stage not in ('WON', 'LOST') then d.amount else 0 end), 0) as open_amount,
               coalesce(sum(case when d.stage = 'WON' then 1 else 0 end), 0) as won_deals,
               coalesce(sum(case when d.stage = 'WON' then d.amount else 0 end), 0) as won_amount
        from crm_deals d
        join ad_campaigns ac on ac.id = d.ad_campaign_id
        where d.company_id = ?
        """ + dateFilter + """
        group by ac.id, ac.name, ac.provider
        order by won_amount desc, open_amount desc
        limit 15
        """;

    List<CampaignRoi> topAdCampaigns =
        jdbc.query(
            adSql,
            (rs, row) ->
                new CampaignRoi(
                    String.valueOf(rs.getLong("id")),
                    rs.getString("title"),
                    "AD_CAMPAIGN",
                    rs.getString("provider"),
                    rs.getInt("deals_count"),
                    rs.getBigDecimal("open_amount"),
                    rs.getInt("won_deals"),
                    rs.getBigDecimal("won_amount")),
            chParams.toArray());

    // 4. Top Lead Forms
    String lfSql = """
        select lf.id, lf.title,
               count(d.id) as deals_count,
               coalesce(sum(case when d.stage not in ('WON', 'LOST') then d.amount else 0 end), 0) as open_amount,
               coalesce(sum(case when d.stage = 'WON' then 1 else 0 end), 0) as won_deals,
               coalesce(sum(case when d.stage = 'WON' then d.amount else 0 end), 0) as won_amount
        from crm_deals d
        join crm_lead_forms lf on lf.id = d.lead_form_id and lf.company_id = d.company_id
        where d.company_id = ?
        """ + dateFilter + """
        group by lf.id, lf.title
        order by won_amount desc, open_amount desc
        limit 15
        """;

    List<CampaignRoi> topLeadForms =
        jdbc.query(
            lfSql,
            (rs, row) ->
                new CampaignRoi(
                    String.valueOf(rs.getLong("id")),
                    rs.getString("title"),
                    "WEB_FORM",
                    "INBOUND_FORM",
                    rs.getInt("deals_count"),
                    rs.getBigDecimal("open_amount"),
                    rs.getInt("won_deals"),
                    rs.getBigDecimal("won_amount")),
            chParams.toArray());

    return new AttributionReport(
        p,
        periodLabel,
        totalWonRevenue,
        marketingAttributedWon,
        round1(marketingContributionPct),
        totalAttributedDeals,
        channels,
        topBroadcasts,
        topAdCampaigns,
        topLeadForms,
        Instant.now());
  }

  private static String channelLabel(String ch) {
    if (ch == null) return "Direct Sales";
    return switch (ch.toUpperCase(Locale.ROOT)) {
      case "DIRECT_SALES" -> "Direct Sales / Outbound";
      case "BROADCAST" -> "WhatsApp, Email & SMS Broadcasts";
      case "META_ADS" -> "Meta Ads (Facebook & Instagram)";
      case "GOOGLE_ADS" -> "Google Ads";
      case "WEB_FORM" -> "Web Inbound Lead Forms";
      case "REFERRAL" -> "Customer Referrals";
      case "ORGANIC" -> "Organic Search & Website";
      case "EVENT_WEBINAR" -> "Events & Webinars";
      case "PARTNER" -> "Partners & Affiliates";
      default -> ch.replace('_', ' ');
    };
  }

  private static boolean isMarketingChannel(String ch) {
    if (ch == null) return false;
    String upper = ch.toUpperCase(Locale.ROOT);
    return !upper.equals("DIRECT_SALES");
  }

  private static double round1(double v) {
    return Math.round(v * 10.0) / 10.0;
  }
}
