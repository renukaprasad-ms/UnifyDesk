package com.messaging.kafka.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.kafka.event.KafkaEvent;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.StringUtils;

@Configuration
public class KafkaProducerConfig {

  @Bean
  public ProducerFactory<String, KafkaEvent<?>> kafkaEventProducerFactory(
      @Value("${KAFKA_BOOTSTRAP_SERVERS:${SPRING_KAFKA_BOOTSTRAP_SERVERS:localhost:9001}}")
          String bootstrapServers,
      @Value("${KAFKA_CLIENT_ID:messaging-api}") String clientId,
      @Value("${KAFKA_DELIVERY_TIMEOUT_MS:120000}") int deliveryTimeoutMs,
      @Value("${KAFKA_REQUEST_TIMEOUT_MS:30000}") int requestTimeoutMs,
      @Value("${KAFKA_LINGER_MS:10}") int lingerMs,
      @Value("${KAFKA_BATCH_SIZE:32768}") int batchSize,
      @Value("${KAFKA_COMPRESSION_TYPE:none}") String compressionType,
      @Value("${KAFKA_MAX_IN_FLIGHT_REQUESTS:5}") int maxInFlightRequests,
      com.messaging.kafka.config.KafkaProperties appKafkaProperties,
      ObjectMapper objectMapper) {
    Map<String, Object> properties = new HashMap<>();
    properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    properties.put(ProducerConfig.CLIENT_ID_CONFIG, clientId);
    properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    properties.put(ProducerConfig.ACKS_CONFIG, "all");
    properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    properties.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, deliveryTimeoutMs);
    properties.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, requestTimeoutMs);
    properties.put(ProducerConfig.LINGER_MS_CONFIG, lingerMs);
    properties.put(ProducerConfig.BATCH_SIZE_CONFIG, batchSize);
    properties.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, compressionType);
    properties.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, maxInFlightRequests);
    properties.put(
        CommonClientConfigs.SECURITY_PROTOCOL_CONFIG,
        appKafkaProperties.getSecurity().getProtocol());
    if (StringUtils.hasText(appKafkaProperties.getSecurity().getSaslMechanism())) {
      properties.put("sasl.mechanism", appKafkaProperties.getSecurity().getSaslMechanism());
    }
    if (StringUtils.hasText(appKafkaProperties.getSecurity().getSaslJaasConfig())) {
      properties.put("sasl.jaas.config", appKafkaProperties.getSecurity().getSaslJaasConfig());
    }
    JsonSerializer<KafkaEvent<?>> valueSerializer = new JsonSerializer<>(objectMapper);
    valueSerializer.setAddTypeInfo(false);
    return new DefaultKafkaProducerFactory<>(properties, new StringSerializer(), valueSerializer);
  }

  @Bean
  public KafkaTemplate<String, KafkaEvent<?>> kafkaTemplate(
      ProducerFactory<String, KafkaEvent<?>> kafkaEventProducerFactory) {
    return new KafkaTemplate<>(kafkaEventProducerFactory);
  }
}
