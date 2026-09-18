package com.messaging.channel.gmail;

import com.messaging.kafka.event.EventMetadata;
import com.messaging.kafka.event.GmailSyncRequestedEvent;
import com.messaging.kafka.event.KafkaEvent;
import com.messaging.kafka.priority.MessagePriority;
import com.messaging.kafka.producer.EventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GmailSyncQueueService {

  public static final String EVENT_TYPE = "gmail.sync.requested";

  private final EventPublisher eventPublisher;

  public void enqueue(Long companyId, Long userId, Long connectionId, boolean initialSync) {
    EventMetadata metadata =
        EventMetadata.create(
            EVENT_TYPE, "1", companyId, userId, null, null, MessagePriority.MEDIUM);

    eventPublisher.publish(
        KafkaEvent.of(metadata, new GmailSyncRequestedEvent(connectionId, initialSync)));
  }
}
