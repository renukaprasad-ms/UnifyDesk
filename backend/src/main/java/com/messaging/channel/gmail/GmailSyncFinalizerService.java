package com.messaging.channel.gmail;

import com.messaging.asyncjob.service.AsyncJobService;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.gmail.entity.GmailSyncRun;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GmailSyncFinalizerService {

  private final GmailSyncStateService stateService;
  private final GmailInboxPersistenceService persistenceService;
  private final ChannelConnectionRepository connectionRepository;
  private final AsyncJobService asyncJobService;

  @Transactional
  public void tryFinalize(Long jobId) {
    if (!stateService.claimFinalization(jobId)) {
      return;
    }

    GmailSyncRun run = stateService.requireRun(jobId);
    long failed = stateService.failedCount(jobId);

    if (failed > 0) {
      asyncJobService.fail(
          jobId,
          "Gmail sync finished with "
              + failed
              + " messages that could not be imported. Run sync again to retry them.");
      return;
    }

    ChannelConnection connection =
        connectionRepository
            .findByIdAndCompanyId(run.getConnectionId(), run.getCompanyId())
            .orElseThrow(() -> new NotFoundException("Gmail connection not found"));

    persistenceService.completeFullSync(connection, run.getHistoryId());
    asyncJobService.complete(
        jobId,
        "Gmail sync complete: "
            + run.getDiscoveredCount()
            + " messages processed in "
            + run.getBatchCount()
            + " batches");
  }
}
