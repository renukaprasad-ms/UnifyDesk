package com.messaging.kafka.config;

import com.messaging.kafka.event.KafkaEvent;
import com.messaging.kafka.priority.MessagePriority;
import com.messaging.kafka.topic.KafkaTopics;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.StringUtils;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
@RequiredArgsConstructor
public class KafkaConsumerConfig {

  private final com.messaging.kafka.config.KafkaProperties appKafkaProperties;
  private final KafkaTopics kafkaTopics;
  private final KafkaTemplate<String, KafkaEvent<?>> kafkaTemplate;

  @Value("${KAFKA_BOOTSTRAP_SERVERS:${SPRING_KAFKA_BOOTSTRAP_SERVERS:localhost:9001}}")
  private String bootstrapServers;

  @Value("${KAFKA_CONSUMER_AUTO_OFFSET_RESET:earliest}")
  private String autoOffsetReset;

  @Value("${KAFKA_MAX_POLL_INTERVAL_MS:300000}")
  private int maxPollIntervalMs;

  @Value("${KAFKA_SESSION_TIMEOUT_MS:45000}")
  private int sessionTimeoutMs;

  @Value("${KAFKA_HEARTBEAT_INTERVAL_MS:15000}")
  private int heartbeatIntervalMs;

  @Bean
  public ConsumerFactory<String, KafkaEvent<?>> kafkaEventConsumerFactory() {
    Map<String, Object> properties = new HashMap<>();
    properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
    properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
    properties.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, maxPollIntervalMs);
    properties.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, sessionTimeoutMs);
    properties.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, heartbeatIntervalMs);
    properties.put(JsonDeserializer.TRUSTED_PACKAGES, "com.messaging.kafka.event");
    properties.put(JsonDeserializer.VALUE_DEFAULT_TYPE, KafkaEvent.class.getName());
    properties.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
    properties.put(
        CommonClientConfigs.SECURITY_PROTOCOL_CONFIG,
        appKafkaProperties.getSecurity().getProtocol());
    if (StringUtils.hasText(appKafkaProperties.getSecurity().getSaslMechanism())) {
      properties.put("sasl.mechanism", appKafkaProperties.getSecurity().getSaslMechanism());
    }
    if (StringUtils.hasText(appKafkaProperties.getSecurity().getSaslJaasConfig())) {
      properties.put("sasl.jaas.config", appKafkaProperties.getSecurity().getSaslJaasConfig());
    }
    return new DefaultKafkaConsumerFactory<>(properties);
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, KafkaEvent<?>>
      criticalKafkaListenerContainerFactory() {
    return factory(MessagePriority.CRITICAL, appKafkaProperties.getTopics().getCritical());
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, KafkaEvent<?>>
      highKafkaListenerContainerFactory() {
    return factory(MessagePriority.HIGH, appKafkaProperties.getTopics().getHigh());
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, KafkaEvent<?>>
      mediumKafkaListenerContainerFactory() {
    return factory(MessagePriority.MEDIUM, appKafkaProperties.getTopics().getMedium());
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, KafkaEvent<?>>
      lowKafkaListenerContainerFactory() {
    return factory(MessagePriority.LOW, appKafkaProperties.getTopics().getLow());
  }

  private ConcurrentKafkaListenerContainerFactory<String, KafkaEvent<?>> factory(
      MessagePriority priority, com.messaging.kafka.config.KafkaProperties.PriorityTopic topic) {
    ConcurrentKafkaListenerContainerFactory<String, KafkaEvent<?>> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(kafkaEventConsumerFactory());
    factory.setConcurrency(topic.getConcurrency());
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
    factory.getContainerProperties().setPollTimeout(1500L);
    factory.setCommonErrorHandler(errorHandler(priority));
    Properties consumerProperties = new Properties();
    consumerProperties.put(
        ConsumerConfig.MAX_POLL_RECORDS_CONFIG, String.valueOf(topic.getMaxPollRecords()));
    factory.getContainerProperties().setKafkaConsumerProperties(consumerProperties);
    return factory;
  }

  private DefaultErrorHandler errorHandler(MessagePriority priority) {
    ExponentialBackOff backOff = new ExponentialBackOff();
    backOff.setInitialInterval(appKafkaProperties.getRetry().getInitialDelay().toMillis());
    backOff.setMultiplier(appKafkaProperties.getRetry().getMultiplier());
    backOff.setMaxInterval(appKafkaProperties.getRetry().getMaxDelay().toMillis());
    backOff.setMaxElapsedTime(
        appKafkaProperties.getRetry().getMaxDelay().toMillis()
            * appKafkaProperties.getRetry().getMaxAttempts());

    DeadLetterPublishingRecoverer recoverer =
        new DeadLetterPublishingRecoverer(
            kafkaTemplate,
            (record, exception) ->
                new TopicPartition(kafkaTopics.dltTopic(priority), record.partition()));

    DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
    errorHandler.addNotRetryableExceptions(
        com.messaging.kafka.exception.NonRetryableKafkaException.class,
        com.messaging.common.exception.BadRequestException.class);
    return errorHandler;
  }
}
