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
 * Verified financial-ledger analytics, distinct from billable usage.
 * - Invoiced: finalized invoices (OPEN/PAID/UNCOLLECTIBLE) by finalized_at.
 * - Collected: successful payments by succeeded_at.
 * - Refunded: successful refunds by provider processed time or updated_at fallback.
 * - Open balance: CURRENT total amount_due from OPEN invoices, not date-windowed.
 * Includes tax where present; not an accrual-revenue recognition report.
 */
@RestController
@RequestMapping("/api/v1/admin/billing/analytics")
@RequiredArgsConstructor
public class BillingFinancialAnalyticsController {
  private final JdbcTemplate jdbcTemplate;
  private final PlatformAdminAuthorizationService authorization;
  private final CurrentUserService currentUserService;

  public record FinancialTotals(
      BigDecimal invoicedInr, BigDecimal collectedInr,
      BigDecimal refundedInr, BigDecimal openBalanceInr) {}

  public record CompanyFinancial(
      String companyId, BigDecimal invoicedInr, BigDecimal collectedInr,
      BigDecimal refundedInr, BigDecimal openBalanceInr) {}

  public record DailyFinancial(
      String day, BigDecimal invoicedInr, BigDecimal collectedInr, BigDecimal refundedInr) {}

  public record FinancialReport(
      Instant from, Instant to, FinancialTotals totals,
      List<CompanyFinancial> companies, List<DailyFinancial> daily) {}

  private static final String EVENTS_SQL = """
      with financial_events as (
        select company_id, finalized_at as event_at, total as invoiced,
               0::numeric as collected, 0::numeric as refunded
          from invoices
         where currency = 'INR' and status in ('OPEN', 'PAID', 'UNCOLLECTIBLE')
           and finalized_at is not null
        union all
        select company_id, succeeded_at as event_at, 0::numeric, amount, 0::numeric
          from payments
         where currency = 'INR' and status = 'SUCCEEDED'
           and succeeded_at is not null
        union all
        select company_id,
               coalesce(provider_processed_at, updated_at) as event_at,
               0::numeric, 0::numeric, amount
          from refunds
         where currency = 'INR' and status = 'SUCCEEDED'
      )
      """;

  @GetMapping("/financial")
  public ResponseEntity<ApiResponse<FinancialReport>> overview(
      @RequestParam Instant from, @RequestParam Instant to) {
    authorization.requireSuperAdmin(currentUserService.currentUserId());
    if (!from.isBefore(to) || to.isAfter(Instant.now().plusSeconds(60))
        || from.isBefore(to.minusSeconds(366L * 86400))) {
      throw new IllegalArgumentException("Financial reporting period must span at most 366 days");
    }
    Timestamp start = Timestamp.from(from);
    Timestamp end = Timestamp.from(to);

    List<CompanyFinancial> companies = jdbcTemplate.query(
        EVENTS_SQL + """
        , in_period as (
            select company_id, sum(invoiced) as invoiced,
                   sum(collected) as collected, sum(refunded) as refunded
              from financial_events
             where event_at >= ? and event_at < ?
             group by company_id
          ),
          open_balances as (
            select company_id, sum(amount_due) as open_balance
              from invoices
             where status = 'OPEN' and currency = 'INR'
             group by company_id
          )
        select coalesce(p.company_id, b.company_id) as company_id,
               coalesce(p.invoiced, 0) as invoiced,
               coalesce(p.collected, 0) as collected,
               coalesce(p.refunded, 0) as refunded,
               coalesce(b.open_balance, 0) as open_balance
          from in_period p full outer join open_balances b on p.company_id = b.company_id
         order by invoiced desc, company_id
        """,
        (rs, row) -> new CompanyFinancial(
            Long.toString(rs.getLong("company_id")), rs.getBigDecimal("invoiced"),
            rs.getBigDecimal("collected"), rs.getBigDecimal("refunded"),
            rs.getBigDecimal("open_balance")), start, end);

    List<DailyFinancial> daily = jdbcTemplate.query(
        EVENTS_SQL + """
        select to_char((event_at at time zone 'UTC')::date, 'YYYY-MM-DD') as day,
               coalesce(sum(invoiced), 0) as invoiced,
               coalesce(sum(collected), 0) as collected,
               coalesce(sum(refunded), 0) as refunded
          from financial_events
         where event_at >= ? and event_at < ?
         group by (event_at at time zone 'UTC')::date
         order by day
        """,
        (rs, row) -> new DailyFinancial(
            rs.getString("day"), rs.getBigDecimal("invoiced"),
            rs.getBigDecimal("collected"), rs.getBigDecimal("refunded")), start, end);

    BigDecimal invoiced = BigDecimal.ZERO;
    BigDecimal collected = BigDecimal.ZERO;
    BigDecimal refunded = BigDecimal.ZERO;
    BigDecimal outstanding = BigDecimal.ZERO;
    for (CompanyFinancial company : companies) {
      invoiced = invoiced.add(company.invoicedInr());
      collected = collected.add(company.collectedInr());
      refunded = refunded.add(company.refundedInr());
      outstanding = outstanding.add(company.openBalanceInr());
    }
    return ResponseEntity.ok(ApiResponse.success(
        HttpStatus.OK.value(),
        new FinancialReport(from, to,
            new FinancialTotals(invoiced, collected, refunded, outstanding), companies, daily),
        "Financial analytics fetched"));
  }
}
