package com.messaging.channel.gmail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.asyncjob.dto.AsyncJobResponse;
import com.messaging.asyncjob.enums.AsyncJobType;
import com.messaging.asyncjob.service.AsyncJobService;
import com.messaging.channel.gmail.dto.GmailSyncQueuedResponse;
import com.messaging.kafka.event.EventMetadata;
import com.messaging.kafka.event.GmailSyncRequestedEvent;
import com.messaging.kafka.event.KafkaEvent;
import com.messaging.kafka.priority.MessagePriority;
import com.messaging.kafka.producer.EventPublisher;
import java.time.LocalDate;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GmailSyncQueueService {

  public static final String EVENT_TYPE = "gmail.sync.requested";

  private final EventPublisher eventPublisher;
  private final AsyncJobService asyncJobService;
  private final ObjectMapper objectMapper;

  public GmailSyncQueuedResponse enqueue(
      Long companyId, Long userId, Long connectionId, LocalDate fromDate, LocalDate toDate) {
    log.info(
        "GMAIL_SYNC_DIAG enqueue_start companyId={} userId={} connectionId={} fromDate={} toDate={}",
        companyId,
        userId,
        connectionId,
        fromDate,
        toDate);
    AsyncJobResponse job =
        asyncJobService.create(
            companyId,
            userId,
            AsyncJobType.GMAIL_SYNC,
            metadata(connectionId, fromDate, toDate),
            "Gmail sync queued");

    log.info(
        "GMAIL_SYNC_DIAG job_created jobId={} companyId={} connectionId={}",
        job.jobId(),
        companyId,
        connectionId);

    EventMetadata metadata =
        EventMetadata.create(
            EVENT_TYPE, "2", companyId, userId, job.jobId(), null, MessagePriority.MEDIUM);

    try {
      eventPublisher.publish(
          KafkaEvent.of(
              metadata,
              new GmailSyncRequestedEvent(
                  connectionId, Long.valueOf(job.jobId()), fromDate, toDate)));
      log.info(
          "GMAIL_SYNC_DIAG event_published eventId={} jobId={} companyId={} connectionId={} priority={}",
          metadata.eventId(),
          job.jobId(),
          companyId,
          connectionId,
          metadata.priority());
    } catch (RuntimeException exception) {
      log.error(
          "GMAIL_SYNC_DIAG enqueue_failed jobId={} companyId={} connectionId={} reason={}",
          job.jobId(),
          companyId,
          connectionId,
          exception.getClass().getSimpleName(),
          exception);
      asyncJobService.fail(Long.valueOf(job.jobId()), "Unable to queue Gmail sync");
      throw exception;
    }

    return new GmailSyncQueuedResponse(
        connectionId.toString(), job.jobId(), "QUEUED", fromDate, toDate);
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
