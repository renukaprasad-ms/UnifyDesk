package com.messaging.emailprovider.queue;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConditionalOnProperty(prefix = "app.kafka", name = "enabled", havingValue = "true")
public class KafkaTopicConfig {

  @Bean
  NewTopic emailDeliveryRequestedTopic(
      @Value("${app.kafka.topics.delivery-requested:email.delivery.requested}") String name,
      @Value("${app.kafka.partitions:6}") int partitions,
      @Value("${app.kafka.replication-factor:1}") short replicationFactor) {
    return topic(name, partitions, replicationFactor);
  }

  @Bean
  NewTopic emailLifecycleTopic(
      @Value("${app.kafka.topics.lifecycle:email.lifecycle.events}") String name,
      @Value("${app.kafka.partitions:6}") int partitions,
      @Value("${app.kafka.replication-factor:1}") short replicationFactor) {
    return topic(name, partitions, replicationFactor);
  }

  @Bean
  NewTopic emailValidationBatchTopic(
      @Value("${app.kafka.topics.validation-batch:email.validation.batch}") String name,
      @Value("${app.kafka.partitions:6}") int partitions,
      @Value("${app.kafka.replication-factor:1}") short replicationFactor) {
    return topic(name, partitions, replicationFactor);
  }

  @Bean
  NewTopic emailValidationLifecycleTopic(
      @Value("${app.kafka.topics.validation-lifecycle:email.validation.lifecycle}") String name,
      @Value("${app.kafka.partitions:6}") int partitions,
      @Value("${app.kafka.replication-factor:1}") short replicationFactor) {
    return topic(name, partitions, replicationFactor);
  }

  private NewTopic topic(String name, int partitions, short replicationFactor) {
    return TopicBuilder.name(name).partitions(partitions).replicas(replicationFactor).build();
  }
}
