package com.messaging.channel.imapsmtp;

import com.messaging.channel.email.EmailFolder;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.imapsmtp.ImapSmtpSyncStateService.ClaimedIncremental;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.inbox.dto.InboxEmailChangedEvent;
import com.messaging.realtime.service.RealtimeService;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ImapSmtpIncrementalScheduler {

  private final ImapSmtpProperties properties;
  private final ChannelConnectionRepository connectionRepository;
  private final ImapSmtpConnectionService connectionService;
  private final ImapSmtpMailClient mailClient;
  private final ImapSmtpInboxPersistenceService persistenceService;
  private final ImapSmtpSyncStateService stateService;
  private final RealtimeService realtimeService;
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
  private final AtomicInteger inFlight = new AtomicInteger();

  public ImapSmtpIncrementalScheduler(
      ImapSmtpProperties properties,
      ChannelConnectionRepository connectionRepository,
      ImapSmtpConnectionService connectionService,
      ImapSmtpMailClient mailClient,
      ImapSmtpInboxPersistenceService persistenceService,
      ImapSmtpSyncStateService stateService,
      RealtimeService realtimeService) {
    this.properties = properties;
    this.connectionRepository = connectionRepository;
    this.connectionService = connectionService;
    this.mailClient = mailClient;
    this.persistenceService = persistenceService;
    this.stateService = stateService;
    this.realtimeService = realtimeService;
  }

  @Scheduled(
      fixedDelayString = "${app.channel.imap-smtp.incremental-poll-interval:1m}",
      initialDelayString = "${app.channel.imap-smtp.incremental-initial-delay:1m}")
  public void poll() {
    if (!properties.isEnabled()) return;

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
              .orElseThrow(() -> new IllegalStateException("IMAP connection not found"));

      ImapSmtpStoredCredentials credentials = connectionService.credentials(connection);
      ImapSmtpCursorState cursors = stateService.read(connection.getId());
      int changed = 0;
      Set<Long> changedConversationIds = new LinkedHashSet<>();
      Set<Long> changedMessageIds = new LinkedHashSet<>();

      for (EmailFolder folder : new EmailFolder[] {EmailFolder.INBOX, EmailFolder.SENT}) {
        ImapSmtpCursorState.FolderCursor cursor = cursors.cursor(folder);
        var delta =
            mailClient.incremental(
                credentials,
                folder,
                cursor.uidValidity(),
                cursor.lastUid(),
                properties.getIncrementalMaxMessagesPerFolder());

        if (!delta.messages().isEmpty()) {
          var persisted = persistenceService.persist(connection, delta.messages());
          changed += persisted.created() + persisted.updated();
          changedConversationIds.addAll(persisted.conversationIds());
          changedMessageIds.addAll(persisted.messageIds());
        }
        stateService.advance(connection, folder, delta.uidValidity(), delta.lastUid());
      }

      connection.setLastSyncAt(Instant.now());
      connectionRepository.save(connection);
      stateService.complete(connection.getId());

      if (changed > 0) {
        realtimeService.sendToCompany(
            connection.getCompanyId(),
            "inbox.email.changed",
            InboxEmailChangedEvent.boundedDelta(
                connection.getId(),
                ProviderType.IMAP_SMTP,
                changedConversationIds,
                changedMessageIds,
                Set.of()));
      }
    } catch (RuntimeException exception) {
      log.warn(
          "imap_incremental_sync_failed connectionId={} attempt={} reason={}",
          claim.connectionId(),
          claim.attempt(),
          exception.getClass().getSimpleName());
      try {
        stateService.retry(
            claim.connectionId(),
            claim.attempt(),
            exception.getMessage() == null
                ? "IMAP incremental sync failed"
                : exception.getMessage());
      } catch (RuntimeException retryFailure) {
        log.error(
            "imap_incremental_retry_state_failed connectionId={}",
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
