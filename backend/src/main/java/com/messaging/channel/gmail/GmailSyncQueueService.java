package com.messaging.channel.gmail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.asyncjob.dto.AsyncJobResponse;
import com.messaging.asyncjob.enums.AsyncJobType;
import com.messaging.asyncjob.service.AsyncJobService;
import com.messaging.channel.email.EmailSyncRequest;
import com.messaging.channel.gmail.dto.GmailSyncQueuedResponse;
import com.messaging.channel.gmail.entity.GmailSyncRun;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GmailSyncQueueService {

  public static final String EVENT_TYPE = "gmail.sync.requested";

  private final AsyncJobService asyncJobService;
  private final GmailSyncCoordinatorPublisher coordinatorPublisher;
  private final GmailSyncJobFactory jobFactory;
  private final GmailSyncStateService stateService;
  private final ObjectMapper objectMapper;

  public GmailSyncQueuedResponse enqueue(
      Long companyId, Long userId, Long connectionId, EmailSyncRequest request) {
    var active =
        asyncJobService.findActive(companyId, AsyncJobType.GMAIL_SYNC, connectionId.toString());
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
              .findActive(companyId, AsyncJobType.GMAIL_SYNC, connectionId.toString())
              .orElseThrow(() -> exception);
      return queuedResponse(job);
    }

    try {
      coordinatorPublisher.publish(stateService.requireRun(Long.valueOf(job.jobId())), null);
    } catch (RuntimeException exception) {
      asyncJobService.fail(Long.valueOf(job.jobId()), "Unable to queue Gmail sync");
      throw exception;
    }

    return new GmailSyncQueuedResponse(
        connectionId.toString(),
        job.jobId(),
        job.status().name(),
        request.mode(),
        request.folder(),
        request.fromDate(),
        request.toDate(),
        request.messageCount());
  }

  public AsyncJobResponse active(Long companyId, Long connectionId) {
    return asyncJobService
        .findActive(companyId, AsyncJobType.GMAIL_SYNC, connectionId.toString())
        .orElse(null);
  }

  private GmailSyncQueuedResponse queuedResponse(AsyncJobResponse job) {
    GmailSyncRun run = stateService.requireRun(Long.valueOf(job.jobId()));
    return new GmailSyncQueuedResponse(
        run.getConnectionId().toString(),
        job.jobId(),
        job.status().name(),
        run.getSyncMode(),
        run.getFolder(),
        run.getFromDate(),
        run.getToDate(),
        run.getMessageCount());
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
      throw new IllegalStateException("Unable to serialize Gmail sync metadata", exception);
    }
  }
}
