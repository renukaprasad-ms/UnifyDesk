package com.messaging.channel.zoho;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.asyncjob.enums.AsyncJobType;
import com.messaging.asyncjob.service.AsyncJobService;
import com.messaging.asyncjob.service.AsyncJobService.ClaimedAsyncJob;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.company.enums.CompanyStatus;
import com.messaging.company.repository.CompanyRepository;
import com.messaging.inbox.dto.InboxEmailChangedEvent;
import com.messaging.realtime.service.RealtimeService;
import jakarta.annotation.PreDestroy;
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
public class ZohoMailSyncWorker {

  private final ZohoMailOAuthProperties properties;
  private final AsyncJobService asyncJobService;
  private final ZohoMailConnectionLookup connectionLookup;
  private final ZohoMailMailboxService mailboxService;
  private final ZohoMailInboxPersistenceService persistenceService;
  private final CompanyRepository companyRepository;
  private final RealtimeService realtimeService;
  private final ObjectMapper objectMapper;
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
  private final AtomicInteger inFlight = new AtomicInteger();

  public ZohoMailSyncWorker(
      ZohoMailOAuthProperties properties,
      AsyncJobService asyncJobService,
      ZohoMailConnectionLookup connectionLookup,
      ZohoMailMailboxService mailboxService,
      ZohoMailInboxPersistenceService persistenceService,
      CompanyRepository companyRepository,
      RealtimeService realtimeService,
      ObjectMapper objectMapper) {
    this.properties = properties;
    this.asyncJobService = asyncJobService;
    this.connectionLookup = connectionLookup;
    this.mailboxService = mailboxService;
    this.persistenceService = persistenceService;
    this.companyRepository = companyRepository;
    this.realtimeService = realtimeService;
    this.objectMapper = objectMapper;
  }

  @Scheduled(fixedDelayString = "${app.channel.zoho-mail.worker-poll-interval-ms:500}")
  public void dispatch() {
    if (!properties.isEnabled()) return;

    int available = Math.max(1, properties.getWorkerConcurrency()) - inFlight.get();
    if (available <= 0) return;

    List<ClaimedAsyncJob> jobs =
        asyncJobService.claim(
            AsyncJobType.ZOHO_MAIL_SYNC, available, properties.getStaleJobAfter());

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
      ZohoMailSyncQueueService.SyncMetadata metadata =
          objectMapper.readValue(job.metadataJson(), ZohoMailSyncQueueService.SyncMetadata.class);
      if (metadata.connectionId() == null || metadata.request() == null) {
        throw new IllegalArgumentException("Invalid Zoho Mail sync metadata");
      }
      if (job.companyId() == null
          || companyRepository
              .findById(job.companyId())
              .map(company -> company.getStatus() != CompanyStatus.ACTIVE)
              .orElse(true)) {
        throw new IllegalStateException("Workspace is not active");
      }

      ChannelConnection connection =
          connectionLookup.requireActive(job.companyId(), metadata.connectionId());
      List<ZohoMailMessageSnapshot> snapshots =
          mailboxService.history(connection, metadata.request());

      asyncJobService.setTotal(job.jobId(), snapshots.size(), "Importing Zoho Mail history");
      var persisted = persistenceService.persist(connection, snapshots);
      asyncJobService.progress(
          job.jobId(),
          snapshots.size(),
          snapshots.size(),
          "Imported " + (persisted.created() + persisted.updated()) + " Zoho Mail messages");
      asyncJobService.complete(job.jobId(), "Zoho Mail sync complete");

      // Historical/manual sync is independent of the live polling watermark. Advancing
      // lastSyncAt here could skip mail that arrived while a long backfill was running.
      if (!persisted.conversationIds().isEmpty() || !persisted.messageIds().isEmpty()) {
        realtimeService.sendToCompany(
            connection.getCompanyId(),
            "inbox.email.changed",
            InboxEmailChangedEvent.boundedDelta(
                connection.getId(),
                com.messaging.channel.enums.ProviderType.ZOHO_MAIL,
                persisted.conversationIds(),
                persisted.messageIds(),
                Set.of()));
      }
    } catch (Exception exception) {
      log.warn(
          "zoho_mail_sync_failed jobId={} connectionId={} reason={}",
          job.jobId(),
          job.resourceId(),
          exception.getClass().getSimpleName(),
          exception);
      asyncJobService.fail(
          job.jobId(),
          exception.getMessage() == null ? "Zoho Mail sync failed" : exception.getMessage());
    }
  }

  @PreDestroy
  void shutdown() {
    executor.shutdown();
  }
}
