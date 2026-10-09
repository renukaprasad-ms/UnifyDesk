package com.messaging.billing.service;

import com.messaging.billing.dto.GlobalManagedRateResponse;
import com.messaging.billing.dto.ReviseGlobalManagedRateRequest;
import com.messaging.billing.enums.BillingCategory;
import com.messaging.billing.enums.BillingSku;
import com.messaging.billing.enums.CommunicationDirection;
import com.messaging.billing.enums.UsageUnit;
import com.messaging.channel.enums.ChannelType;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.ConflictException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Effective-dated fallback managed rates; never rewrites historical price snapshots.
 * Global rules apply to all managed providers only after company and plan pricing.
 */
@Service
@RequiredArgsConstructor
public class GlobalManagedRateService {
  private final JdbcTemplate jdbcTemplate;

  private static final String COLUMNS = """
      select id, billing_sku, price, currency, enabled, effective_from, effective_to,
             created_by_user_id, reason
        from billing_global_managed_rates
      """;

  /** Only the canonical managed outbound SKU shapes may inherit a global rate. */
  public static BillingSku skuFor(
      ChannelType channel, BillingCategory category,
      CommunicationDirection direction, UsageUnit unit) {
    if (direction != CommunicationDirection.OUTBOUND) return null;
    if (channel == ChannelType.EMAIL && category == BillingCategory.EMAIL_OUTBOUND
        && unit == UsageUnit.MESSAGE) return BillingSku.MANAGED_EMAIL_OUTBOUND;
    if (channel == ChannelType.SMS && unit == UsageUnit.SEGMENT
        && (category == BillingCategory.SMS_PROMOTIONAL
            || category == BillingCategory.SMS_TRANSACTIONAL
            || category == BillingCategory.SMS_OTP)) return BillingSku.MANAGED_SMS_SEGMENT;
    return null;
  }

  @Transactional(readOnly = true)
  public Optional<GlobalManagedRateResponse> effectiveRate(BillingSku sku, Instant occurredAt) {
    if (sku == null || occurredAt == null) return Optional.empty();
    List<GlobalManagedRateResponse> rates = jdbcTemplate.query(
        COLUMNS + """
            where billing_sku = ?
              and enabled = true
              and effective_from <= ?
              and (effective_to is null or effective_to > ?)
            order by effective_from desc limit 2
            """,
        (rs, row) -> map(rs),
        sku.name(), Timestamp.from(occurredAt), Timestamp.from(occurredAt));
    if (rates.size() > 1) {
      throw new ConflictException("Overlapping global managed rates for " + sku);
    }
    return rates.stream().findFirst();
  }

  @Transactional(readOnly = true)
  public List<GlobalManagedRateResponse> history() {
    return jdbcTemplate.query(
        COLUMNS + "order by billing_sku, effective_from desc",
        (rs, row) -> map(rs));
  }

  /**
   * A serialized, forward-only revision. Ends the previous interval at activation and
   * appends a new immutable price. No rewriting of already-priced financial usage.
   */
  @Transactional
  public GlobalManagedRateResponse revise(ReviseGlobalManagedRateRequest request, Long actorUserId) {
    BillingSku sku = request.billingSku();
    if (sku == null || sku != BillingSku.MANAGED_EMAIL_OUTBOUND
        && sku != BillingSku.MANAGED_SMS_SEGMENT) {
      throw new BadRequestException("Unsupported global managed billing SKU");
    }
    BigDecimal amount = request.price();
    if (amount == null || amount.signum() < 0 || amount.scale() > 6) {
      throw new BadRequestException("Price must be a non-negative INR value with at most 6 decimals");
    }
    Instant now = Instant.now();
    Instant from = request.effectiveFrom() == null ? now : request.effectiveFrom();
    if (from.isBefore(now)) {
      throw new BadRequestException("Global price revisions cannot be backdated");
    }
    // One transaction-wide advisory lock for this SKU prevents concurrent overlapping revisions.
    jdbcTemplate.queryForList(
        "select pg_advisory_xact_lock(hashtext('billing_global_managed_rates'), hashtext(?))",
        sku.name());
    List<GlobalManagedRateResponse> history = jdbcTemplate.query(
        COLUMNS + "where billing_sku = ? order by effective_from", (rs, row) -> map(rs), sku.name());
    if (history.stream().anyMatch(rule -> !rule.effectiveFrom().isBefore(from))) {
      throw new ConflictException("A global rate is already scheduled on or after the requested start");
    }
    var current = history.stream()
        .filter(rule -> FinancialEffectiveTime.includes(rule.effectiveFrom(), rule.effectiveTo(), from))
        .toList();
    if (current.size() > 1) {
      throw new ConflictException("Conflicting effective global managed rates");
    }
    if (!current.isEmpty()) {
      var prior = current.getFirst();
      int closed = jdbcTemplate.update(
          """
          update billing_global_managed_rates set effective_to = ?
           where id = ? and effective_from < ?
             and (effective_to is null or effective_to > ?)
          """,
          Timestamp.from(from), Long.valueOf(prior.rateId()), Timestamp.from(from),
          Timestamp.from(from));
      if (closed != 1) throw new ConflictException("Global price changed concurrently");
    }
    Long id = jdbcTemplate.queryForObject(
        """
        insert into billing_global_managed_rates
          (billing_sku, price, currency, enabled, effective_from, created_by_user_id, reason)
        values (?, ?, 'INR', true, ?, ?, ?) returning id
        """,
        Long.class, sku.name(), amount, Timestamp.from(from), actorUserId,
        request.reason() == null ? null : request.reason().trim());
    return new GlobalManagedRateResponse(
        String.valueOf(id), sku, amount, "INR", true, from, null,
        actorUserId == null ? null : actorUserId.toString(),
        request.reason() == null ? null : request.reason().trim());
  }

  private GlobalManagedRateResponse map(java.sql.ResultSet rs) throws java.sql.SQLException {
    long actorId = rs.getLong("created_by_user_id");
    String actor = rs.wasNull() ? null : Long.toString(actorId);
    Timestamp end = rs.getTimestamp("effective_to");
    return new GlobalManagedRateResponse(
        Long.toString(rs.getLong("id")),
        BillingSku.valueOf(rs.getString("billing_sku")),
        rs.getBigDecimal("price"), rs.getString("currency"), rs.getBoolean("enabled"),
        rs.getTimestamp("effective_from").toInstant(),
        end == null ? null : end.toInstant(), actor, rs.getString("reason"));
  }
}
