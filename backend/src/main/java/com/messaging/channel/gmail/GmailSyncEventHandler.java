package com.messaging.channel.gmail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.asyncjob.service.AsyncJobService;
import com.messaging.common.exception.ApiException;
import com.messaging.kafka.consumer.KafkaEventHandler;
import com.messaging.kafka.event.GmailSyncRequestedEvent;
import com.messaging.kafka.event.KafkaEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GmailSyncEventHandler implements KafkaEventHandler {

  private final GmailSyncService syncService;
  private final AsyncJobService asyncJobService;
  private final ObjectMapper objectMapper;

  @Override
  public boolean supports(String eventType) {
    return GmailSyncQueueService.EVENT_TYPE.equals(eventType);
  }

  @Override
  public void handle(KafkaEvent<?> event) {
    log.info(
        "GMAIL_SYNC_DIAG event_received eventId={} companyId={} userId={} correlationId={}",
        event.eventId(),
        event.companyId(),
        event.userId(),
        event.correlationId());

    GmailSyncRequestedEvent payload =
        objectMapper.convertValue(event.payload(), GmailSyncRequestedEvent.class);

    if (event.companyId() == null
        || payload.connectionId() == null
        || payload.jobId() == null
        || payload.fromDate() == null
        || payload.toDate() == null) {
      throw new IllegalArgumentException("Gmail sync event is incomplete");
    }

    log.info(
        "GMAIL_SYNC_DIAG event_validated eventId={} jobId={} connectionId={} fromDate={} toDate={}",
        event.eventId(),
        payload.jobId(),
        payload.connectionId(),
        payload.fromDate(),
        payload.toDate());

    try {
      syncService.sync(
          event.companyId(),
          payload.connectionId(),
          payload.jobId(),
          payload.fromDate(),
          payload.toDate());
      log.info(
          "GMAIL_SYNC_DIAG event_completed eventId={} jobId={} connectionId={}",
          event.eventId(),
          payload.jobId(),
          payload.connectionId());
    } catch (RuntimeException exception) {
      log.error(
          "GMAIL_SYNC_DIAG event_failed eventId={} jobId={} connectionId={} reason={} message={}",
          event.eventId(),
          payload.jobId(),
          payload.connectionId(),
          exception.getClass().getSimpleName(),
          exception.getMessage(),
          exception);
      asyncJobService.fail(payload.jobId(), userSafeMessage(exception));
      throw exception;
    }
  }

  private String userSafeMessage(RuntimeException exception) {
    if (exception instanceof ApiException && exception.getMessage() != null) {
      return exception.getMessage();
    }
    return "Gmail sync failed. Please try again.";
  }
}
