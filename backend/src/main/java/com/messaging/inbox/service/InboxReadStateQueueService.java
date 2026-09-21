package com.messaging.inbox.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.asyncjob.dto.AsyncJobResponse;
import com.messaging.asyncjob.enums.AsyncJobType;
import com.messaging.asyncjob.service.AsyncJobService;
import com.messaging.channel.email.EmailFolder;
import com.messaging.common.exception.BadRequestException;
import com.messaging.kafka.event.EventMetadata;
import com.messaging.kafka.event.InboxMarkAllReadRequestedEvent;
import com.messaging.kafka.event.KafkaEvent;
import com.messaging.kafka.priority.MessagePriority;
import com.messaging.kafka.producer.EventPublisher;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InboxReadStateQueueService {

  public static final String EVENT_TYPE = "inbox.read.all.requested";

  private final AsyncJobService asyncJobService;
  private final EventPublisher eventPublisher;
  private final ObjectMapper objectMapper;

  public AsyncJobResponse queueMarkAllRead(
      Long companyId, Long userId, Long connectionId, EmailFolder folder) {
    EmailFolder resolvedFolder = folder == null ? EmailFolder.INBOX : folder;
    if (resolvedFolder != EmailFolder.INBOX) {
      throw new BadRequestException("Mark all as read is only available for the inbox");
    }
    String resourceId = connectionId + ":" + resolvedFolder.name();

    var active =
        asyncJobService.findActive(companyId, AsyncJobType.INBOX_READ_SYNC, resourceId);
    if (active.isPresent()) {
      return active.get();
    }

    String metadata;
    try {
      metadata =
          objectMapper.writeValueAsString(
              Map.of(
                  "connectionId", connectionId.toString(),
                  "folder", resolvedFolder.name()));
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to serialize inbox read job metadata", exception);
    }

    AsyncJobResponse job =
        asyncJobService.create(
            companyId,
            userId,
            AsyncJobType.INBOX_READ_SYNC,
            resourceId,
            metadata,
            "Mark all as read queued");

    EventMetadata eventMetadata =
        EventMetadata.create(
            EVENT_TYPE,
            "1",
            companyId,
            userId,
            job.jobId(),
            null,
            MessagePriority.HIGH);
    KafkaEvent<InboxMarkAllReadRequestedEvent> event =
        KafkaEvent.of(
            eventMetadata,
            new InboxMarkAllReadRequestedEvent(
                Long.valueOf(job.jobId()), connectionId, resolvedFolder));

    try {
      eventPublisher.publish("inbox-read:" + connectionId, event);
      return job;
    } catch (RuntimeException exception) {
      asyncJobService.fail(Long.valueOf(job.jobId()), "Unable to queue mark all as read");
      throw exception;
    }
  }
}
