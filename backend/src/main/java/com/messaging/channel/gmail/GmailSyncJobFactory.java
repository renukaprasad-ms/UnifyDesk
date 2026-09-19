package com.messaging.channel.gmail;

import com.messaging.asyncjob.dto.AsyncJobResponse;
import com.messaging.asyncjob.enums.AsyncJobType;
import com.messaging.asyncjob.service.AsyncJobService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GmailSyncJobFactory {

  private final AsyncJobService asyncJobService;
  private final GmailSyncStateService stateService;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public AsyncJobResponse create(
      Long companyId,
      Long userId,
      Long connectionId,
      LocalDate fromDate,
      LocalDate toDate,
      String metadataJson) {
    AsyncJobResponse job =
        asyncJobService.create(
            companyId,
            userId,
            AsyncJobType.GMAIL_SYNC,
            connectionId.toString(),
            metadataJson,
            "Gmail sync queued");

    stateService.createRun(
        Long.valueOf(job.jobId()), companyId, userId, connectionId, fromDate, toDate);
    return job;
  }
}
