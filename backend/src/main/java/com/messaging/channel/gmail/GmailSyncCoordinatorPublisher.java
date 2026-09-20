package com.messaging.channel.gmail;

import com.messaging.channel.gmail.entity.GmailSyncRun;
import com.messaging.kafka.event.EventMetadata;
import com.messaging.kafka.event.GmailSyncRequestedEvent;
import com.messaging.kafka.event.KafkaEvent;
import com.messaging.kafka.priority.MessagePriority;
import com.messaging.kafka.producer.EventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GmailSyncCoordinatorPublisher {

  private final EventPublisher eventPublisher;

  public void publish(GmailSyncRun run, String causationId) {
    EventMetadata metadata =
        EventMetadata.create(
            GmailSyncQueueService.EVENT_TYPE,
            "4",
            run.getCompanyId(),
            run.getUserId(),
            run.getJobId().toString(),
            causationId,
            MessagePriority.MEDIUM);

    KafkaEvent<GmailSyncRequestedEvent> event =
        KafkaEvent.of(
            metadata,
            new GmailSyncRequestedEvent(
                run.getConnectionId(),
                run.getJobId(),
                run.getSyncMode(),
                run.getFolder(),
                run.getFromDate(),
                run.getToDate(),
                run.getMessageCount()));

    eventPublisher.publish("gmail-connection:" + run.getConnectionId(), event);
  }
}
