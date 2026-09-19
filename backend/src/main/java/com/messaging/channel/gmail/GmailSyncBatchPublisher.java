package com.messaging.channel.gmail;

import com.messaging.channel.gmail.entity.GmailSyncBatch;
import com.messaging.channel.gmail.entity.GmailSyncRun;
import com.messaging.kafka.event.EventMetadata;
import com.messaging.kafka.event.GmailSyncBatchRequestedEvent;
import com.messaging.kafka.event.KafkaEvent;
import com.messaging.kafka.priority.MessagePriority;
import com.messaging.kafka.producer.EventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GmailSyncBatchPublisher {

  public static final String EVENT_TYPE = "gmail.sync.batch.requested";

  private final EventPublisher eventPublisher;

  public void publish(GmailSyncRun run, GmailSyncBatch batch, String causationId) {
    EventMetadata metadata =
        EventMetadata.create(
            EVENT_TYPE,
            "1",
            run.getCompanyId(),
            run.getUserId(),
            run.getJobId().toString(),
            causationId,
            MessagePriority.MEDIUM);

    eventPublisher.publish(
        KafkaEvent.of(
            metadata,
            new GmailSyncBatchRequestedEvent(
                batch.getId(), batch.getJobId(), batch.getConnectionId())));
  }
}
