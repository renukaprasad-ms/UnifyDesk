package com.messaging.channel.imapsmtp;

import com.messaging.channel.email.EmailFolder;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.company.enums.CompanyStatus;
import com.messaging.company.repository.CompanyRepository;
import com.messaging.realtime.service.RealtimeService;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.Map;
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
  private final CompanyRepository companyRepository;
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
      CompanyRepository companyRepository,
      ImapSmtpConnectionService connectionService,
      ImapSmtpMailClient mailClient,
      ImapSmtpInboxPersistenceService persistenceService,
      ImapSmtpSyncStateService stateService,
      RealtimeService realtimeService) {
    this.properties = properties;
    this.connectionRepository = connectionRepository;
    this.companyRepository = companyRepository;
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

    var connections =
        connectionRepository.findByProviderTypeAndStatus(
            ProviderType.IMAP_SMTP, ChannelConnectionStatus.ACTIVE);
    int available = Math.max(1, properties.getWorkerConcurrency()) - inFlight.get();

    for (ChannelConnection connection : connections) {
      if (available-- <= 0) break;
      inFlight.incrementAndGet();
      executor.submit(
          () -> {
            try {
              syncConnection(connection);
            } finally {
              inFlight.decrementAndGet();
            }
          });
    }
  }

  private void syncConnection(ChannelConnection connection) {
    try {
      boolean activeCompany =
          companyRepository
              .findById(connection.getCompanyId())
              .map(company -> company.getStatus() == CompanyStatus.ACTIVE)
              .orElse(false);
      if (!activeCompany) return;

      ImapSmtpStoredCredentials credentials = connectionService.credentials(connection);
      ImapSmtpCursorState cursors = stateService.read(connection.getId());
      int changed = 0;

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
        }
        stateService.advance(connection, folder, delta.uidValidity(), delta.lastUid());
      }

      connection.setLastSyncAt(Instant.now());
      connectionRepository.save(connection);

      if (changed > 0) {
        realtimeService.sendToCompany(
            connection.getCompanyId(),
            "inbox.email.changed",
            Map.of(
                "connectionId", connection.getId().toString(),
                "provider", "IMAP_SMTP",
                "changed", changed));
      }
    } catch (RuntimeException exception) {
      log.warn(
          "imap_incremental_sync_failed connectionId={} account={} reason={}",
          connection.getId(),
          connection.getExternalAccountId(),
          exception.getClass().getSimpleName());
    }
  }

  @PreDestroy
  void shutdown() {
    executor.shutdown();
  }
}
