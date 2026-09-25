package com.messaging.broadcast.worker;

import com.messaging.broadcast.config.BroadcastProperties;
import com.messaging.broadcast.service.BroadcastBatchClaimService;
import com.messaging.broadcast.service.BroadcastBatchProcessor;
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
public class BroadcastBatchWorkerScheduler {

  private final BroadcastProperties properties;
  private final BroadcastBatchClaimService claimService;
  private final BroadcastBatchProcessor processor;
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
  private final AtomicInteger inFlight = new AtomicInteger();

  public BroadcastBatchWorkerScheduler(
      BroadcastProperties properties,
      BroadcastBatchClaimService claimService,
      BroadcastBatchProcessor processor) {
    this.properties = properties;
    this.claimService = claimService;
    this.processor = processor;
  }

  @Scheduled(fixedDelayString = "${app.broadcast.worker-poll-interval-ms:250}")
  public void dispatch() {
    int available = Math.max(1, properties.getWorkerConcurrency()) - inFlight.get();
    if (available <= 0) return;
    List<Long> batchIds = claimService.claim(available);
    for (Long batchId : batchIds) {
      inFlight.incrementAndGet();
      executor.submit(
          () -> {
            try {
              processor.process(batchId);
            } catch (RuntimeException exception) {
              log.error(
                  "broadcast_batch_unhandled_error batchId={} reason={}",
                  batchId,
                  exception.getClass().getSimpleName(),
                  exception);
            } finally {
              inFlight.decrementAndGet();
            }
          });
    }
  }

  @PreDestroy
  void shutdown() {
    executor.shutdown();
  }
}
