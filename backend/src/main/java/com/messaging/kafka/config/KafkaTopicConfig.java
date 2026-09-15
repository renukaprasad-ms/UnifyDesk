package com.messaging.kafka.config;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@RequiredArgsConstructor
public class KafkaTopicConfig {

  private final KafkaProperties kafkaProperties;

  @Bean
  public List<NewTopic> kafkaPriorityTopics() {
    KafkaProperties.Topics topics = kafkaProperties.getTopics();
    return List.of(
        topic(topics.getCritical()),
        retryTopic(topics.getCritical()),
        dltTopic(topics.getCritical()),
        topic(topics.getHigh()),
        retryTopic(topics.getHigh()),
        dltTopic(topics.getHigh()),
        topic(topics.getMedium()),
        retryTopic(topics.getMedium()),
        dltTopic(topics.getMedium()),
        topic(topics.getLow()),
        retryTopic(topics.getLow()),
        dltTopic(topics.getLow()));
  }

  private NewTopic topic(KafkaProperties.PriorityTopic topic) {
    return baseTopic(topic.getName(), topic.getPartitions(), topic.getRetention());
  }

  private NewTopic retryTopic(KafkaProperties.PriorityTopic topic) {
    return baseTopic(topic.getRetryName(), topic.getPartitions(), topic.getRetention());
  }

  private NewTopic dltTopic(KafkaProperties.PriorityTopic topic) {
    return baseTopic(
        topic.getDltName(),
        topic.getPartitions(),
        kafkaProperties.getTopics().getDltRetention());
  }

  private NewTopic baseTopic(String name, int partitions, java.time.Duration retention) {
    return TopicBuilder.name(name)
        .partitions(partitions)
        .replicas(kafkaProperties.getTopics().getReplicationFactor())
        .configs(
            Map.of(
                "retention.ms",
                String.valueOf(retention.toMillis()),
                "min.insync.replicas",
                String.valueOf(kafkaProperties.getTopics().getMinInsyncReplicas())))
        .build();
  }
}
