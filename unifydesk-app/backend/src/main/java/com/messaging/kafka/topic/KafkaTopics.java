package com.messaging.kafka.topic;

import com.messaging.kafka.config.KafkaProperties;
import com.messaging.kafka.priority.MessagePriority;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class KafkaTopics {

  private final Map<MessagePriority, String> topics = new EnumMap<>(MessagePriority.class);
  private final Map<MessagePriority, String> retryTopics = new EnumMap<>(MessagePriority.class);
  private final Map<MessagePriority, String> dltTopics = new EnumMap<>(MessagePriority.class);

  public KafkaTopics(KafkaProperties properties) {
    topics.put(MessagePriority.CRITICAL, properties.getTopics().getCritical().getName());
    topics.put(MessagePriority.HIGH, properties.getTopics().getHigh().getName());
    topics.put(MessagePriority.MEDIUM, properties.getTopics().getMedium().getName());
    topics.put(MessagePriority.LOW, properties.getTopics().getLow().getName());

    retryTopics.put(MessagePriority.CRITICAL, properties.getTopics().getCritical().getRetryName());
    retryTopics.put(MessagePriority.HIGH, properties.getTopics().getHigh().getRetryName());
    retryTopics.put(MessagePriority.MEDIUM, properties.getTopics().getMedium().getRetryName());
    retryTopics.put(MessagePriority.LOW, properties.getTopics().getLow().getRetryName());

    dltTopics.put(MessagePriority.CRITICAL, properties.getTopics().getCritical().getDltName());
    dltTopics.put(MessagePriority.HIGH, properties.getTopics().getHigh().getDltName());
    dltTopics.put(MessagePriority.MEDIUM, properties.getTopics().getMedium().getDltName());
    dltTopics.put(MessagePriority.LOW, properties.getTopics().getLow().getDltName());
  }

  public String topic(MessagePriority priority) {
    return topics.get(priority);
  }

  public String retryTopic(MessagePriority priority) {
    return retryTopics.get(priority);
  }

  public String dltTopic(MessagePriority priority) {
    return dltTopics.get(priority);
  }
}
