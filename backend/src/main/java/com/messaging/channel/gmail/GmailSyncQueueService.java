package com.messaging.channel.gmail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.asyncjob.dto.AsyncJobResponse;
import com.messaging.asyncjob.enums.AsyncJobType;
import com.messaging.asyncjob.service.AsyncJobService;
import com.messaging.channel.gmail.dto.GmailSyncQueuedResponse;
import com.messaging.channel.gmail.entity.GmailSyncRun;
import java.time.LocalDate;
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
      Long companyId, Long userId, Long connectionId, LocalDate fromDate, LocalDate toDate) {
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
              fromDate,
              toDate,
              metadata(connectionId, fromDate, toDate));
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
        connectionId.toString(), job.jobId(), job.status().name(), fromDate, toDate);
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
        run.getFromDate(),
        run.getToDate());
  }

  private String metadata(Long connectionId, LocalDate fromDate, LocalDate toDate) {
    try {
      return objectMapper.writeValueAsString(
          Map.of(
              "connectionId", connectionId.toString(),
              "fromDate", fromDate.toString(),
              "toDate", toDate.toString()));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize Gmail sync metadata", exception);
    }
  }
}
