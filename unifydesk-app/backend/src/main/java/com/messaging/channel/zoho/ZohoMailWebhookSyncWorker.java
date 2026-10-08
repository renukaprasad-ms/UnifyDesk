package com.messaging.channel.zoho;

import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ProviderType;
import jakarta.annotation.PreDestroy;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Durable webhook wakeups. A successful HTTP callback only sets pending_at;
 * worker claims are DB-backed and safe across app replicas/restarts. Events
 * arriving during a claimed poll remain pending for a second pass.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ZohoMailWebhookSyncWorker {

  private final JdbcTemplate jdbc;
  private final ZohoMailPollingScheduler pollingScheduler;
  private final ZohoMailOAuthProperties properties;
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
  private final AtomicInteger inFlight = new AtomicInteger();

  @Scheduled(fixedDelayString = "${app.channel.zoho-mail.webhook-worker-interval:1s}")
  public void dispatch() {
    if (!properties.isEnabled() || !properties.isWebhookEnabled()) return;
    int available = Math.max(0, properties.getWorkerConcurrency() - inFlight.get());
    if (available == 0) return;

    List<Claim> claims = jdbc.query(
        """
        with due as (
          select registration.connection_id
          from zoho_mail_webhook_registrations registration
          join channel_connections connection on connection.id=registration.connection_id
          where registration.pending_at is not null
            and registration.next_attempt_at <= now()
            and (registration.lease_until is null or registration.lease_until < now())
            and connection.status='ACTIVE'
            and connection.provider_type='ZOHO_MAIL'
          order by registration.pending_at
          for update of registration skip locked
          limit ?
        )
        update zoho_mail_webhook_registrations registration
        set lease_until=now() + interval '10 minutes', updated_at=now()
        from due
        where registration.connection_id=due.connection_id
        returning registration.connection_id, registration.last_received_at,
                  registration.lease_until, registration.attempts
        """,
        (rs, row) -> new Claim(
            rs.getLong("connection_id"),
            rs.getTimestamp("last_received_at").toInstant(),
            rs.getTimestamp("lease_until").toInstant(),
            rs.getInt("attempts")),
        available);

    for (Claim claim : claims) {
      inFlight.incrementAndGet();
      executor.submit(() -> {
        try {
          if (pollingScheduler.pollWebhookConnection(claim.connectionId())) {
            finish(claim);
          } else {
            retry(claim);
          }
        } catch (RuntimeException exception) {
          log.warn("zoho_webhook_sync_failed connectionId={} reason={}",
              claim.connectionId(), exception.getClass().getSimpleName());
          retry(claim);
        } finally {
          inFlight.decrementAndGet();
        }
      });
    }
  }

  private void finish(Claim claim) {
    jdbc.update(
        """
        update zoho_mail_webhook_registrations
        set pending_at=case when last_received_at=? then null else pending_at end,
            last_processed_at=now(), next_attempt_at=now(),
            lease_until=null, attempts=0, updated_at=now()
        where connection_id=? and lease_until=?
        """,
        Timestamp.from(claim.receivedAt()),
        claim.connectionId(),
        Timestamp.from(claim.leaseUntil()));
  }

  private void retry(Claim claim) {
    long delay = Math.min(120, 15L << Math.min(3, claim.attempts()));
    jdbc.update(
        """
        update zoho_mail_webhook_registrations
        set lease_until=null, attempts=attempts+1,
            next_attempt_at=?, updated_at=now()
        where connection_id=? and lease_until=?
        """,
        Timestamp.from(Instant.now().plusSeconds(delay)),
        claim.connectionId(),
        Timestamp.from(claim.leaseUntil()));
  }

  @PreDestroy
  void shutdown() {
    executor.shutdown();
  }

  private record Claim(long connectionId, Instant receivedAt, Instant leaseUntil, int attempts) {}
}
