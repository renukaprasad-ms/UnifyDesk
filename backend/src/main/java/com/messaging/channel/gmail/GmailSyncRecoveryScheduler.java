package com.messaging.channel.gmail;

import com.messaging.channel.gmail.entity.GmailSyncBatch;
import com.messaging.channel.gmail.enums.GmailSyncBatchStatus;
import com.messaging.channel.gmail.repository.GmailSyncBatchRepository;
import com.messaging.channel.gmail.repository.GmailSyncRunRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GmailSyncRecoveryScheduler {

  private final GmailOAuthProperties properties;
  private final GmailSyncRunRepository runRepository;
  private final GmailSyncBatchRepository batchRepository;
  private final GmailSyncStateService stateService;
  private final GmailSyncCoordinatorPublisher coordinatorPublisher;

  @Scheduled(fixedDelayString = "${app.channel.gmail.sync-batch-recovery-interval:1m}")
  public void recover() {
    Instant cutoff = Instant.now().minus(properties.getSyncBatchStaleAfter());
    int size = Math.max(1, properties.getSyncBatchRecoverySize());

    runRepository.findRecoverable(cutoff, PageRequest.of(0, size)).forEach(
        run -> {
          try {
            stateService.touchRun(run.getJobId());
            coordinatorPublisher.publish(run, "recovery");
          } catch (RuntimeException exception) {
            log.warn(
                "Unable to recover Gmail sync coordinator jobId={} reason={}",
                run.getJobId(),
                exception.getClass().getSimpleName());
          }
        });

    List<GmailSyncBatch> batches =
        batchRepository.findRecoverable(
            List.of(
                GmailSyncBatchStatus.PENDING,
                GmailSyncBatchStatus.RUNNING,
                GmailSyncBatchStatus.RETRY),
            cutoff,
            PageRequest.of(0, size));

    for (GmailSyncBatch batch : batches) {
      try {
        if (!stateService.resetStaleForRecovery(batch.getId(), cutoff)) {
          continue;
        }
        log.info(
            "Recovered stale Gmail batch batchId={} jobId={} for DB worker retry",
            batch.getId(),
            batch.getJobId());
      } catch (RuntimeException exception) {
        log.warn(
            "Unable to recover Gmail sync batch batchId={} jobId={} reason={}",
            batch.getId(),
            batch.getJobId(),
            exception.getClass().getSimpleName());
      }
    }
  }
}
