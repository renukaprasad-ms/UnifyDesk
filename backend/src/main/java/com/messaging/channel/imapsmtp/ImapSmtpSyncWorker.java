package com.messaging.channel.imapsmtp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.asyncjob.enums.AsyncJobType;
import com.messaging.asyncjob.service.AsyncJobService;
import com.messaging.asyncjob.service.AsyncJobService.ClaimedAsyncJob;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.company.enums.CompanyStatus;
import com.messaging.company.repository.CompanyRepository;
import com.messaging.channel.enums.ProviderType;
import com.messaging.inbox.dto.InboxEmailChangedEvent;
import com.messaging.realtime.service.RealtimeService;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ImapSmtpSyncWorker {

  private final ImapSmtpProperties properties;
  private final AsyncJobService asyncJobService;
  private final ImapSmtpConnectionService connectionService;
  private final ImapSmtpMailClient mailClient;
  private final ImapSmtpInboxPersistenceService persistenceService;
  private final ChannelConnectionRepository connectionRepository;
  private final CompanyRepository companyRepository;
  private final RealtimeService realtimeService;
  private final ObjectMapper objectMapper;
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
  private final AtomicInteger inFlight = new AtomicInteger();

  public ImapSmtpSyncWorker(
      ImapSmtpProperties properties,
      AsyncJobService asyncJobService,
      ImapSmtpConnectionService connectionService,
      ImapSmtpMailClient mailClient,
      ImapSmtpInboxPersistenceService persistenceService,
      ChannelConnectionRepository connectionRepository,
      CompanyRepository companyRepository,
      RealtimeService realtimeService,
      ObjectMapper objectMapper) {
    this.properties = properties;
    this.asyncJobService = asyncJobService;
    this.connectionService = connectionService;
    this.mailClient = mailClient;
    this.persistenceService = persistenceService;
    this.connectionRepository = connectionRepository;
    this.companyRepository = companyRepository;
    this.realtimeService = realtimeService;
    this.objectMapper = objectMapper;
  }

  @Scheduled(fixedDelayString = "${app.channel.imap-smtp.worker-poll-interval-ms:500}")
  public void dispatch() {
    if (!properties.isEnabled()) return;

    int available = Math.max(1, properties.getWorkerConcurrency()) - inFlight.get();
    if (available <= 0) return;

    List<ClaimedAsyncJob> jobs =
        asyncJobService.claim(
            AsyncJobType.IMAP_SMTP_SYNC, available, properties.getStaleJobAfter());
    for (ClaimedAsyncJob job : jobs) {
      inFlight.incrementAndGet();
      executor.submit(
          () -> {
            try {
              process(job);
            } finally {
              inFlight.decrementAndGet();
            }
          });
    }
  }

  private void process(ClaimedAsyncJob job) {
    try {
      ImapSmtpSyncQueueService.SyncMetadata metadata =
          objectMapper.readValue(
              job.metadataJson(), ImapSmtpSyncQueueService.SyncMetadata.class);

      Long connectionId = metadata.connectionId();
      if (connectionId == null || metadata.request() == null) {
        throw new IllegalArgumentException("Invalid IMAP sync job metadata");
      }
      if (job.companyId() == null
          || companyRepository
              .findById(job.companyId())
              .map(company -> company.getStatus() != CompanyStatus.ACTIVE)
              .orElse(true)) {
        throw new IllegalStateException("Workspace is not active");
      }

      ChannelConnection connection =
          connectionService.requireActive(job.companyId(), connectionId);
      ImapSmtpStoredCredentials credentials = connectionService.credentials(connection);
      AtomicBoolean totalSet = new AtomicBoolean(false);
      AtomicInteger changed = new AtomicInteger();
      Set<Long> changedConversationIds = new LinkedHashSet<>();
      Set<Long> changedMessageIds = new LinkedHashSet<>();

      int processed =
          mailClient.syncHistory(
              credentials,
              metadata.request(),
              properties.getSyncMaxMessages(),
              25,
              (batch, total) -> {
                if (totalSet.compareAndSet(false, true)) {
                  asyncJobService.setTotal(
                      job.jobId(), total, "Syncing IMAP mailbox history");
                }
                var persisted = persistenceService.persist(connection, batch);
                changed.addAndGet(persisted.created() + persisted.updated());
                changedConversationIds.addAll(persisted.conversationIds());
                changedMessageIds.addAll(persisted.messageIds());
                asyncJobService.incrementProgress(
                    job.jobId(),
                    batch.size(),
                    "Synced "
                        + (persisted.created() + persisted.updated())
                        + " IMAP messages");
              });

      if (!totalSet.get()) {
        asyncJobService.setTotal(job.jobId(), 0, "No IMAP messages matched this sync");
      }
      asyncJobService.complete(job.jobId(), "IMAP email sync complete");

      connection.setLastSyncAt(Instant.now());
      connectionRepository.save(connection);
      if (processed > 0 || changed.get() > 0) {
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
    } catch (Exception exception) {
      log.warn(
          "imap_sync_failed jobId={} connectionId={} reason={}",
          job.jobId(),
          job.resourceId(),
          exception.getClass().getSimpleName(),
          exception);
      asyncJobService.fail(
          job.jobId(),
          exception.getMessage() == null
              ? "IMAP sync failed"
              : exception.getMessage());
    }
  }

  @PreDestroy
  void shutdown() {
    executor.shutdown();
  }
}
