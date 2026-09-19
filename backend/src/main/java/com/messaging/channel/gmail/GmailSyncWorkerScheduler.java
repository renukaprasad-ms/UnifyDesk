package com.messaging.channel.gmail;

import com.messaging.channel.gmail.GmailSyncStateService.ClaimedBatch;
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
public class GmailSyncWorkerScheduler {

  private final GmailOAuthProperties properties;
  private final GmailSyncStateService stateService;
  private final GmailSyncBatchService batchService;
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
  private final AtomicInteger inFlight = new AtomicInteger();

  public GmailSyncWorkerScheduler(
      GmailOAuthProperties properties,
      GmailSyncStateService stateService,
      GmailSyncBatchService batchService) {
    this.properties = properties;
    this.stateService = stateService;
    this.batchService = batchService;
  }

  @Scheduled(fixedDelayString = "${app.channel.gmail.worker-poll-interval-ms:250}")
  public void dispatch() {
    int concurrency = Math.max(1, properties.getWorkerConcurrency());
    int availableSlots = concurrency - inFlight.get();
    if (availableSlots <= 0) {
      return;
    }

    List<ClaimedBatch> batches = stateService.claimNextBatches(availableSlots);
    if (batches.isEmpty()) {
      return;
    }

    for (ClaimedBatch batch : batches) {
      inFlight.incrementAndGet();
      executor.submit(
          () -> {
            try {
              batchService.processClaimed(batch);
            } catch (RuntimeException exception) {
              log.error(
                  "Unhandled Gmail worker error batchId={} jobId={} connectionId={} error={}",
                  batch.batchId(),
                  batch.jobId(),
                  batch.connectionId(),
                  exception.getClass().getSimpleName(),
                  exception);
            } finally {
              inFlight.decrementAndGet();
            }
          });
    }
  }

  public int inFlightCount() {
    return inFlight.get();
  }

  @PreDestroy
  void shutdown() {
    executor.shutdown();
  }
}
