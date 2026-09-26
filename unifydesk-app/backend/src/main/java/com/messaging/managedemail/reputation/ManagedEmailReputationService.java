package com.messaging.managedemail.reputation;

import com.messaging.common.id.IdGenerator;
import com.messaging.managedemail.runtime.ManagedEmailRuntimeProperties;
import com.messaging.managedemail.runtime.ManagedEmailRuntimeRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManagedEmailReputationService {

  private final JdbcTemplate jdbcTemplate;
  private final ManagedEmailRuntimeRepository runtimeRepository;
  private final ManagedEmailRuntimeProperties properties;

  @Transactional
  public ManagedEmailDeliverabilityResponse evaluate(Long companyId) {
    ManagedEmailDeliverabilityResponse response = calculate(companyId);
    ManagedEmailRuntimeRepository.ManagedTenantRuntime tenant =
        runtimeRepository.findTenantRuntime(companyId).orElse(null);

    if (response.status() == ManagedEmailDeliverabilityResponse.Status.SUSPENDED
        && tenant != null) {
      runtimeRepository.suspendTenant(
          companyId, tenant.tenantId(), "Automated Managed Email reputation safety policy");
    }

    if (tenant != null) {
      jdbcTemplate.update(
          """
          insert into managed_email_reputation_snapshots
            (id, company_id, tenant_id, window_start, window_end, accepted_count,
             hard_bounce_count, complaint_count, hard_bounce_rate, complaint_rate,
             provider_reputation_status, decision, created_at)
          values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
          """,
          IdGenerator.nextId(),
          companyId,
          tenant.tenantId(),
          Timestamp.from(response.windowStart()),
          Timestamp.from(response.windowEnd()),
          response.accepted(),
          response.hardBounces(),
          response.complaints(),
          response.hardBounceRate(),
          response.complaintRate(),
          tenant.reputationStatus(),
          response.status().name());
    }
    return response;
  }

  @Transactional(readOnly = true)
  public ManagedEmailDeliverabilityResponse current(Long companyId) {
    return calculate(companyId);
  }

  @Transactional(readOnly = true)
  public double rateMultiplier(Long companyId) {
    List<String> decisions =
        jdbcTemplate.queryForList(
            """
            select decision
              from managed_email_reputation_snapshots
             where company_id = ?
             order by window_end desc
             limit 1
            """,
            String.class,
            companyId);
    if (decisions.isEmpty()) return 1.0;
    return switch (decisions.getFirst()) {
      case "SUSPENDED" -> 0.0;
      case "THROTTLED" ->
          properties.getThrottleRateMultiplier() == null
              ? 0.0
              : properties.getThrottleRateMultiplier().doubleValue();
      default -> 1.0;
    };
  }

  private ManagedEmailDeliverabilityResponse calculate(Long companyId) {
    Instant end = Instant.now();
    Instant start = end.minusSeconds(Math.max(1, properties.getReputationWindowHours()) * 3600L);
    long accepted = accepted(companyId, start, end);
    long hardBounces = events(companyId, "BOUNCE", true, start, end);
    long complaints = events(companyId, "COMPLAINT", false, start, end);
    BigDecimal bounceRate = rate(hardBounces, accepted);
    BigDecimal complaintRate = rate(complaints, accepted);
    ManagedEmailRuntimeRepository.ManagedTenantRuntime tenant =
        runtimeRepository.findTenantRuntime(companyId).orElse(null);
    ManagedEmailDeliverabilityResponse.Status status =
        decide(accepted, bounceRate, complaintRate, tenant);
    boolean restricted =
        status == ManagedEmailDeliverabilityResponse.Status.THROTTLED
            || status == ManagedEmailDeliverabilityResponse.Status.SUSPENDED
            || (tenant != null && !tenant.isSendingEnabled());
    return new ManagedEmailDeliverabilityResponse(
        status,
        start,
        end,
        accepted,
        hardBounces,
        complaints,
        bounceRate,
        complaintRate,
        restricted,
        notice(status));
  }

  private long accepted(Long companyId, Instant start, Instant end) {
    Long value =
        jdbcTemplate.queryForObject(
            """
            select count(*)
              from managed_email_usage_events
             where company_id = ?
               and event_type = 'COMMIT'
               and created_at >= ?
               and created_at < ?
            """,
            Long.class,
            companyId,
            Timestamp.from(start),
            Timestamp.from(end));
    return value == null ? 0 : value;
  }

  private long events(
      Long companyId, String type, boolean hardOnly, Instant start, Instant end) {
    String sql =
        """
        select count(*)
          from managed_email_provider_events
         where company_id = ?
           and event_type = ?
           and correlation_status = 'MATCHED'
           and event_timestamp >= ?
           and event_timestamp < ?
        """
            + (hardOnly ? " and upper(coalesce(bounce_type, '')) = 'PERMANENT'" : "");
    Long value =
        jdbcTemplate.queryForObject(
            sql,
            Long.class,
            companyId,
            type,
            Timestamp.from(start),
            Timestamp.from(end));
    return value == null ? 0 : value;
  }

  private ManagedEmailDeliverabilityResponse.Status decide(
      long accepted,
      BigDecimal bounceRate,
      BigDecimal complaintRate,
      ManagedEmailRuntimeRepository.ManagedTenantRuntime tenant) {
    if (tenant != null && !tenant.isSendingEnabled()) {
      return ManagedEmailDeliverabilityResponse.Status.SUSPENDED;
    }
    if (!properties.reputationPolicyConfigured()
        || accepted < properties.getMinimumReputationSampleSize()) {
      return ManagedEmailDeliverabilityResponse.Status.INSUFFICIENT_DATA;
    }
    if (atOrAbove(bounceRate, properties.getSuspensionHardBounceRate())
        || atOrAbove(complaintRate, properties.getSuspensionComplaintRate())) {
      return ManagedEmailDeliverabilityResponse.Status.SUSPENDED;
    }
    if (atOrAbove(bounceRate, properties.getThrottleHardBounceRate())
        || atOrAbove(complaintRate, properties.getThrottleComplaintRate())) {
      return ManagedEmailDeliverabilityResponse.Status.THROTTLED;
    }
    if (atOrAbove(bounceRate, properties.getWarningHardBounceRate())
        || atOrAbove(complaintRate, properties.getWarningComplaintRate())) {
      return ManagedEmailDeliverabilityResponse.Status.WARNING;
    }
    return ManagedEmailDeliverabilityResponse.Status.HEALTHY;
  }

  private boolean atOrAbove(BigDecimal value, BigDecimal threshold) {
    return threshold != null && value.compareTo(threshold) >= 0;
  }

  private BigDecimal rate(long numerator, long denominator) {
    if (denominator <= 0) return BigDecimal.ZERO.setScale(8);
    return BigDecimal.valueOf(numerator)
        .divide(BigDecimal.valueOf(denominator), 8, RoundingMode.HALF_UP);
  }

  private String notice(ManagedEmailDeliverabilityResponse.Status status) {
    return switch (status) {
      case INSUFFICIENT_DATA -> "More sending history is needed to calculate deliverability.";
      case HEALTHY -> null;
      case WARNING -> "Deliverability signals need attention.";
      case THROTTLED -> "Managed Email sending is temporarily throttled for safety.";
      case SUSPENDED -> "Managed Email sending is suspended for safety.";
    };
  }
}
