package com.messaging.channel.zoho;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.asyncjob.enums.AsyncJobType;
import com.messaging.asyncjob.service.AsyncJobService;
import com.messaging.asyncjob.service.AsyncJobService.ClaimedAsyncJob;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.company.enums.CompanyStatus;
import com.messaging.company.repository.CompanyRepository;
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
public class ZohoMailSyncWorker {

  private final ZohoMailOAuthProperties properties;
  private final AsyncJobService asyncJobService;
  private final ZohoMailMailboxSyncService mailboxSyncService;
  private final ChannelConnectionRepository connectionRepository;
  private final CompanyRepository companyRepository;
  private final ObjectMapper objectMapper;
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
  private final AtomicInteger inFlight = new AtomicInteger();

  public ZohoMailSyncWorker(
      ZohoMailOAuthProperties properties,
      AsyncJobService asyncJobService,
      ZohoMailMailboxSyncService mailboxSyncService,
      ChannelConnectionRepository connectionRepository,
      CompanyRepository companyRepository,
      ObjectMapper objectMapper) {
    this.properties = properties;
    this.asyncJobService = asyncJobService;
    this.mailboxSyncService = mailboxSyncService;
    this.connectionRepository = connectionRepository;
    this.companyRepository = companyRepository;
    this.objectMapper = objectMapper;
  }

  @Scheduled(fixedDelayString = "${app.channel.zoho-mail.worker-poll-interval-ms:500}")
  public void dispatch() {
    if (!properties.configured()) return;

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
          objectMapper.readValue(
              job.metadataJson(), ZohoMailSyncQueueService.SyncMetadata.class);
      if (metadata.connectionId() == null || metadata.request() == null) {
        throw new IllegalArgumentException("Invalid Zoho Mail sync job metadata");
      }

      if (job.companyId() == null
          || companyRepository
              .findById(job.companyId())
              .map(company -> company.getStatus() != CompanyStatus.ACTIVE)
              .orElse(true)) {
        throw new IllegalStateException("Workspace is not active");
      }

      ChannelConnection connection =
          connectionRepository
              .findByIdAndCompanyId(metadata.connectionId(), job.companyId())
              .orElseThrow(() -> new IllegalStateException("Zoho Mail connection not found"));
      if (connection.getProviderType() != ProviderType.ZOHO_MAIL
          || connection.getStatus() != ChannelConnectionStatus.ACTIVE) {
        throw new IllegalStateException("Zoho Mail connection is not active");
      }

      int target =
          metadata.request().messageCount() == null
              ? properties.getSyncMaxMessages()
              : Math.min(metadata.request().messageCount(), properties.getSyncMaxMessages());
      asyncJobService.start(job.jobId(), target, "Syncing Zoho Mail history");

      var result =
          mailboxSyncService.sync(
              connection,
              metadata.request(),
              (scanned, changed) ->
                  asyncJobService.progress(
                      job.jobId(),
                      scanned,
                      target,
                      "Scanned " + scanned + " Zoho messages; " + changed + " changed"));

      asyncJobService.setTotal(
          job.jobId(), result.scanned(), "Finalizing Zoho Mail sync");
      if (result.scanned() > 0) {
        asyncJobService.progress(
            job.jobId(),
            result.scanned(),
            result.scanned(),
            "Persisted "
                + (result.created() + result.updated())
                + " Zoho messages");
      }
      asyncJobService.complete(job.jobId(), "Zoho Mail sync complete");

      connection.setLastSyncAt(Instant.now());
      connectionRepository.save(connection);
    } catch (Exception exception) {
      log.warn(
          "zoho_mail_sync_failed jobId={} connectionId={} reason={}",
          job.jobId(),
          job.resourceId(),
          exception.getClass().getSimpleName(),
          exception);
      asyncJobService.fail(
          job.jobId(),
          exception.getMessage() == null
              ? "Zoho Mail sync failed"
              : exception.getMessage());
    }
  }

  @PreDestroy
  void shutdown() {
    executor.shutdown();
  }
}
