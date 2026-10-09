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

/** Read-only diagnostics; does not rewrite invoices or alter immutable usage pricing. */
@RestController
@RequestMapping("/api/v1/admin/billing/analytics")
@RequiredArgsConstructor
public class BillingUsageReconciliationController {
  private final JdbcTemplate jdbcTemplate;
  private final PlatformAdminAuthorizationService authorization;
  private final CurrentUserService currentUserService;

  public record CompanyReconciliation(
      String companyId,
      long events, BigDecimal ratedInr, BigDecimal allocatedInr,
      long finalizedUnallocatedEvents, long missingInvoiceLines, long priceMathMismatches) {}

  public record ReconciliationReport(
      Instant from, Instant to, List<CompanyReconciliation> companies) {}

  @GetMapping("/usage-reconciliation")
  public ResponseEntity<ApiResponse<ReconciliationReport>> reconcile(
      @RequestParam Instant from, @RequestParam Instant to) {
    authorization.requireSuperAdmin(currentUserService.currentUserId());
    if (!from.isBefore(to) || to.isAfter(Instant.now().plusSeconds(60))
        || from.isBefore(to.minusSeconds(366L * 86400))) {
      throw new IllegalArgumentException("Reconciliation reporting period must span at most 366 days");
    }
    List<CompanyReconciliation> rows = jdbcTemplate.query(
        """
        select u.company_id,
               count(*) as events,
               coalesce(sum(u.total_price), 0) as rated,
               coalesce(sum(case when a.usage_event_id is not null
                            then u.total_price else 0 end), 0) as allocated,
               count(*) filter (where b.status = 'FINALIZED'
                                 and a.usage_event_id is null) as finalized_unallocated,
               count(*) filter (where a.usage_event_id is not null
                                 and l.id is null) as line_missing,
               count(*) filter (where u.total_price <>
                          round(u.unit_price_snapshot * u.quantity, 6)) as bad_price_math
          from usage_events u
          left join billing_periods b on b.id = u.billing_period_id
          left join usage_invoice_allocations a on a.usage_event_id = u.id
          left join invoice_line_items l
            on l.invoice_id = a.invoice_id
           and l.idempotency_key = 'USAGE_EVENT:' || u.id::text
         where u.service_ownership = 'MANAGED'
           and u.currency = 'INR'
           and u.occurred_at >= ? and u.occurred_at < ?
         group by u.company_id
         order by finalized_unallocated desc, line_missing desc, bad_price_math desc, rated desc
        """,
        (rs, row) -> new CompanyReconciliation(
            Long.toString(rs.getLong("company_id")), rs.getLong("events"),
            rs.getBigDecimal("rated"), rs.getBigDecimal("allocated"),
            rs.getLong("finalized_unallocated"), rs.getLong("line_missing"),
            rs.getLong("bad_price_math")),
        Timestamp.from(from), Timestamp.from(to));
    return ResponseEntity.ok(ApiResponse.success(
        HttpStatus.OK.value(), new ReconciliationReport(from, to, rows),
        "Managed usage reconciliation fetched"));
  }
}
