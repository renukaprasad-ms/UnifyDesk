package com.messaging.kafka.producer;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.messaging.kafka.config.KafkaProperties;
import com.messaging.kafka.event.EventMetadata;
import com.messaging.kafka.event.KafkaEvent;
import com.messaging.kafka.event.OtpRequestedEvent;
import com.messaging.kafka.key.KafkaKeyResolver;
import com.messaging.kafka.priority.MessagePriority;
import com.messaging.kafka.topic.KafkaTopics;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

class EventPublisherTests {

  @Test
  void publishesCriticalEventToCriticalTopic() {
    KafkaTemplate<String, KafkaEvent<?>> kafkaTemplate = org.mockito.Mockito.mock(KafkaTemplate.class);
    KafkaTopics topics = new KafkaTopics(properties());
    EventPublisher publisher = new EventPublisher(kafkaTemplate, topics, new KafkaKeyResolver());
    KafkaEvent<OtpRequestedEvent> event =
        KafkaEvent.of(
            EventMetadata.create(
                "auth.otp.requested",
                "v1",
                1L,
                2L,
                "corr",
                null,
                MessagePriority.CRITICAL),
            new OtpRequestedEvent(
                "user@example.com", "EMAIL", "Verify your email", "Your code is 123456", false));
    when(kafkaTemplate.send("messaging.jobs.critical", "1:user:2", event))
        .thenReturn(new CompletableFuture<>());

    publisher.publish(event);

    verify(kafkaTemplate).send("messaging.jobs.critical", "1:user:2", event);
  }

  private KafkaProperties properties() {
    KafkaProperties properties = new KafkaProperties();
    properties.getTopics().getCritical().setName("messaging.jobs.critical");
    properties.getTopics().getHigh().setName("messaging.jobs.high");
    properties.getTopics().getMedium().setName("messaging.jobs.medium");
    properties.getTopics().getLow().setName("messaging.jobs.low");
    return properties;
  }
}
