package com.messaging.billing.controller;

import com.messaging.admin.service.PlatformAdminAuthorizationService;
import com.messaging.common.response.ApiResponse;
import com.messaging.security.service.CurrentUserService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Super-admin read-only managed usage analytics. This reports recorded, priced usage events,
 * not cash collected or provider delivery telemetry. All intervals are half-open [from, to).
 */
@RestController
@RequestMapping("/api/v1/admin/billing/analytics")
@RequiredArgsConstructor
public class BillingUsageAnalyticsController {
  private final JdbcTemplate jdbcTemplate;
  private final PlatformAdminAuthorizationService authorizationService;
  private final CurrentUserService currentUserService;

  public record ChannelTotals(long emailMessages, long smsSegments, long whatsappMessages,
                              BigDecimal managedChargesInr) {}

  public record CompanyUsage(long companyId, long emailMessages, long smsSegments,
                             long whatsappMessages, BigDecimal managedChargesInr) {}

  public record DailyUsage(String day, long emailMessages, long smsSegments,
                           long whatsappMessages, BigDecimal managedChargesInr) {}

  public record UsageReport(Instant from, Instant to, ChannelTotals totals,
                            List<CompanyUsage> companies, List<DailyUsage> daily) {}

  @GetMapping("/managed-usage")
  public ResponseEntity<ApiResponse<UsageReport>> managedUsage(
      @RequestParam Instant from, @RequestParam Instant to) {
    authorizationService.requireSuperAdmin(currentUserService.currentUserId());
    if (!from.isBefore(to) || to.isAfter(Instant.now().plusSeconds(60))) {
      throw new IllegalArgumentException("Invalid analytics interval");
    }
    if (from.isBefore(to.minusSeconds(366L * 86400))) {
      throw new IllegalArgumentException("Analytics interval cannot exceed 366 days");
    }
    Timestamp start = Timestamp.from(from);
    Timestamp end = Timestamp.from(to);
    // Billing is explicitly in INR. Do not mix foreign-currency monetary amounts.
    String predicate = """
        from usage_events u
        where u.service_ownership = 'MANAGED'
          and u.currency = 'INR'
          and u.occurred_at >= ? and u.occurred_at < ?
        """;
    String columns = """
        coalesce(sum(case when u.billing_sku = 'MANAGED_EMAIL_OUTBOUND'
          then u.quantity else 0 end), 0) as email_count,
        coalesce(sum(case when u.billing_sku = 'MANAGED_SMS_SEGMENT'
          then u.quantity else 0 end), 0) as sms_count,
        coalesce(sum(case when u.channel = 'WHATSAPP'
          then u.quantity else 0 end), 0) as whatsapp_count,
        coalesce(sum(u.total_price), 0) as charges
        """;
    ChannelTotals totals = jdbcTemplate.queryForObject(
        "select " + columns + predicate, (rs, n) -> new ChannelTotals(
            rs.getLong("email_count"), rs.getLong("sms_count"),
            rs.getLong("whatsapp_count"), rs.getBigDecimal("charges")), start, end);
    List<CompanyUsage> companies = jdbcTemplate.query(
        "select u.company_id, " + columns + predicate +
            "group by u.company_id order by charges desc, u.company_id",
        (rs, n) -> new CompanyUsage(rs.getLong("company_id"), rs.getLong("email_count"),
            rs.getLong("sms_count"), rs.getLong("whatsapp_count"),
            rs.getBigDecimal("charges")), start, end);
    // UTC day boundaries are deliberate. Frontend must label the timezone.
    List<DailyUsage> daily = jdbcTemplate.query(
        "select to_char((u.occurred_at at time zone 'UTC')::date, 'YYYY-MM-DD') as day, " +
            columns + predicate +
            "group by (u.occurred_at at time zone 'UTC')::date order by day",
        (rs, n) -> new DailyUsage(rs.getString("day"), rs.getLong("email_count"),
            rs.getLong("sms_count"), rs.getLong("whatsapp_count"),
            rs.getBigDecimal("charges")), start, end);
    return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(),
        new UsageReport(from, to, totals, companies, daily),
        "Managed usage analytics fetched"));
  }
}
