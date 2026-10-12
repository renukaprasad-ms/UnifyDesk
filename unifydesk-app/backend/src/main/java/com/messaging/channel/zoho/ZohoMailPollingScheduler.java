package com.messaging.channel.zoho;

import com.messaging.asyncjob.enums.AsyncJobType;
import com.messaging.asyncjob.service.AsyncJobService;
import com.messaging.channel.email.EmailFolder;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.inbox.dto.InboxEmailChangedEvent;
import com.messaging.realtime.service.RealtimeService;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ZohoMailPollingScheduler {

  private final ZohoMailOAuthProperties properties;
  private final ChannelConnectionRepository connectionRepository;
  private final AsyncJobService asyncJobService;
  private final ZohoMailMailboxService mailboxService;
  private final ZohoMailInboxPersistenceService persistenceService;
  private final RealtimeService realtimeService;
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
  private final AtomicInteger inFlight = new AtomicInteger();
  private final Set<Long> activeConnectionIds = ConcurrentHashMap.newKeySet();

  public ZohoMailPollingScheduler(
      ZohoMailOAuthProperties properties,
      ChannelConnectionRepository connectionRepository,
      AsyncJobService asyncJobService,
      ZohoMailMailboxService mailboxService,
      ZohoMailInboxPersistenceService persistenceService,
      RealtimeService realtimeService) {
    this.properties = properties;
    this.connectionRepository = connectionRepository;
    this.asyncJobService = asyncJobService;
    this.mailboxService = mailboxService;
    this.persistenceService = persistenceService;
    this.realtimeService = realtimeService;
  }

  @Scheduled(
      fixedDelayString = "${app.channel.zoho-mail.poll-interval:2m}",
      initialDelayString = "${app.channel.zoho-mail.poll-initial-delay:1m}")
  public void poll() {
    if (!properties.isEnabled()) return;

    int available = Math.max(1, properties.getWorkerConcurrency()) - inFlight.get();
    if (available <= 0) return;

    List<ChannelConnection> connections =
        connectionRepository.findByProviderTypeAndStatus(
            ProviderType.ZOHO_MAIL, ChannelConnectionStatus.ACTIVE);

    for (ChannelConnection connection : connections) {
      if (available-- <= 0) break;
      if (asyncJobService
          .findActive(
              connection.getCompanyId(),
              AsyncJobType.ZOHO_MAIL_SYNC,
              connection.getId().toString())
          .isPresent()) {
        continue;
      }

      if (!activeConnectionIds.add(connection.getId())) continue;

      inFlight.incrementAndGet();
      executor.submit(
          () -> {
            try {
              sync(connection);
            } finally {
              activeConnectionIds.remove(connection.getId());
              inFlight.decrementAndGet();
            }
          });
    }
  }

  /**
   * Called only by the durable webhook worker. Uses the same watermark and
   * per-process mailbox lock as safety polling, preventing overlapping work.
   */
  public boolean pollWebhookConnection(Long connectionId) {
    if (!properties.isEnabled()
        || inFlight.get() >= Math.max(1, properties.getWorkerConcurrency())) return false;
    ChannelConnection connection = connectionRepository.findById(connectionId).orElse(null);
    if (connection == null
        || connection.getProviderType() != ProviderType.ZOHO_MAIL
        || connection.getStatus() != ChannelConnectionStatus.ACTIVE) return false;
    if (asyncJobService.findActive(
            connection.getCompanyId(), AsyncJobType.ZOHO_MAIL_SYNC,
            connectionId.toString()).isPresent()) return false;
    if (!activeConnectionIds.add(connectionId)) return false;
    inFlight.incrementAndGet();
    try {
      return sync(connection);
    } finally {
      activeConnectionIds.remove(connectionId);
      inFlight.decrementAndGet();
    }
  }

  private boolean sync(ChannelConnection connection) {
    Instant baseline =
        (connection.getLastSyncAt() == null ? connection.getCreatedAt() : connection.getLastSyncAt())
            .minus(2, ChronoUnit.MINUTES);
    Set<Long> conversations = new LinkedHashSet<>();
    Set<Long> messages = new LinkedHashSet<>();
    boolean complete = true;

    try {
      for (EmailFolder folder : new EmailFolder[] {EmailFolder.INBOX, EmailFolder.SENT}) {
        ZohoMailMailboxService.IncrementalResult incremental =
            mailboxService.incremental(connection, folder, baseline);
        complete &= incremental.complete();
        List<ZohoMailMessageSnapshot> snapshots = incremental.messages();
        if (snapshots.isEmpty()) continue;

        var persisted = persistenceService.persist(connection, snapshots);
        conversations.addAll(persisted.conversationIds());
        messages.addAll(persisted.messageIds());
      }

      if (complete) {
        connection.setLastSyncAt(Instant.now());
        connectionRepository.save(connection);
      } else {
        log.warn(
            "zoho_mail_poll_page_cap_reached connectionId={} watermark={} - live watermark not advanced",
            connection.getId(),
            baseline);
      }

      if (!conversations.isEmpty() || !messages.isEmpty()) {
        realtimeService.sendToCompany(
            connection.getCompanyId(),
            "inbox.email.changed",
            InboxEmailChangedEvent.boundedDelta(
                connection.getId(), ProviderType.ZOHO_MAIL, conversations, messages, Set.of()));
      }
      return complete;
    } catch (RuntimeException exception) {
      log.warn(
          "zoho_mail_poll_failed connectionId={} reason={}",
          connection.getId(),
          exception.getClass().getSimpleName());
      return false;
    }
  }

  @PreDestroy
  void shutdown() {
    executor.shutdown();
  }
}
