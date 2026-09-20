package com.messaging.channel.microsoft;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.asyncjob.dto.AsyncJobResponse;
import com.messaging.asyncjob.enums.AsyncJobType;
import com.messaging.asyncjob.service.AsyncJobService;
import com.messaging.channel.email.EmailSyncRequest;
import com.messaging.channel.email.EmailSyncRequestValidator;
import com.messaging.channel.microsoft.dto.MicrosoftSyncQueuedResponse;
import com.messaging.channel.microsoft.entity.MicrosoftBackfillRun;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MicrosoftBackfillQueueService {

  private final AsyncJobService asyncJobService;
  private final MicrosoftBackfillJobFactory jobFactory;
  private final MicrosoftBackfillStateService stateService;
  private final MicrosoftOAuthProperties properties;
  private final ObjectMapper objectMapper;

  public MicrosoftSyncQueuedResponse enqueue(
      Long companyId, Long userId, Long connectionId, EmailSyncRequest request) {
    EmailSyncRequestValidator.validate(
        request,
        properties.getBackfillMaxRangeDays(),
        properties.getBackfillMaxMessages());

    var active =
        asyncJobService.findActive(
            companyId, AsyncJobType.MICROSOFT_SYNC, connectionId.toString());
    if (active.isPresent()) {
      return queuedResponse(active.get());
    }

    AsyncJobResponse job;
    try {
      job =
          jobFactory.create(
              companyId,
              userId,
              connectionId,
              request,
              metadata(connectionId, request));
    } catch (DataIntegrityViolationException exception) {
      job =
          asyncJobService
              .findActive(
                  companyId, AsyncJobType.MICROSOFT_SYNC, connectionId.toString())
              .orElseThrow(() -> exception);
    }

    return queuedResponse(job);
  }

  public AsyncJobResponse active(Long companyId, Long connectionId) {
    return asyncJobService
        .findActive(companyId, AsyncJobType.MICROSOFT_SYNC, connectionId.toString())
        .orElse(null);
  }

  private MicrosoftSyncQueuedResponse queuedResponse(AsyncJobResponse job) {
    MicrosoftBackfillRun run = stateService.require(Long.valueOf(job.jobId()));
    return new MicrosoftSyncQueuedResponse(
        run.getConnectionId().toString(),
        job.jobId(),
        job.status().name(),
        run.getSyncMode(),
        run.getFolder(),
        run.getFromDate(),
        run.getToDate(),
        run.getSyncMode() == com.messaging.channel.email.EmailSyncMode.LATEST_MESSAGES
            ? run.getMessageLimit()
            : null);
  }

  private String metadata(Long connectionId, EmailSyncRequest request) {
    try {
      Map<String, Object> metadata = new LinkedHashMap<>();
      metadata.put("connectionId", connectionId.toString());
      metadata.put("mode", request.mode().name());
      metadata.put("folder", request.folder().name());
      if (request.fromDate() != null) metadata.put("fromDate", request.fromDate().toString());
      if (request.toDate() != null) metadata.put("toDate", request.toDate().toString());
      if (request.messageCount() != null) metadata.put("messageCount", request.messageCount());
      return objectMapper.writeValueAsString(metadata);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize Microsoft sync metadata", exception);
    }
  }
}
