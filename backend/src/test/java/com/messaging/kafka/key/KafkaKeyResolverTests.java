package com.messaging.kafka.key;

import static org.assertj.core.api.Assertions.assertThat;

import com.messaging.kafka.event.EventMetadata;
import com.messaging.kafka.event.KafkaEvent;
import com.messaging.kafka.event.NotificationRequestedEvent;
import com.messaging.kafka.priority.MessagePriority;
import org.junit.jupiter.api.Test;

class KafkaKeyResolverTests {

  @Test
  void prefersCompanyAndUserForOrderingKey() {
    EventMetadata metadata =
        EventMetadata.create(
            "notification.requested",
            "v1",
            7L,
            9L,
            "corr-1",
            null,
            MessagePriority.MEDIUM);
    KafkaEvent<NotificationRequestedEvent> event =
        KafkaEvent.of(metadata, new NotificationRequestedEvent(7L, "user@example.com", "EMAIL", "welcome"));

    assertThat(new KafkaKeyResolver().resolve(event)).isEqualTo("7:user:9");
  }
}
