package com.messaging.channel.microsoft;

import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.microsoft.MicrosoftApiClient.DeltaCursorExpiredException;
import com.messaging.channel.microsoft.MicrosoftApiClient.DeltaResponse;
import com.messaging.channel.microsoft.MicrosoftSyncStateService.ClaimedSync;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.company.enums.CompanyStatus;
import com.messaging.company.repository.CompanyRepository;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MicrosoftSyncWorker {

  private final MicrosoftOAuthProperties properties;
  private final MicrosoftSyncStateService stateService;
  private final MicrosoftTokenService tokenService;
  private final MicrosoftApiClient apiClient;
  private final MicrosoftInboxPersistenceService persistenceService;
  private final ChannelConnectionRepository connectionRepository;
  private final CompanyRepository companyRepository;
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
  private final AtomicInteger inFlight = new AtomicInteger();

  public MicrosoftSyncWorker(
      MicrosoftOAuthProperties properties,
      MicrosoftSyncStateService stateService,
      MicrosoftTokenService tokenService,
      MicrosoftApiClient apiClient,
      MicrosoftInboxPersistenceService persistenceService,
      ChannelConnectionRepository connectionRepository,
      CompanyRepository companyRepository) {
    this.properties = properties;
    this.stateService = stateService;
    this.tokenService = tokenService;
    this.apiClient = apiClient;
    this.persistenceService = persistenceService;
    this.connectionRepository = connectionRepository;
    this.companyRepository = companyRepository;
  }

  @Scheduled(fixedDelayString = "${app.channel.microsoft.worker-poll-interval-ms:500}")
  public void dispatch() {
    stateService.recoverStale();

    int available = Math.max(1, properties.getWorkerConcurrency()) - inFlight.get();
    if (available <= 0) return;

    List<ClaimedSync> claimed = stateService.claim(available);
    for (ClaimedSync sync : claimed) {
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

  void process(ClaimedSync sync) {
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
        stateService.retry(sync.stateId(), "Workspace or Microsoft connection is inactive");
        return;
      }

      String token = tokenService.accessToken(connection);
      DeltaResponse page =
          apiClient.delta(token, sync.folderKey(), sync.continuationUrl());
      persistenceService.persistPage(connection, sync.folderKey(), page.value());
      stateService.completePage(sync.stateId(), page.nextLink(), page.deltaLink());

      connection.setLastSyncAt(java.time.Instant.now());
      connectionRepository.save(connection);
    } catch (DeltaCursorExpiredException exception) {
      stateService.resetCursor(sync.stateId());
    } catch (RuntimeException exception) {
      log.warn(
          "Microsoft sync failed connectionId={} folder={} attempt={} reason={}",
          sync.connectionId(),
          sync.folderKey(),
          sync.attempt(),
          exception.getClass().getSimpleName(),
          exception);
      stateService.retry(sync.stateId(), safeMessage(exception));
    }
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
