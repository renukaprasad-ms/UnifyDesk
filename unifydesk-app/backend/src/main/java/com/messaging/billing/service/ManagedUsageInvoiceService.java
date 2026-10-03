package com.messaging.billing.service;

import com.messaging.billing.dto.InvoiceLineRequest;
import com.messaging.billing.dto.InvoiceResponse;
import com.messaging.billing.enums.InvoiceLineType;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManagedUsageInvoiceService {

  private final JdbcTemplate jdbcTemplate;
  private final InvoiceService invoiceService;

  @Transactional
  public int materializeClosingPeriods() {
    List<PeriodRow> periods =
        jdbcTemplate.query(
            """
            select id, company_id, start_at, end_at
              from billing_periods
             where kind = 'MANAGED_USAGE'
               and status = 'CLOSING'
             order by end_at, id
             limit 100
            """,
            (rs, rowNum) ->
                new PeriodRow(
                    rs.getLong("id"),
                    rs.getLong("company_id"),
                    rs.getTimestamp("start_at").toInstant(),
                    rs.getTimestamp("end_at").toInstant()));
    int materialized = 0;
    for (PeriodRow period : periods) materialized += materialize(period);
    return materialized;
  }

  private int materialize(PeriodRow target) {
    InvoiceResponse invoice = invoiceService.createDraft(target.companyId(), target.id(), null);
    long invoiceId = Long.parseLong(invoice.invoiceId());

    List<UsageRow> rows =
        jdbcTemplate.query(
            """
            select u.id,
                   u.quantity,
                   u.unit_price_snapshot,
                   u.billing_sku,
                   original.start_at as original_start,
                   original.end_at as original_end,
                   original.id as original_period_id
              from usage_events u
              join billing_periods original on original.id = u.billing_period_id
              left join usage_invoice_allocations allocation on allocation.usage_event_id = u.id
             where u.company_id = ?
               and u.service_ownership = 'MANAGED'
               and allocation.usage_event_id is null
               and (
                 original.id = ?
                 or (original.status = 'FINALIZED' and original.end_at <= ?)
               )
             order by u.occurred_at, u.id
            """,
            (rs, rowNum) ->
                new UsageRow(
                    rs.getLong("id"),
                    rs.getLong("quantity"),
                    rs.getBigDecimal("unit_price_snapshot"),
                    rs.getString("billing_sku"),
                    rs.getTimestamp("original_start").toInstant(),
                    rs.getTimestamp("original_end").toInstant(),
                    rs.getLong("original_period_id")),
            target.companyId(),
            target.id(),
            Timestamp.from(target.startAt()));

    int count = 0;
    for (UsageRow row : rows) {
      boolean priorPeriod = row.originalPeriodId() != target.id();
      String lineKey = "USAGE_EVENT:" + row.id();
      invoiceService.addLine(
          invoiceId,
          new InvoiceLineRequest(
              priorPeriod
                  ? InvoiceLineType.PRIOR_PERIOD_USAGE_ADJUSTMENT
                  : InvoiceLineType.MANAGED_USAGE,
              (priorPeriod ? "Prior-period " : "") + row.billingSku() + " usage",
              java.math.BigDecimal.valueOf(row.quantity()),
              row.unitPrice(),
              priorPeriod ? target.startAt() : row.originalStart(),
              priorPeriod ? target.endAt() : row.originalEnd(),
              priorPeriod ? row.originalStart() : null,
              priorPeriod ? row.originalEnd() : null,
              "usage:" + row.id(),
              lineKey));
      jdbcTemplate.update(
          """
          insert into usage_invoice_allocations
            (usage_event_id, invoice_id, billing_period_id, allocated_at)
          values (?, ?, ?, now())
          on conflict (usage_event_id) do nothing
          """,
          row.id(),
          invoiceId,
          target.id());
      count++;
    }
    return count;
  }

  private record PeriodRow(Long id, Long companyId, Instant startAt, Instant endAt) {}

  private record UsageRow(
      Long id,
      long quantity,
      java.math.BigDecimal unitPrice,
      String billingSku,
      Instant originalStart,
      Instant originalEnd,
      Long originalPeriodId) {}
}
