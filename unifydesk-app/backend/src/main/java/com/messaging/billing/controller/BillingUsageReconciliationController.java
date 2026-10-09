package com.messaging.billing.controller;

import com.messaging.admin.service.PlatformAdminAuthorizationService;
import com.messaging.common.exception.BadRequestException;
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

  /** Cross-check successful provider handoffs against canonical billable events. */
  public record SourceReconciliation(
      String companyId,
      long acceptedEmailHandoffs,
      long committedSmsRecipients,
      long missingEmailFinancialEvents,
      long missingSmsFinancialEvents,
      long mismatchedSmsSegments) {}

  public record ReconciliationReport(
      Instant from, Instant to, List<CompanyReconciliation> companies,
      List<SourceReconciliation> sourceChecks) {}

  @GetMapping("/usage-reconciliation")
  public ResponseEntity<ApiResponse<ReconciliationReport>> reconcile(
      @RequestParam Instant from, @RequestParam Instant to) {
    authorization.requireSuperAdmin(currentUserService.currentUserId());
    if (!from.isBefore(to) || to.isAfter(Instant.now().plusSeconds(60))
        || from.isBefore(to.minusSeconds(366L * 86400))) {
      throw new BadRequestException("Reconciliation reporting period must span at most 366 days");
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
    // Operational handoff records are authoritative for their own service. A missing
    // canonical usage record is an accounting gap, not proof of a billable charge.
    // Check the source's occurrence interval, not just usage_events.occurred_at: late
    // financial events may be inserted in a different time window.
    List<SourceReconciliation> sourceChecks = jdbcTemplate.query(
        """
        with source_email as (
          select e.company_id, e.recipient_id
            from managed_email_usage_events e
           where e.event_type = 'COMMIT'
             and e.created_at >= ? and e.created_at < ?
        ),
        email_summary as (
          select e.company_id,
                 count(*) as accepted,
                 count(*) filter (where not exists (
                   select 1 from usage_events u
                    where u.company_id = e.company_id
                      and u.service_ownership = 'MANAGED'
                      and u.billing_sku = 'MANAGED_EMAIL_OUTBOUND'
                      and u.idempotency_key =
                        'managed-email:recipient:' || e.recipient_id::text || ':accepted'
                 )) as missing
            from source_email e
           group by e.company_id
        ),
        source_sms as (
          select r.company_id, r.recipient_id, r.estimated_segments
            from managed_sms_usage_reservations r
           where r.state = 'COMMITTED'
             and r.updated_at >= ? and r.updated_at < ?
        ),
        sms_summary as (
          select r.company_id, count(*) as accepted,
                 count(*) filter (where u.id is null) as missing,
                 count(*) filter (where u.id is not null
                                     and u.quantity <> r.estimated_segments) as mismatched
            from source_sms r
            left join usage_events u
              on u.company_id = r.company_id
             and u.service_ownership = 'MANAGED'
             and u.billing_sku = 'MANAGED_SMS_SEGMENT'
             and u.idempotency_key =
                 'managed-sms:recipient:' || r.recipient_id::text || ':accepted'
           group by r.company_id
        )
        select coalesce(e.company_id,s.company_id) as company_id,
               coalesce(e.accepted,0) as email_accepted,
               coalesce(s.accepted,0) as sms_accepted,
               coalesce(e.missing,0) as email_missing,
               coalesce(s.missing,0) as sms_missing,
               coalesce(s.mismatched,0) as sms_mismatched
          from email_summary e
          full outer join sms_summary s on s.company_id = e.company_id
         order by email_missing desc, sms_missing desc, sms_mismatched desc, company_id
        """,
        (rs, row) -> new SourceReconciliation(
            Long.toString(rs.getLong("company_id")),
            rs.getLong("email_accepted"), rs.getLong("sms_accepted"),
            rs.getLong("email_missing"), rs.getLong("sms_missing"),
            rs.getLong("sms_mismatched")),
        Timestamp.from(from), Timestamp.from(to),
        Timestamp.from(from), Timestamp.from(to));
    return ResponseEntity.ok(ApiResponse.success(
        HttpStatus.OK.value(), new ReconciliationReport(from, to, rows, sourceChecks),
        "Managed usage reconciliation fetched"));
  }
}
