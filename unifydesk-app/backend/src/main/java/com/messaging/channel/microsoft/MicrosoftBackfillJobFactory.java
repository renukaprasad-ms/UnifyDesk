package com.messaging.channel.microsoft;

import com.messaging.asyncjob.dto.AsyncJobResponse;
import com.messaging.asyncjob.enums.AsyncJobType;
import com.messaging.asyncjob.service.AsyncJobService;
import com.messaging.channel.email.EmailSyncRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MicrosoftBackfillJobFactory {

  private final AsyncJobService asyncJobService;
  private final MicrosoftBackfillStateService stateService;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public AsyncJobResponse create(
      Long companyId,
      Long userId,
      Long connectionId,
      EmailSyncRequest request,
      String metadataJson) {
    AsyncJobResponse job =
        asyncJobService.create(
            companyId,
            userId,
            AsyncJobType.MICROSOFT_SYNC,
            connectionId.toString(),
            metadataJson,
            "Microsoft email sync queued");

    stateService.createRun(
        Long.valueOf(job.jobId()), companyId, userId, connectionId, request);
    return job;
  }
}
