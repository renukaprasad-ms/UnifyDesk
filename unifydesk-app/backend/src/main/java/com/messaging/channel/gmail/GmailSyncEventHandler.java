package com.messaging.channel.gmail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.asyncjob.service.AsyncJobService;
import com.messaging.common.exception.ApiException;
import com.messaging.common.exception.BadRequestException;
import com.messaging.kafka.exception.RetryableKafkaException;
import com.messaging.kafka.consumer.KafkaEventHandler;
import com.messaging.kafka.event.GmailSyncRequestedEvent;
import com.messaging.kafka.event.KafkaEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
    GmailSyncRequestedEvent payload =
        objectMapper.convertValue(event.payload(), GmailSyncRequestedEvent.class);

    if (event.companyId() == null
        || payload.connectionId() == null
        || payload.jobId() == null
        || payload.mode() == null
        || payload.folder() == null) {
      throw new IllegalArgumentException("Gmail sync event is incomplete");
    }

    try {
      syncService.sync(
          event.companyId(),
          payload.connectionId(),
          payload.jobId(),
          payload.mode(),
          payload.folder(),
          payload.fromDate(),
          payload.toDate(),
          payload.messageCount());
    } catch (BadRequestException exception) {
      asyncJobService.fail(payload.jobId(), userSafeMessage(exception));
      throw exception;
    } catch (RuntimeException exception) {
      throw new RetryableKafkaException("Gmail sync coordinator failed", exception);
    }
  }

  private String userSafeMessage(RuntimeException exception) {
    if (exception instanceof ApiException && exception.getMessage() != null) {
      return exception.getMessage();
    }
    return "Gmail sync failed. Please try again.";
  }
}
