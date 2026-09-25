package com.messaging.kafka.topic;

import static org.assertj.core.api.Assertions.assertThat;

import com.messaging.kafka.config.KafkaProperties;
import com.messaging.kafka.priority.MessagePriority;
import org.junit.jupiter.api.Test;

class KafkaTopicsTests {

  @Test
  void mapsPrioritiesToConfiguredTopics() {
    KafkaTopics topics = new KafkaTopics(properties());

    assertThat(topics.topic(MessagePriority.CRITICAL)).isEqualTo("messaging.jobs.critical");
    assertThat(topics.topic(MessagePriority.HIGH)).isEqualTo("messaging.jobs.high");
    assertThat(topics.topic(MessagePriority.MEDIUM)).isEqualTo("messaging.jobs.medium");
    assertThat(topics.topic(MessagePriority.LOW)).isEqualTo("messaging.jobs.low");
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
