package com.messaging.managedemail.metering;

import com.messaging.billing.enums.BillingCategory;
import com.messaging.billing.enums.BillingSku;
import com.messaging.billing.enums.CommunicationDirection;
import com.messaging.billing.enums.ServiceOwnership;
import com.messaging.billing.enums.UsageUnit;
import com.messaging.billing.exception.PlanEntitlementException;
import com.messaging.billing.service.EntitlementService;
import com.messaging.billing.service.ManagedFinancialExposureGuard;
import com.messaging.billing.service.UsageBillingService;
import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.enums.ProviderType;
import com.messaging.managedemail.runtime.ManagedEmailIdGenerator;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManagedEmailUsageService {

  public static final String MONTHLY_ENTITLEMENT = "MANAGED_EMAIL_MESSAGES_MONTHLY";

  private final JdbcTemplate jdbcTemplate;
  private final EntitlementService entitlementService;
  private final ManagedEmailIdGenerator idGenerator;
  private final UsageBillingService usageBillingService;
  private final ManagedFinancialExposureGuard financialExposureGuard;

  @Transactional(readOnly = true)
  public boolean canReserve(Long companyId, long units) {
    if (units <= 0) return true;
    long limit = entitlementService.integerValue(companyId, MONTHLY_ENTITLEMENT);
    if (limit < 0) return true;
    UsageRow row = currentRow(companyId, periodStart(Instant.now()));
    return allocated(row) + units <= limit;
  }

  @Transactional
  public void reserve(Long companyId, Long recipientId, Long runId, int attemptNo) {
    financialExposureGuard.assertAllowed(companyId);
    LocalDate period = periodStart(Instant.now());
    ensurePeriod(companyId, period);
    UsageRow row = lockRow(companyId, period);
    String key = reserveKey(recipientId, attemptNo);
    if (eventExists(key)) return;

    long limit = entitlementService.integerValue(companyId, MONTHLY_ENTITLEMENT);
    if (limit >= 0 && allocated(row) + 1 > limit) {
      throw new PlanEntitlementException("MANAGED_EMAIL_MONTHLY_LIMIT_REACHED");
    }

    int inserted =
        jdbcTemplate.update(
            """
            insert into managed_email_usage_events
              (id, company_id, recipient_id, run_id, event_type, units, period_start,
               idempotency_key, created_at)
            values (?, ?, ?, ?, 'RESERVE', 1, ?, ?, now())
            on conflict (idempotency_key) do nothing
            """,
            idGenerator.nextId(),
            companyId,
            recipientId,
            runId,
            Date.valueOf(period),
            key);
    if (inserted == 1) {
      jdbcTemplate.update(
          """
          update managed_email_usage_periods
             set reserved_count = reserved_count + 1,
                 updated_at = now(),
                 version = version + 1
           where company_id = ?
             and period_start = ?
          """,
          companyId,
          Date.valueOf(period));
    }
  }

  @Transactional
  public void commit(
      Long companyId,
      Long recipientId,
      Long runId,
      int attemptNo,
      String providerMessageId) {
    commit(companyId, recipientId, runId, attemptNo, providerMessageId, Instant.now());
  }

  @Transactional
  public void commit(
      Long companyId,
      Long recipientId,
      Long runId,
      int attemptNo,
      String providerMessageId,
      Instant acceptedAt) {
    String reserveKey = reserveKey(recipientId, attemptNo);
    LocalDate period = reservationPeriod(reserveKey);
    if (period == null) return;
    ensurePeriod(companyId, period);
    lockRow(companyId, period);

    int inserted =
        jdbcTemplate.update(
            """
            insert into managed_email_usage_events
              (id, company_id, recipient_id, run_id, provider_message_id, event_type, units,
               period_start, idempotency_key, created_at)
            values (?, ?, ?, ?, ?, 'COMMIT', 1, ?, ?, now())
            on conflict (idempotency_key) do nothing
            """,
            idGenerator.nextId(),
            companyId,
            recipientId,
            runId,
            trim(providerMessageId),
            Date.valueOf(period),
            commitKey(recipientId));
    if (inserted == 1) {
      jdbcTemplate.update(
          """
          update managed_email_usage_periods
             set accepted_count = accepted_count + 1,
                 reserved_count = greatest(reserved_count - 1, 0),
                 updated_at = now(),
                 version = version + 1
           where company_id = ?
             and period_start = ?
          """,
          companyId,
          Date.valueOf(period));
    }

    usageBillingService.record(
        companyId,
        ServiceOwnership.MANAGED,
        BillingSku.MANAGED_EMAIL_OUTBOUND,
        "MANAGED_EMAIL",
        recipientId.toString(),
        providerMessageId,
        "managed-email:recipient:" + recipientId + ":accepted",
        ChannelType.EMAIL,
        ProviderType.AMAZON_SES,
        BillingCategory.EMAIL_OUTBOUND,
        CommunicationDirection.OUTBOUND,
        null,
        null,
        UsageUnit.MESSAGE,
        1,
        acceptedAt == null ? Instant.now() : acceptedAt);
  }

  @Transactional
  public void commitLatest(
      Long companyId, Long recipientId, Long runId, String providerMessageId) {
    if (eventExists(commitKey(recipientId))) return;
    List<String> keys =
        jdbcTemplate.queryForList(
            """
            select idempotency_key
              from managed_email_usage_events
             where recipient_id = ?
               and company_id = ?
               and event_type = 'RESERVE'
             order by created_at desc
             limit 1
            """,
            String.class,
            recipientId,
            companyId);
    if (keys.isEmpty()) return;
    Integer attemptNo = parseAttempt(keys.getFirst());
    if (attemptNo != null) {
      commit(companyId, recipientId, runId, attemptNo, providerMessageId);
    }
  }

  @Transactional
  public void release(Long companyId, Long recipientId, Long runId, int attemptNo) {
    String reserveKey = reserveKey(recipientId, attemptNo);
    LocalDate period = reservationPeriod(reserveKey);
    if (period == null || eventExists(commitKey(recipientId))) return;
    ensurePeriod(companyId, period);
    lockRow(companyId, period);

    int inserted =
        jdbcTemplate.update(
            """
            insert into managed_email_usage_events
              (id, company_id, recipient_id, run_id, event_type, units, period_start,
               idempotency_key, created_at)
            values (?, ?, ?, ?, 'RELEASE', -1, ?, ?, now())
            on conflict (idempotency_key) do nothing
            """,
            idGenerator.nextId(),
            companyId,
            recipientId,
            runId,
            Date.valueOf(period),
            releaseKey(recipientId, attemptNo));
    if (inserted == 1) {
      jdbcTemplate.update(
          """
          update managed_email_usage_periods
             set reserved_count = greatest(reserved_count - 1, 0),
                 updated_at = now(),
                 version = version + 1
           where company_id = ?
             and period_start = ?
          """,
          companyId,
          Date.valueOf(period));
    }
  }

  @Transactional(readOnly = true)
  public ManagedEmailUsageResponse summary(Long companyId) {
    LocalDate period = periodStart(Instant.now());
    UsageRow row = currentRow(companyId, period);
    long limit = entitlementService.integerValue(companyId, MONTHLY_ENTITLEMENT);
    long billable = Math.max(0, row.accepted() + row.adjusted());
    long usedForQuota = allocated(row);
    long remaining = limit < 0 ? -1 : Math.max(0, limit - usedForQuota);
    return new ManagedEmailUsageResponse(
        period,
        period.plusMonths(1),
        row.accepted(),
        row.reserved(),
        row.adjusted(),
        billable,
        limit,
        remaining,
        limit >= 0 && remaining == 0);
  }

  private long allocated(UsageRow row) {
    return row.accepted() + row.reserved() + Math.max(0, row.adjusted());
  }

  private void ensurePeriod(Long companyId, LocalDate period) {
    jdbcTemplate.update(
        """
        insert into managed_email_usage_periods
          (company_id, period_start, accepted_count, reserved_count, adjusted_count,
           updated_at, version)
        values (?, ?, 0, 0, 0, now(), 0)
        on conflict (company_id, period_start) do nothing
        """,
        companyId,
        Date.valueOf(period));
  }

  private UsageRow lockRow(Long companyId, LocalDate period) {
    return jdbcTemplate.queryForObject(
        """
        select accepted_count, reserved_count, adjusted_count
          from managed_email_usage_periods
         where company_id = ?
           and period_start = ?
         for update
        """,
        (resultSet, rowNum) ->
            new UsageRow(
                resultSet.getLong("accepted_count"),
                resultSet.getLong("reserved_count"),
                resultSet.getLong("adjusted_count")),
        companyId,
        Date.valueOf(period));
  }

  private UsageRow currentRow(Long companyId, LocalDate period) {
    List<UsageRow> rows =
        jdbcTemplate.query(
            """
            select accepted_count, reserved_count, adjusted_count
              from managed_email_usage_periods
             where company_id = ?
               and period_start = ?
            """,
            (resultSet, rowNum) ->
                new UsageRow(
                    resultSet.getLong("accepted_count"),
                    resultSet.getLong("reserved_count"),
                    resultSet.getLong("adjusted_count")),
            companyId,
            Date.valueOf(period));
    return rows.isEmpty() ? new UsageRow(0, 0, 0) : rows.getFirst();
  }

  private boolean eventExists(String key) {
    Integer count =
        jdbcTemplate.queryForObject(
            "select count(*) from managed_email_usage_events where idempotency_key = ?",
            Integer.class,
            key);
    return count != null && count > 0;
  }

  private LocalDate reservationPeriod(String key) {
    List<Date> periods =
        jdbcTemplate.queryForList(
            """
            select period_start
              from managed_email_usage_events
             where idempotency_key = ?
               and event_type = 'RESERVE'
            """,
            Date.class,
            key);
    return periods.isEmpty() ? null : periods.getFirst().toLocalDate();
  }

  private LocalDate periodStart(Instant instant) {
    return instant.atZone(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1);
  }

  private String reserveKey(Long recipientId, int attemptNo) {
    return "managed-email:reserve:" + recipientId + ":" + attemptNo;
  }

  private String releaseKey(Long recipientId, int attemptNo) {
    return "managed-email:release:" + recipientId + ":" + attemptNo;
  }

  private String commitKey(Long recipientId) {
    return "managed-email:commit:" + recipientId;
  }

  private Integer parseAttempt(String key) {
    int index = key.lastIndexOf(':');
    if (index < 0 || index == key.length() - 1) return null;
    try {
      return Integer.parseInt(key.substring(index + 1));
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  private String trim(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private record UsageRow(long accepted, long reserved, long adjusted) {}
}
