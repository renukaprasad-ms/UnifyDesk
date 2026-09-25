package com.messaging.channel.microsoft;

import com.messaging.asyncjob.service.AsyncJobService;
import com.messaging.channel.email.EmailSyncMode;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.microsoft.MicrosoftApiClient.MessagePage;
import com.messaging.channel.microsoft.MicrosoftBackfillStateService.ClaimedBackfill;
import com.messaging.channel.microsoft.MicrosoftBackfillStateService.Completion;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.billing.exception.PlanEntitlementException;
import com.messaging.common.exception.BadRequestException;
import com.messaging.company.enums.CompanyStatus;
import com.messaging.company.repository.CompanyRepository;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MicrosoftBackfillWorker {

  private final MicrosoftOAuthProperties properties;
  private final MicrosoftBackfillStateService stateService;
  private final MicrosoftTokenService tokenService;
  private final MicrosoftApiClient apiClient;
  private final MicrosoftInboxPersistenceService persistenceService;
  private final ChannelConnectionRepository connectionRepository;
  private final CompanyRepository companyRepository;
  private final AsyncJobService asyncJobService;
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
  private final AtomicInteger inFlight = new AtomicInteger();

  public MicrosoftBackfillWorker(
      MicrosoftOAuthProperties properties,
      MicrosoftBackfillStateService stateService,
      MicrosoftTokenService tokenService,
      MicrosoftApiClient apiClient,
      MicrosoftInboxPersistenceService persistenceService,
      ChannelConnectionRepository connectionRepository,
      CompanyRepository companyRepository,
      AsyncJobService asyncJobService) {
    this.properties = properties;
    this.stateService = stateService;
    this.tokenService = tokenService;
    this.apiClient = apiClient;
    this.persistenceService = persistenceService;
    this.connectionRepository = connectionRepository;
    this.companyRepository = companyRepository;
    this.asyncJobService = asyncJobService;
  }

  @Scheduled(fixedDelayString = "${app.channel.microsoft.backfill-poll-interval-ms:500}")
  public void dispatch() {
    stateService.recoverStale();

    int available = Math.max(1, properties.getBackfillWorkerConcurrency()) - inFlight.get();
    if (available <= 0) return;

    List<ClaimedBackfill> claimed = stateService.claim(available);
    for (ClaimedBackfill sync : claimed) {
      inFlight.incrementAndGet();
      executor.submit(
          () -> {
            try {
              process(sync);
            } finally {
              inFlight.decrementAndGet();
            }
          });
    }
  }

  void process(ClaimedBackfill sync) {
    try {
      ChannelConnection connection =
          connectionRepository
              .findByIdAndCompanyId(sync.connectionId(), sync.companyId())
              .orElseThrow(() -> new BadRequestException("Microsoft connection not found"));

      boolean activeCompany =
          companyRepository
              .findById(sync.companyId())
              .map(company -> company.getStatus() == CompanyStatus.ACTIVE)
              .orElse(false);

      if (!activeCompany
          || connection.getProviderType() != ProviderType.MICROSOFT
          || connection.getStatus() != ChannelConnectionStatus.ACTIVE) {
        fail(sync, "Workspace or Microsoft connection is inactive");
        return;
      }

      int remaining =
          Math.max(0, sync.messageLimit() - Math.toIntExact(Math.min(sync.processedCount(), Integer.MAX_VALUE)));
      if (remaining <= 0) {
        stateService.completePage(sync.jobId(), 0, null);
        asyncJobService.complete(sync.jobId(), "Microsoft email sync complete");
        return;
      }

      long totalForProgress =
          sync.mode() == EmailSyncMode.LATEST_MESSAGES ? sync.messageLimit() : 0;
      asyncJobService.startOrResume(
          sync.jobId(),
          totalForProgress,
          "Syncing " + sync.folder().name().toLowerCase() + " from Microsoft");

      String token = tokenService.accessToken(connection);
      int pageSize = Math.min(Math.min(remaining, 200), Math.max(1, properties.getDeltaPageSize()));
      MessagePage page =
          apiClient.listMessages(
              token,
              sync.folder(),
              sync.mode(),
              sync.fromDate(),
              sync.toDate(),
              pageSize,
              sync.nextUrl());

      List<MicrosoftApiClient.GraphMessage> messages =
          page.value() == null ? List.of() : page.value();
      if (messages.size() > remaining) {
        messages = messages.subList(0, remaining);
      }

      persistenceService.persistPage(
          connection, sync.folder().microsoftFolderKey(), messages);

      Completion completion =
          stateService.completePage(sync.jobId(), messages.size(), page.nextLink());

      if (sync.mode() == EmailSyncMode.LATEST_MESSAGES) {
        asyncJobService.progress(
            sync.jobId(),
            completion.processedCount(),
            completion.messageLimit(),
            "Synced " + completion.processedCount() + " Microsoft messages");
      } else {
        asyncJobService.incrementProgress(
            sync.jobId(),
            messages.size(),
            "Synced " + completion.processedCount() + " Microsoft messages");
      }

      if (completion.completed()) {
        boolean capped =
            sync.mode() == EmailSyncMode.DATE_RANGE
                && completion.reachedLimit()
                && page.nextLink() != null
                && !page.nextLink().isBlank();
        asyncJobService.complete(
            sync.jobId(),
            capped
                ? "Microsoft sync reached the safety limit; use a narrower date range for older mail"
                : "Microsoft email sync complete");
      }

      connection.setLastSyncAt(java.time.Instant.now());
      connectionRepository.save(connection);
    } catch (BadRequestException | PlanEntitlementException exception) {
      fail(sync, safeMessage(exception));
    } catch (RuntimeException exception) {
      if (sync.attempt() >= Math.max(1, properties.getBackfillMaxAttempts())) {
        fail(sync, safeMessage(exception));
        return;
      }

      log.warn(
          "Microsoft history sync retrying connectionId={} folder={} jobId={} attempt={} reason={}",
          sync.connectionId(),
          sync.folder(),
          sync.jobId(),
          sync.attempt(),
          exception.getClass().getSimpleName());
      stateService.retry(sync.jobId(), safeMessage(exception));
    }
  }

  private void fail(ClaimedBackfill sync, String reason) {
    stateService.fail(sync.jobId(), reason);
    asyncJobService.fail(sync.jobId(), reason);
    log.warn(
        "Microsoft history sync failed connectionId={} folder={} jobId={} reason={}",
        sync.connectionId(),
        sync.folder(),
        sync.jobId(),
        reason);
  }

  private String safeMessage(Throwable error) {
    String message = error.getMessage();
    return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
  }

  @PreDestroy
  void shutdown() {
    executor.shutdown();
  }
}
