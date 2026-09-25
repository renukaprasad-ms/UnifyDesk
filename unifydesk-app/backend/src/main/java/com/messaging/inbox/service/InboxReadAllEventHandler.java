package com.messaging.inbox.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.asyncjob.service.AsyncJobService;
import com.messaging.common.exception.ApiException;
import com.messaging.common.exception.BadRequestException;
import com.messaging.kafka.consumer.KafkaEventHandler;
import com.messaging.kafka.event.InboxMarkAllReadRequestedEvent;
import com.messaging.kafka.event.KafkaEvent;
import com.messaging.kafka.exception.RetryableKafkaException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InboxReadAllEventHandler implements KafkaEventHandler {

  private final InboxReadStateService readStateService;
  private final AsyncJobService asyncJobService;
  private final ObjectMapper objectMapper;

  @Override
  public boolean supports(String eventType) {
    return InboxReadStateQueueService.EVENT_TYPE.equals(eventType);
  }

  @Override
  public void handle(KafkaEvent<?> event) {
    InboxMarkAllReadRequestedEvent payload =
        objectMapper.convertValue(event.payload(), InboxMarkAllReadRequestedEvent.class);

    if (event.companyId() == null
        || payload.jobId() == null
        || payload.connectionId() == null
        || payload.folder() == null) {
      throw new IllegalArgumentException("Inbox mark-all-read event is incomplete");
    }

    try {
      readStateService.processMarkAllRead(
          payload.jobId(), event.companyId(), payload.connectionId(), payload.folder());
    } catch (BadRequestException exception) {
      asyncJobService.fail(payload.jobId(), userSafeMessage(exception));
      throw exception;
    } catch (RuntimeException exception) {
      throw new RetryableKafkaException("Inbox mark-all-read worker failed", exception);
    }
  }

  private String userSafeMessage(RuntimeException exception) {
    if (exception instanceof ApiException && exception.getMessage() != null) {
      return exception.getMessage();
    }
    return "Unable to mark inbox messages as read. Please try again.";
  }
}
