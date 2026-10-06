package com.messaging.managedemail.delivery;

import com.messaging.billing.service.ManagedFinancialExposureGuard;
import com.messaging.managedemail.metering.ManagedEmailUsageService;
import com.messaging.managedemail.runtime.ManagedEmailRuntimeProperties;
import com.messaging.managedemail.runtime.ManagedEmailRuntimeRepository;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManagedEmailProviderBudgetService {

  private final JdbcTemplate jdbcTemplate;
  private final ManagedEmailUsageService usageService;
  private final ManagedEmailRuntimeProperties properties;
  private final ManagedFinancialExposureGuard financialExposureGuard;

  public boolean hasUsableProviderSnapshot(
      ManagedEmailRuntimeRepository.ManagedSenderRuntime runtime) {
    if (runtime == null
        || runtime.providerMaxSendRate() == null
        || runtime.providerMax24HourSend() == null
        || runtime.providerSentLast24Hours() == null
        || runtime.providerLastValidatedAt() == null
        || runtime.providerMaxSendRate() <= 0
        || runtime.providerMax24HourSend() <= 0
        || runtime.providerSentLast24Hours() < 0
        || runtime.providerSentLast24Hours() > runtime.providerMax24HourSend()) {
      return false;
    }
    int maxAgeSeconds = properties.getProviderSnapshotMaxAgeSeconds();
    if (maxAgeSeconds <= 0) return false;
    Duration age = Duration.between(runtime.providerLastValidatedAt(), Instant.now());
    return !age.isNegative() && age.compareTo(Duration.ofSeconds(maxAgeSeconds)) <= 0;
  }

  @Transactional
  public boolean reserveForSend(
      ManagedEmailRuntimeRepository.ManagedSenderRuntime runtime,
      Long recipientId,
      Long runId,
      int attemptNo) {
    financialExposureGuard.assertAllowed(runtime == null ? null : runtime.companyId());
    if (!hasUsableProviderSnapshot(runtime)) {
      return false;
    }

    lockAccount(runtime.region());

    long localUsageSinceSnapshot =
        committedSince(runtime.region(), runtime.providerLastValidatedAt())
            + outstandingReservations(runtime.region());
    double remaining =
        runtime.providerMax24HourSend()
            - runtime.providerSentLast24Hours()
            - localUsageSinceSnapshot;
    if (remaining < 1.0d) {
      return false;
    }

    usageService.reserve(runtime.companyId(), recipientId, runId, attemptNo);
    return true;
  }

  private void lockAccount(String region) {
    long lockKey = Integer.toUnsignedLong(("managed-email-provider-budget:" + region).hashCode());
    jdbcTemplate.execute(
        (ConnectionCallback<Void>)
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement("select pg_advisory_xact_lock(?)")) {
                statement.setLong(1, lockKey);
                statement.execute();
              }
              return null;
            });
  }

  private long committedSince(String region, Instant snapshotAt) {
    Long count =
        jdbcTemplate.queryForObject(
            """
            select count(*)
              from managed_email_usage_events usage
              join broadcast_recipients recipient on recipient.id = usage.recipient_id
              join broadcast_senders sender on sender.id = recipient.sender_id
              join broadcast_sender_connections connection on connection.id = sender.connection_id
             where usage.event_type = 'COMMIT'
               and usage.created_at >= ?
               and connection.management_mode = 'MESSAGING_MANAGED'
               and connection.provider = 'AMAZON_SES'
               and connection.region = ?
            """,
            Long.class,
            Timestamp.from(snapshotAt),
            region);
    return count == null ? 0 : count;
  }

  private long outstandingReservations(String region) {
    Long count =
        jdbcTemplate.queryForObject(
            """
            select count(*)
              from managed_email_usage_events reserve_event
              join broadcast_recipients recipient on recipient.id = reserve_event.recipient_id
              join broadcast_senders sender on sender.id = recipient.sender_id
              join broadcast_sender_connections connection on connection.id = sender.connection_id
             where reserve_event.event_type = 'RESERVE'
               and connection.management_mode = 'MESSAGING_MANAGED'
               and connection.provider = 'AMAZON_SES'
               and connection.region = ?
               and not exists (
                 select 1
                   from managed_email_usage_events committed
                  where committed.recipient_id = reserve_event.recipient_id
                    and committed.event_type = 'COMMIT'
               )
               and not exists (
                 select 1
                   from managed_email_usage_events released
                  where released.idempotency_key =
                        replace(reserve_event.idempotency_key, ':reserve:', ':release:')
               )
            """,
            Long.class,
            region);
    return count == null ? 0 : count;
  }
}
