package com.messaging.managedsms.metering;

import com.messaging.billing.enums.BillingCategory;
import com.messaging.billing.enums.CommunicationDirection;
import com.messaging.billing.enums.UsageUnit;
import com.messaging.billing.exception.PlanEntitlementException;
import com.messaging.billing.service.EntitlementService;
import com.messaging.billing.service.UsageBillingService;
import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.enums.ProviderType;
import com.messaging.common.id.IdGenerator;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManagedSmsUsageService {

  public static final String MONTHLY_ENTITLEMENT = "MANAGED_SMS_SEGMENTS_MONTHLY";

  private final JdbcTemplate jdbcTemplate;
  private final EntitlementService entitlementService;
  private final UsageBillingService usageBillingService;

  @Transactional(readOnly = true)
  public boolean canReserve(Long companyId, long segments) {
    if (segments <= 0) return true;
    long limit = entitlementService.integerValue(companyId, MONTHLY_ENTITLEMENT);
    if (limit < 0) return true;
    return allocated(companyId, periodStart()) + segments <= limit;
  }

  @Transactional
  public void reserve(
      Long companyId, Long runId, Long recipientId, int attemptNo, int estimatedSegments) {
    if (estimatedSegments <= 0) {
      throw new IllegalArgumentException("Managed SMS segment reservation must be positive");
    }
    String key = reserveKey(recipientId, attemptNo);
    if (exists(key)) return;
    lockCompany(companyId);
    long limit = entitlementService.integerValue(companyId, MONTHLY_ENTITLEMENT);
    if (limit >= 0 && allocated(companyId, periodStart()) + estimatedSegments > limit) {
      throw new PlanEntitlementException("MANAGED_SMS_LIMIT_REACHED");
    }
    jdbcTemplate.update(
        """
        insert into managed_sms_usage_reservations (
          id, company_id, run_id, recipient_id, attempt_no, estimated_segments,
          state, idempotency_key, provider_credit_snapshot, created_at, updated_at
        ) values (?, ?, ?, ?, ?, ?, 'RESERVED', ?, null, now(), now())
        on conflict (idempotency_key) do nothing
        """,
        IdGenerator.nextId(),
        companyId,
        runId,
        recipientId,
        attemptNo,
        estimatedSegments,
        key);
  }

  @Transactional
  public void commit(
      Long companyId,
      Long runId,
      Long recipientId,
      int attemptNo,
      String providerRequestId,
      String messageCategory,
      BigDecimal providerCredit) {
    Reservation reservation = lockReservation(recipientId, attemptNo);
    if (reservation == null
        || "COMMITTED".equals(reservation.state())
        || "RELEASED".equals(reservation.state())) return;

    String billingKey =
        "managed-sms:recipient:" + recipientId + ":attempt:" + attemptNo + ":accepted";
    usageBillingService.record(
        companyId,
        "MANAGED_SMS",
        recipientId.toString(),
        billingKey,
        ChannelType.SMS,
        ProviderType.MSG91_SMS,
        billingCategory(messageCategory),
        CommunicationDirection.OUTBOUND,
        "IN",
        null,
        UsageUnit.MESSAGE,
        reservation.segments(),
        Instant.now());

    jdbcTemplate.update(
        """
        update managed_sms_usage_reservations
           set state = 'COMMITTED',
               provider_credit_snapshot = coalesce(?, provider_credit_snapshot),
               updated_at = now()
         where recipient_id = ?
           and attempt_no = ?
           and company_id = ?
           and state in ('RESERVED', 'HELD_UNKNOWN')
        """,
        providerCredit,
        recipientId,
        attemptNo,
        companyId);
  }

  @Transactional
  public void holdUnknown(Long companyId, Long recipientId, int attemptNo) {
    jdbcTemplate.update(
        """
        update managed_sms_usage_reservations
           set state = 'HELD_UNKNOWN', updated_at = now()
         where recipient_id = ?
           and attempt_no = ?
           and company_id = ?
           and state = 'RESERVED'
        """,
        recipientId,
        attemptNo,
        companyId);
  }

  @Transactional
  public void release(Long companyId, Long recipientId, int attemptNo) {
    jdbcTemplate.update(
        """
        update managed_sms_usage_reservations
           set state = 'RELEASED', updated_at = now()
         where recipient_id = ?
           and attempt_no = ?
           and company_id = ?
           and state in ('RESERVED', 'HELD_UNKNOWN')
        """,
        recipientId,
        attemptNo,
        companyId);
  }

  @Transactional
  public void commitLatest(
      Long companyId,
      Long runId,
      Long recipientId,
      String providerRequestId,
      String messageCategory,
      BigDecimal providerCredit) {
    List<Integer> attempts =
        jdbcTemplate.queryForList(
            """
            select attempt_no
              from managed_sms_usage_reservations
             where company_id = ?
               and recipient_id = ?
               and state in ('RESERVED', 'HELD_UNKNOWN')
             order by attempt_no desc
             limit 1
            """,
            Integer.class,
            companyId,
            recipientId);
    if (!attempts.isEmpty()) {
      commit(
          companyId,
          runId,
          recipientId,
          attempts.getFirst(),
          providerRequestId,
          messageCategory,
          providerCredit);
    }
  }

  @Transactional(readOnly = true)
  public UsageSummary summary(Long companyId, boolean sendingEnabled) {
    LocalDate start = periodStart();
    LocalDate end = start.plusMonths(1);
    long accepted = sum(companyId, start, "COMMITTED");
    long reserved = sum(companyId, start, "RESERVED");
    long unknown = sum(companyId, start, "HELD_UNKNOWN");
    long limit = entitlementService.integerValue(companyId, MONTHLY_ENTITLEMENT);
    long remaining = limit < 0 ? -1 : Math.max(0, limit - accepted - reserved - unknown);
    return new UsageSummary(start, end, accepted, reserved, unknown, limit, remaining, sendingEnabled);
  }

  private long allocated(Long companyId, LocalDate period) {
    return sum(companyId, period, "COMMITTED")
        + sum(companyId, period, "RESERVED")
        + sum(companyId, period, "HELD_UNKNOWN");
  }

  private void lockCompany(Long companyId) {
    jdbcTemplate.execute(
        (ConnectionCallback<Void>)
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement("select pg_advisory_xact_lock(?)")) {
                statement.setLong(1, companyId);
                statement.execute();
              }
              return null;
            });
  }

  private long sum(Long companyId, LocalDate period, String state) {
    Long value =
        jdbcTemplate.queryForObject(
            """
            select coalesce(sum(estimated_segments), 0)
              from managed_sms_usage_reservations
             where company_id = ?
               and state = ?
               and created_at >= ?
               and created_at < ?
            """,
            Long.class,
            companyId,
            state,
            Date.valueOf(period),
            Date.valueOf(period.plusMonths(1)));
    return value == null ? 0L : value;
  }

  private Reservation lockReservation(Long recipientId, int attemptNo) {
    List<Reservation> rows =
        jdbcTemplate.query(
            """
            select estimated_segments, state
              from managed_sms_usage_reservations
             where recipient_id = ?
               and attempt_no = ?
             for update
            """,
            (rs, rowNum) -> new Reservation(rs.getInt("estimated_segments"), rs.getString("state")),
            recipientId,
            attemptNo);
    return rows.isEmpty() ? null : rows.getFirst();
  }

  private boolean exists(String key) {
    Integer count =
        jdbcTemplate.queryForObject(
            "select count(*) from managed_sms_usage_reservations where idempotency_key = ?",
            Integer.class,
            key);
    return count != null && count > 0;
  }

  private BillingCategory billingCategory(String category) {
    return "PROMOTIONAL".equalsIgnoreCase(category)
        ? BillingCategory.SMS_PROMOTIONAL
        : BillingCategory.SMS_TRANSACTIONAL;
  }

  private LocalDate periodStart() {
    return Instant.now().atZone(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1);
  }

  private String reserveKey(Long recipientId, int attemptNo) {
    return "managed-sms:recipient:" + recipientId + ":attempt:" + attemptNo + ":reserve";
  }

  private record Reservation(int segments, String state) {}

  public record UsageSummary(
      LocalDate periodStart,
      LocalDate periodEnd,
      long acceptedSegments,
      long reservedSegments,
      long unknownSegments,
      long limit,
      long remaining,
      boolean sendingEnabled) {}
}
