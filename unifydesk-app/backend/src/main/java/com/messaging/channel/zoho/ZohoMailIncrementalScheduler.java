package com.messaging.channel.zoho;

import com.messaging.channel.email.EmailFolder;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.channel.zoho.ZohoMailSyncStateService.ClaimedIncremental;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ZohoMailIncrementalScheduler {

  private final ZohoMailOAuthProperties properties;
  private final ChannelConnectionRepository connectionRepository;
  private final ZohoMailMailboxSyncService mailboxSyncService;
  private final ZohoMailSyncStateService stateService;
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
  private final AtomicInteger inFlight = new AtomicInteger();

  public ZohoMailIncrementalScheduler(
      ZohoMailOAuthProperties properties,
      ChannelConnectionRepository connectionRepository,
      ZohoMailMailboxSyncService mailboxSyncService,
      ZohoMailSyncStateService stateService) {
    this.properties = properties;
    this.connectionRepository = connectionRepository;
    this.mailboxSyncService = mailboxSyncService;
    this.stateService = stateService;
  }

  @Scheduled(
      fixedDelayString = "${app.channel.zoho-mail.incremental-poll-interval:2m}",
      initialDelayString = "${app.channel.zoho-mail.incremental-initial-delay:1m}")
  public void poll() {
    if (!properties.configured()) return;

    int available = Math.max(1, properties.getWorkerConcurrency()) - inFlight.get();
    if (available <= 0) return;

    List<ClaimedIncremental> claimed = stateService.claim(available);
    for (ClaimedIncremental claim : claimed) {
      inFlight.incrementAndGet();
      executor.submit(
          () -> {
            try {
              syncConnection(claim);
            } finally {
              inFlight.decrementAndGet();
            }
          });
    }
  }

  private void syncConnection(ClaimedIncremental claim) {
    try {
      ChannelConnection connection =
          connectionRepository
              .findByIdAndCompanyId(claim.connectionId(), claim.companyId())
              .orElseThrow(() -> new IllegalStateException("Zoho Mail connection not found"));

      Instant startedAt = Instant.now();
      Instant since =
          (claim.lastIncrementalSyncAt() == null
                  ? connection.getCreatedAt()
                  : claim.lastIncrementalSyncAt())
              .minus(properties.getIncrementalOverlap());

      for (EmailFolder folder : new EmailFolder[] {EmailFolder.INBOX, EmailFolder.SENT}) {
        mailboxSyncService.incremental(
            connection,
            folder,
            since,
            properties.getIncrementalMaxMessagesPerFolder());
      }

      connection.setLastSyncAt(Instant.now());
      connectionRepository.save(connection);
      stateService.complete(connection.getId(), startedAt);
    } catch (RuntimeException exception) {
      log.warn(
          "zoho_mail_incremental_sync_failed connectionId={} attempt={} reason={}",
          claim.connectionId(),
          claim.attempt(),
          exception.getClass().getSimpleName());
      try {
        stateService.retry(
            claim.connectionId(),
            claim.attempt(),
            exception.getMessage() == null
                ? "Zoho Mail incremental sync failed"
                : exception.getMessage());
      } catch (RuntimeException retryFailure) {
        log.error(
            "zoho_mail_incremental_retry_state_failed connectionId={}",
            claim.connectionId(),
            retryFailure);
      }
    }
  }

  @PreDestroy
  void shutdown() {
    executor.shutdown();
  }
}
